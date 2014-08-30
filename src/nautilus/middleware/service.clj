(ns nautilus.middleware.service
  "Provides Ring middleware providing a service creation endpoint.

  This namespace provides the relevant logic for adding new authenticated
  service backends as well as a Ring middleware which adds a single endpoint,
  /service, to the application.

  To use this middleware, wrap a Ring handler:

    => (-> (constantly? {:status 404 :body \"Not Found\"})
           (wrap-service-routes db))

  If the Ring application is running on localhost:3000, then a request could
  look like this:

    $ curl -X POST http://localhost:3000/service
           -H \"Content-Type: application/json\"
           -u foo:bar
           -d '{\"service\": \"foo\", \"host\": \"http://example.com/\"}'

  Bear in mind that this middleware depends on a database backend. Currently
  this must be in the format provided by nautilus.component.database. Using a
  system ensures that this component is available where necessary."
  (:require [clout.core                 :as clout]
            [ring.middleware.json       :refer [wrap-json-body
                                                wrap-json-response]]
            [nautilus.database          :as database]
            [nautilus.utils             :as utils]
            [nautilus.middleware.oauth  :as oauth]
            [nautilus.middleware.shared :as shared]))


;; Request validators
;;
;; TODO: Shared with OAuth, move to a common location.
(defn ensure-client-authorized
  "Returns nil if request contains valid client credentials, otherwise an error
  response."
  [{{:keys [username password]} :authorization}]
  (when-not (oauth/valid-client-creds? username password)
    (-> (utils/error-response "unauthorized_client" "Invalid credentials")
        (assoc :status 401)
        (assoc :headers {"WWW-Authenticate" "Basic realm=\"nautilus\""}))))

(defn ensure-params
  "Returns nil if the request body contains host and service, otherwise an
  error response. Anticipates wrap-json-body proceeded this."
  [{{:keys [host service]} :body}]
  (or (when-not host
        (utils/invalid-request "Missing: host"))
      (when-not service
        (utils/invalid-request "Missing: service"))))

(defn ensure-unique
  "Returns nil if the request body contains a service which does not exist in
  the database, otherwise an error response. Anticipates wrap-json-body
  proceeded this. Also expects a db key."
  [{:keys [db] {:keys [service]} :body}]
  (when (database/service-exists? db service)
    (utils/invalid-request "Service exists")))

(def maybe-errored
  (some-fn ensure-client-authorized
           ensure-params
           ensure-unique))


;; Request handlers
(defn create-service
  "Creates a service backend. Also expects a db key. Returns a successful
  response containing the service."
  [{:keys [db] {:keys [host service]} :body}]
  {:status 201
   :body (database/new-service! db service {:host host})})

(defn create-response
  "Service creation wrapper, returns either an error or a successful response."
  [request]
  (or (utils/maybe-wrong-method request #{:post})
      (maybe-errored request)
      (create-service request)))


;; Middleware
(defn wrap-service-routes
  "A middleware which adds a service creation endpoint."
  [handler db]
  (fn [request]
    (let [request      (assoc request :db db)
          create-resp* (-> create-response
                           shared/wrap-basic-auth
                           wrap-json-response
                           (wrap-json-body {:keywords? true}))]
      (condp clout/route-matches request
        "/service" (create-resp* request)
        (handler request)))))
