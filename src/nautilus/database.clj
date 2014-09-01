(ns nautilus.database
  "Provides an interface to Riak."
  (:require [crypto.password.bcrypt :as bcrypt]
            [liza.store             :as store]
            [nautilus.riak          :as riak]))


;; Persistence logic
(defn merge-existing
  "Given a new value, returns a closure which takes an existing value. This
  closure merges the existing with the new."
  [new]
  (fn [existing]
    (merge existing new)))

(defn new-user!
  "Creates a new user in the user-bucket keyed by login containing a map which
  contains the encrypted password."
  [{:keys [user-bucket]} login password]
  (let [encrypted (bcrypt/encrypt password)]
    (store/modify user-bucket login (merge-existing {:password encrypted}))))

(defn new-token!
  "Creates a new token in the token-bucket keyed by token containing login.
  Updates the user associated with login to contain the token in its value map.
  Also deletes any previous tokens associated with a user."
  [{:keys [token-bucket user-bucket]} login token]

  ;; Remove previous token if any
  (when-let [t (:token (store/get user-bucket login))]
    (store/delete token-bucket t))

  ;; Create bidirectional relation between token and user
  (store/modify user-bucket login (merge-existing {:token token}))
  (store/modify token-bucket token (constantly login)))

(defn new-service!
  "Creates a new service mapped to a host."
  [{:keys [service-bucket]} service host]
  (store/modify service-bucket service (merge-existing host)))


;; Database connection
(defn connect-client
  "Connects a Riak client with an opts map and returns the client."
  [opts]
  (riak/connect-client opts))

(defn connect-bucket
  "Connects a Riak bucket with an opts map and returns the bucket."
  [opts]
  (riak/connect-bucket opts))

(defn connect-user-bucket
  "Connects the user bucket and returns the bucket."
  [client]
  (connect-bucket {:client      client
                   :bucket-name "user"}))

(defn connect-token-bucket
  "Connects the token bucket and returns the bucket."
  [client]
  (connect-bucket {:client      client
                   :bucket-name "token"}))

(defn connect-service-bucket
  "Connects the service bucket and returns the bucket."
  [client]
  (connect-bucket {:client      client
                   :bucket-name "service"}))


;; Convenience fns
(defn user-exists?
  "Returns true if login is in the user-bucket, otherwise false."
  [{:keys [user-bucket]} login]
  (boolean
    (store/get user-bucket login)))

(defn user-authenticates?
  "Returns true if login's encrypted password matches offered, otherwise
  false."
  [{:keys [user-bucket]} login offered]
  (boolean
    (when-let [encrypted (:password (store/get user-bucket login))]
      (bcrypt/check offered encrypted))))

(defn get-token
  "Retrieves token from the token-bucket."
  [{:keys [token-bucket]} token]
  (store/get token-bucket token))

(defn token-exists?
  "Returns true if token is in the token-bucket, otherwise false."
  [database token]
  (boolean
    (get-token database token)))

(defn get-service
  "Retrieves service from the service-bucket."
  [{:keys [service-bucket]} service]
  (store/get service-bucket service))

(defn service-exists?
  "Returns true if service is in the service-bucket, otherwise false."
  [database service]
  (boolean
    (get-service database service)))
