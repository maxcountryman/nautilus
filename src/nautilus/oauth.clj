(ns nautilus.oauth
  "OAuth 2 server."
  (:require [nautilus.database :as db]
            [nautilus.users    :as users]
            [nautilus.utils    :as utils]))


;; Registered OAuth 2.0 clients, checked in `ensure-client-authorized`
;;
;; TODO: Move to database?
(def client-creds #{["foo" "bar"]})


;; Utils
(defn valid-client-creds?
  [client-id client-secret]
  (boolean
    (some client-creds #{[client-id client-secret]})))

(defn get-form-param
  [{:keys [form-params]} param]
  (get form-params param))

(def create-token #(utils/rand-alnum 24))


;; Request validators
(defn ensure-client-authorized
  [{:keys [database] {:keys [username password]} :authorization}]
  (when-not (valid-client-creds? username password)
    (-> (utils/error-response "unauthorized_client" "Invalid credentials")
        (assoc :status 401)
        (assoc :headers {"WWW-Authenticate" "Basic realm=\"nautilus\""}))))

(defn ensure-grant-type
  [request]
  (if-let [grant-type (get-form-param request "grant_type")]
    (when-not (#{"password"} grant-type)
      (utils/error-response "unsupported_grant_type" "Invalid grant_type"))
    (utils/invalid-request "Missing: grant_type")))

(defn ensure-scope
  [request]
  (when-let [scope (get-form-param request "scope")]
    (utils/invalid-request "Invalid scope")))

(defn ensure-password-grant
  [{:keys [database]
    :as   request}]
  (let [username (get-form-param request "username")
        password (get-form-param request "password")]
    (or (when-not username
          (utils/invalid-request "Missing: username"))
        (when-not password
          (utils/invalid-request "Missing: password")))))

(defn ensure-user-authorized
  [{:keys [database]
    :as   request}]
  (let [username (get-form-param request "username")
        password (get-form-param request "password")]
    (when-not (db/user-authenticates? database username password)
      (utils/access-denied "Invalid credentials"))))

(def maybe-errored
  (some-fn ensure-client-authorized
           ensure-grant-type
           ensure-scope
           ensure-password-grant
           ensure-user-authorized))


;; Request handlers
(defn token-response
  [{:keys [database]
    :as   request}]
  (let [username (get-form-param request "username")
        token    (create-token)]
    (db/new-token! database username token)
    {:status 201
     :body {:token_type   "Bearer"
            :access_token token
            :user         {:username username}}}))

(defn token-request
  [request]
  (or (maybe-errored request)
      (token-response request)))
