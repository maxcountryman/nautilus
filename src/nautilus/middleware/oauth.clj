(ns nautilus.middleware.oauth
  "Provides Ring middleware providing OAuth 2.0 Bearer Tokens.

  This namespace provides the relevant logic for creating new bearer tokens
  as well as a Ring middleware which adds a single endpoint, /token, to the
  application.

  To use this middleware, wrap a Ring handler:

    => (-> (constantly {:status 404 :body \"Not Found\"})
           (wrap-oauth-routes db))

  If the Ring application is running on localhost:3000, then a request could
  look like this:

    $ curl http://localhost:3000/token
           -u foo:bar
           -d \"username=foo@bar.tld&password=hunter2&grant_type=password\"

  Bear in mind that this middleware depends on a database backend. Currently
  this must be in the format provided by nautilus.component.database. Using a
  system ensures that this component is available where necessary."
  (:require [clout.core                 :as clout]
            [ring.middleware.json       :refer [wrap-json-response]]
            [ring.middleware.params     :refer [wrap-params]]
            [nautilus.database          :as database]
            [nautilus.middleware.shared :as shared]
            [nautilus.utils             :as utils]))

;; Registered OAuth 2.0 clients, checked in `ensure-client-authorized`
;;
;; TODO: Move to database? Make a component?
(def client-creds #{["foo" "bar"]})


;; Utils
(defn valid-client-creds?
  "Returns true if client-id and client-secret are valid client credentials,
  otherwise false."
  [client-id client-secret]
  (boolean
    (some client-creds #{[client-id client-secret]})))

(defn get-form-param
  "Retrieves param from form-params. Anticipates wrap-params proceeded this."
  [{:keys [form-params]} param]
  (get form-params param))

(def new-token #(utils/rand-alnum 24))


;; Request validators
;;
;; TODO: Shared with OAuth, move to a common location.
(defn ensure-client-authorized
  "Returns nil if request contains valid client credentials, otherwise an error
  response."
  [{{:keys [username password]} :authorization}]
  (when-not (valid-client-creds? username password)
    (-> (utils/error-response "unauthorized_client" "Invalid credentials")
        (assoc :status 401)
        (assoc :headers {"WWW-Authenticate" "Basic realm=\"nautilus\""}))))

(defn ensure-grant-type
  "Returns nil if request form contains grant_type=password, otherwise an error
  response. Anticipates wrap-params proceeded this."
  [request]
  (if-let [grant-type (get-form-param request "grant_type")]
    (when-not (#{"password"} grant-type)
      (utils/error-response "unsupported_grant_type" "Invalid grant_type"))
    (utils/invalid-request "Missing: grant_type")))

(defn ensure-scope
  "Returns nil if request form does not contain scope, otherwise an error
  response. Anticipates wrap-params proceeded this."
  [request]
  (when-let [scope (get-form-param request "scope")]
    (utils/invalid-request "Invalid scope")))

(defn ensure-password-grant
  "Returns nil if request form contains username and password, otherwise an
  error response. Anticipates wrap-params proceeded this."
  [request]
  (let [username (get-form-param request "username")
        password (get-form-param request "password")]
    (or (when-not username
          (utils/invalid-request "Missing: username"))
        (when-not password
          (utils/invalid-request "Missing: password")))))

(defn ensure-user-authorized
  "Returns nil if request form contains valid username and password, otherwise
  an error response. Anticipates wrap-params proceeded this. Also expects a db
  key."
  [{:keys [db] :as request}]
  (let [username (get-form-param request "username")
        password (get-form-param request "password")]
    (when-not (database/user-authenticates? db username password)
      (utils/access-denied "Invalid credentials"))))

(def maybe-errored
  (some-fn ensure-client-authorized
           ensure-grant-type
           ensure-scope
           ensure-password-grant
           ensure-user-authorized))


(defn create-token
  "Creates an Oauth 2.0 Bearer Token for a given username. Also expects a db
  key. Returns a successful response containing token and user."
  [{:keys [db] :as request}]
  (let [login (get-form-param request "username")
        token (new-token)]

    ;; Store the token in the database
    (database/new-token! db login token)

    {:status 201
     :body {:token_type   "Bearer"
            :access_token token
            :user         {:login login}}}))

(defn create-response
  "Token creation wrapper, returns either an error or a successful response."
  [request]
  (or (utils/maybe-wrong-method request #{:post})
      (maybe-errored request)
      (create-token request)))


(defn wrap-oauth-routes
  "A middleware which adds a token creation endpoint."
  [handler db]
  (fn [request]
    (let [request      (assoc request :db db)
          create-resp* (-> create-response
                           shared/wrap-basic-auth
                           wrap-json-response
                           wrap-params)]
      (condp clout/route-matches request
        "/token" (create-resp* request)
        (handler request)))))
