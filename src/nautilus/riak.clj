(ns nautilus.riak
  "Provides a wrapper around the Java Riak client via the liza bucket
  abstraction.

  This a port of liza-riak which removes mandatory metrics, amoung other
  things."
  (:require [liza.store           :as store]
            [org.fressian.clojure :as fress])
  (:import [com.basho.riak.client RiakFactory]
           [com.basho.riak.client.bucket Bucket]
           [com.basho.riak.client.cap DefaultRetrier
                                      ConflictResolver
                                      Mutation
                                      Retrier]
           [com.basho.riak.client.builders RiakObjectBuilder]
           [com.basho.riak.client.operations FetchObject]
           [com.basho.riak.client.convert Converter]))

(def default-content-type "application/fressian")

(declare create-converter create-mutator)

(deftype RiakBucket [^Bucket bucket
                     bucket-name
                     ^Retrier retrier
                     ^ConflictResolver resolver
                     ^String content-type
                     opts]
  liza.store/Bucket
  (store/get [this k]
    (-> (.fetch bucket k)
        (.r (:r opts))
        (.notFoundOK (:not-found-ok? opts))
        (.withConverter (create-converter this k))
        (.withResolver resolver)
        (.withRetrier retrier)
        .execute))

  (store/put [this k v]
    (-> (.store bucket k v)
        (.withConverter (create-converter this k))
        (.withResolver resolver)
        (.withRetrier retrier)
        (.withValue v)
        .withoutFetch
        (.returnBody true)
        .execute))

  store/ModifiableBucket
  (modify [this k f]
    (-> (.store bucket k "")
        (.r (:r opts))
        (.notFoundOK (:not-found-ok? opts))
        (.withConverter (create-converter this k))
        (.withResolver resolver)
        (.withRetrier retrier)
        (.withMutator (create-mutator f))
        (.returnBody true)
        .execute))

  store/DeleteableBucket
  (delete [_ k]
    (-> (.delete bucket k)
        (.withRetrier retrier)
        .execute))

  store/Wipeable
  (wipe [_]
    (pmap (fn [k]
            (-> bucket
                (.delete k)
                .execute))
          (.keys bucket))))

(defmulti serialize-content
  (fn [content-type data]
    content-type))

(defmulti deserialize-content
  (fn [content-type data]
    content-type))

(defmethod serialize-content default-content-type
  [content-type data]
  (fress/encode data))

(defmethod deserialize-content default-content-type
  [content-type data]
  (fress/decode data))

(defn create-mutator
  [f]
  (reify Mutation
    (apply [_ existing]
      (f existing))))

(defn create-converter
  [bucket k]
  (reify Converter
    (fromDomain [_ o vclock]
      (-> (RiakObjectBuilder/newBuilder (.bucket-name bucket) k)
        (.withVClock vclock)
        (.withValue (serialize-content (.content-type bucket) o))
        (.withContentType (.content-type bucket))
        .build))
    (toDomain [_ raw]
      (when-not (or (nil? raw) (.isDeleted raw))
        (deserialize-content (.getContentType raw) (.getValue raw))))))

(defn create-resolver
  [merge-fn]
  (reify ConflictResolver
    (resolve [_ siblings]
      (when-not (empty? siblings)
        (if (= 1 (count siblings))
          (first siblings)
          (reduce (fn [existing x]
                    (merge-fn existing x))
                  (into #{} siblings)))))))

(defn default-retrier
  [n]
  (DefaultRetrier/attempts n))

(defn connect-client
  [{:keys [host port pool-size timeout]
    :or   {pool-size Integer/MAX_VALUE
           timeout   3000}}]

  (RiakFactory/newClient
    (-> (com.basho.riak.client.raw.pbc.PBClientConfig$Builder.)
        (.withConnectionTimeoutMillis timeout)
        (.withHost host)
        (.withPort port)
        (.withPoolSize pool-size)
        (.build))))

(defn create-bucket
  [client bucket-name backend allow-siblings? last-write-wins?]
  (-> (.createBucket client bucket-name)
      (.lazyLoadBucketProperties)
      (.backend backend)
      (.allowSiblings allow-siblings?)
      (.lastWriteWins last-write-wins?)
      (.execute)))

(defn connect-bucket
  [{:keys [^com.basho.riak.client.IRiakClient client
           ^String bucket-name
           merge-fn
           content-type
           ^boolean allow-siblings?
           ^boolean last-write-wins?
           ^boolean not-found-ok?
           ^String  backend
           ^Integer retrier-attempts
           ^Integer r]
    :or {merge-fn                  clojure.set/union
         content-type              default-content-type
         ^boolean allow-siblings?  true
         ^boolean last-write-wins? false
         ^boolean not-found-ok?    false
         ^String  backend          "bitcask"
         ^Integer retrier-attempts 3
         ^Integer r                2}}]

  {:pre [(instance? java.lang.String bucket-name)
         (instance? com.basho.riak.client.IRiakClient client)
         (instance? clojure.lang.IFn merge-fn)]}

  (let [riak-opts {:allow-siblings?  allow-siblings?
                   :last-write-wins? last-write-wins?
                   :not-found-ok?    not-found-ok?
                   :backend          backend
                   :r                r}
        bucket    (create-bucket client
                                 bucket-name
                                 backend
                                 allow-siblings?
                                 last-write-wins?)
        resolver  (create-resolver merge-fn)
        retrier   (default-retrier retrier-attempts)]
    (RiakBucket. bucket
                 bucket-name
                 retrier
                 resolver
                 content-type
                 riak-opts)))
