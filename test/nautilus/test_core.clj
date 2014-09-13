(ns nautilus.test-core
  "Provides a simple in-memory bucket store using atoms. Models the behavior
  of the Riak variation."
  (:require [com.stuartsierra.component :as component]
            [liza.store                 :as store]
            [nautilus.database          :as database]
            [nautilus.system            :as system]))

;; Test system
(def system nil)

(deftype MemoryBucket [bucket]
  store/Bucket
  (store/get [_ k]
    (get @bucket k))

  (store/put [_ k v]
    ;; Riak returns only the values, so here we do the same.
    (-> (swap! bucket #(assoc % k v))
        vals
        first))

  store/DeleteableBucket
  (store/delete [_ k]
    (-> (swap! bucket #(dissoc % k))
        vals
        first))

  store/ModifiableBucket
  (store/modify [this k f]
    (->> (store/get this k)
        f
        (store/put this k))))

(defmacro always
  "Like constantly, but delays evaluation of x."
  [x]
  `(fn [& args#]
     ~x))

(defn memory-bucket [] (MemoryBucket. (atom {})))

(defn fixtures-each
  "Sets up a new system specifically for testing purposes. Starts the system.
  Ensures that we are always talking to an in-memory bucket. Finally stops the
  system."
  [f]
  ;; Set up a new system
  (alter-var-root #'system
    (constantly (system/new-system {:web-port 3100})))

  ;; Start the system with an in-memory bucket
  (with-redefs [database/connect-bucket (always (memory-bucket))]
    (alter-var-root #'system component/start))

  ;; Execute the test fn
  (f)

  ;; Stop the system
  (alter-var-root #'system component/stop))
