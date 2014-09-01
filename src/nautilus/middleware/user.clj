(ns nautilus.middleware.user
  "Provides Ring middleware providing a user creation endpoint.

  This namespace provides the relevant logic for creating new users as well as
  a Ring middleware which adds a single endpoint, /user, to the application.

  To use this middleware, wrap a Ring handler:

    => (-> (constantly {:status 404 :body \"Not Found\"})
           (wrap-user-routes db))

  If the Ring application is running on localhost:3000, then a request could
  look like this:

    $ curl http://localhost:3000/user
           -H \"Content-Type: application/json\"
           -d '{\"email\": \"foo@bar.tld\", \"password\": \"hunter2\"}'

  Bear in mind that this middleware depends on a database backend. Currently
  this must be in the format provided by nautilus.component.database. Using a
  system ensures that this component is available where necessary."
  (:require [clout.core           :as clout]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [nautilus.database    :as database]
            [nautilus.utils       :as utils]))


;; Utils
(defn valid-email?
  "Returns true if a provided email conforms to a regex, false otherwise."
  [email]
  (if (nil? email)
    false
    (boolean
      (re-matches #"^\S+@\S+$" email))))  ;; Let's be lenient here

(defn json-request?
  "Returns true if request content-type conforms of a regex, false otherwise."
  [request]
  (if-let [type (:content-type request)]
    (not (empty? (re-find #"^application/(.+\+)?json" type)))))


;; Request validators
(defn ensure-content-type
  "Return if request content-type is a JSON content-type, otherwise an error
  response."
  [request]
  (when-not (json-request? request)
    (utils/invalid-request "Non-JSON Content-Type")))

(defn ensure-args
  "Returns nil if request body contains email and password, otherwise an error
  response. Anticipates wrap-json-body proceeded this."
  [{{:keys [email password]} :body}]
  (or (when-not email
        (utils/invalid-request "Missing: email"))
      (when-not password
        (utils/invalid-request "Missing: password"))))

(defn ensure-email
  "Returns nil if request contains a valid email, otherwise an error response.
  Anticipates wrap-json-body proceeded this."
  [{{:keys [email]} :body}]
  (when-not (valid-email? email)
    (utils/invalid-request "Invalid email")))

(defn ensure-unique
  "Returns nil if request contains a unique email, otherwise an error response.
  Anticipates wrap-json-body proceeded this. Also expects a db key."
  [{:keys [db] {:keys [email]} :body}]
  (when (database/user-exists? db email)
    (utils/invalid-request "User exists")))

(def maybe-errored
  (some-fn ensure-content-type
           ensure-args
           ensure-email
           ensure-unique))


;; Request handlers
(defn create-user
  "Creates a new user for the given email with the given password. Also expects
  a db key. Returns a successful response."
  [{:keys [db] {:keys [email password]} :body}]

  ;; TOOD: return user in response
  (database/new-user! db email password)
  {:status 201 :body {:ok true}})

(defn create-response
  "User creation wrapper, returns either an error or a successful response."
  [request]
  (or (utils/maybe-wrong-method request #{:post})
          (maybe-errored request)
          (create-user request)))


;; Middleware
(defn wrap-user-routes
  "A middleware which adds a user creation endpoint."
  [handler db]
  (fn [request]
    (let [request      (assoc request :db db)
          create-resp* (-> create-response
                           wrap-json-response
                           (wrap-json-body {:keywords? true}))]
      (condp clout/route-matches request
        "/user" (create-resp* request)
        (handler request)))))
