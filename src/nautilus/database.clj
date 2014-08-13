(ns nautilus.database
  "Persistence layer."
  (:require [clojure.set            :as set]
            [crypto.password.bcrypt :as bcrypt]
            [liza.store             :as store]
            [liza.store.riak        :as riak]))


;; Persistence logic
(defn- merge-existing
  [new]
  (fn [existing]
    (merge existing new)))

(defn new-user!
  [{:keys [users-bucket]} email password]
  (let [encrypted (bcrypt/encrypt password)]
    (store/modify users-bucket email (merge-existing {:password encrypted}))))

(defn new-token!
  [{:keys [users-bucket tokens-bucket]} email token]

  ;; Establish a bidirectional relationship between the user and tokens buckets
  (store/modify users-bucket email (merge-existing {:token token}))
  (store/modify tokens-bucket token (merge-existing {:email email})))


;; Database connection
(defn new-client
  [opts]
  (riak/connect-client opts))

(defn new-bucket
  [opts]
  (riak/connect-bucket opts))

(defn users-bucket
  [client]
  (new-bucket {:client      client
               :bucket-name "users"
               :merge-fn    set/union}))

(defn tokens-bucket
  [client]
  (new-bucket {:client      client
               :bucket-name "tokens"
               :merge-fn    set/union}))

(defn services-bucket
  [client]
  (new-bucket {:client      client
               :bucket-name "services"
               :merge-fn    set/union}))


;; Convenience fns
(defn user-exists?
  [{:keys [users-bucket]} email]
  (boolean
    (store/get users-bucket email)))

(defn user-authenticates?
  [{:keys [users-bucket]} email offered]
  (boolean
    (when-let [encrypted (:password (store/get users-bucket email))]
      (bcrypt/check offered encrypted))))

(defn valid-token?
  [{:keys [tokens-bucket]} token]
  (boolean
    (store/get tokens-bucket token)))

(defn get-service
  [{:keys [services-bucket]} service]
  (store/get services-bucket service))

(defn service-exists?
  [database service]
  (boolean
    (get-service database service)))
