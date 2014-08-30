(ns nautilus.middleware.portal
  "Provides Ring middleware providing portal creation and retrieval endpoints.

  Portals are pre-authenticated, expiring proxies to a privileged resource.

  This namespace provides the relevant logic for creating and retrieving
  portals as well as a Ring middleware which adds two endpoints, /portal and
  /:service/:path.

    => (-> (constantly? {:status 404 :body \"Not Found\"})
           (wrap-portal-routes db))

  If the Ring application is running on localhost:3000, then a request could
  look like this:

    $ curl -X POST http://localhost:3000/foo/bar
           -H \"Authorization: Bearer sw388m63sD2jr3fRSMWq6tCf\"

  This will return a portal response. The response contains a URI and a header.
  To use the portal, the URI should be requested and provided the X-Portal-Id
  header:

    $ curl http://localhost:3000/portal
           -H \"X-Portal-Id: d37e3ae4-817a-4386-92ab-fa97d7857ef3\"

  If the portal expires and has not been garbage-collected, the server will
  return HTTP 410 Gone.

  Bear in mind that this middleware depends on a database backend. Currently
  this must be in the format provided by nautilus.component.database. Using a
  system ensures that this component is available where necessary."
  (:require [clj-http.client            :as http]
            [clout.core                 :as clout]
            [ring.middleware.json       :refer [wrap-json-response]]
            [nautilus.database          :as database]
            [nautilus.middleware.shared :as shared]
            [nautilus.utils             :as utils]))


;; TODO: runtime configuration of path
(def ^{:const true
       :private true}
  path
  "/portal")

;; TODO: runtime configuration of TTL
(def ^{:const true
       :private true}
  ttl
  (* 1000 60 20))

;; TODO: Make component?
(def ^{:private true
       :doc "An atom which holds known portals."}
  portals
  (atom nil))


;; Utils
(defn create-id
  "Returns a random UUID as a string."
  []
  (utils/random-uuid))

;; TODO: This should not rely on globals.
(defn create-ttl
  "Returns the Unix time in milliseconds plus a TTL."
  []
  (+ (utils/now) ttl))

(defn add-portal!
  "Adds a portal to the active portals atom, returns the atom."
  [id remote-host remote-path ttl]
  (swap! portals merge {id {:remote-host remote-host
                            :remote-path remote-path
                            :ttl         ttl}}))

(defn request->nautilus-uri
  "Given a request, returns the URI of the portal endpoint."
  [{:keys [scheme server-name server-port]}]
  (str (name scheme)
       "://"
       server-name
       ":"
       server-port
       path))

(defn expired?
  "Returns true if the Unix time in milliseconds is greater than or equal to
  ttl, otherwise false."
  [ttl]
  (>= (utils/now) ttl))

(defn portal-id
  "Returns the value of the X-Portal-Id header."
  [{:keys [headers]}]
  (get headers "x-portal-id"))

(defn slurp-binary
  "Reads is, an InputStream, into a ByteArray buffer. Returns buffer."
  [^java.io.InputStream is len]
  (with-open [rdr is]
    (let [buf (byte-array len)]
      (.read rdr buf)
      buf)))

(defn remote-uri
  "Returns remote-path appended to remote-host."
  [{:keys [remote-host remote-path]}]
  (str remote-host "/" remote-path))

(defn proxy-request
  "Proxies a request via clj-http."
  [{:keys [request-method query-string headers body] :as request} to]
  (http/request {:method  request-method
                 :url     (str to "?" query-string)
                 :headers (dissoc headers "host" "content-length")
                 :body    (when-let [len (get headers "content-length")]
                            (slurp-binary body (Integer/parseInt len)))
                 :follow-redirects true
                 :throw-exceptions false
                 :as :stream}))


;; Request validators
(defn ensure-token
  "Returns nil if request contains a bearer token and it is valid, otherwise
  an error response. Anticipates wrap-bearer-token proceeded this. Also expects
  a db key."
  [{:keys [db bearer-token]}]
  (or (when-not bearer-token
        (utils/invalid-request "Missing: Bearer token"))
      (when-not (database/token-exists? db bearer-token)
        (-> (utils/access-denied "Invalid Bearer token")
            (assoc :status 401)))))

(defn ensure-service
  "Returns nil if request path-params contains an existing service, otherwise
  an error response. Anticipates wrap-query-string proceeded this. Also
  expects a db key."
  [{:keys [db] {:keys [service]} :query-string :as request}]
  (when-not (database/service-exists? db service)
    (utils/invalid-request (str "No such service: " service))))

(defn ensure-portal
  "Returns nil if request contains X-Portal-Id header and that ID is a valid
  portal, otherwise an error response."
  [request]
  (let [id (portal-id request)]
    (or (when-not id
          (utils/invalid-request "Missing: X-Portal-Id"))
        (when-not (get @portals id)
          (-> (utils/invalid-request (str "No such portal ID: " id))
              (assoc :status 404))))))

(defn ensure-liveness
  "Returns nil if request contains a live X-Portal-Id header, otherwise an
  error response."
  [request]
  (let [id (portal-id request)]
    (when (expired? (:ttl (get @portals id)))
      (-> (utils/invalid-request "Expired portal")
          (assoc :status 410)))))


;; TODO: Maybe `ensure-service-path`?
(def maybe-create-errored
  (some-fn ensure-token
           ensure-service))

(def maybe-retrieve-errored
  ;; wrap resp here since successul request cannot be wrapped (it's proxied)
  (wrap-json-response
    (some-fn ensure-portal
             ensure-liveness)))


;; Request handlers
(defn create-portal
  "Creates a new portal. Also expects a db key. Returns a successful response
  containing the portal URI, TTL, and necessary headers."
  [{:keys [db] :as request} service path]
  (let [id   (create-id)
        ttl  (create-ttl)
        host (:host (database/get-service db service))]

    ;; Create the portal
    (add-portal! id host path ttl)

    {:status  201
     :body    {:uri     (request->nautilus-uri request)
               :ttl     ttl
               :headers {:X-Portal-Id id}}}))

(defn create-response
  "Portal creation wrapper, returns either an error or a successful response."
  [{{:keys [service path]} :query-string :as request}]
  (or (utils/maybe-wrong-method request #{:post})
      (maybe-create-errored request)
      (create-portal request service path)))

(defn retrieve-portal
  "Retrieves a portal, proxying the request through the service backend it
  maps to. Returns the proxy result."
  [request]
  (->> request
       portal-id
       (get @portals)
       remote-uri
       (proxy-request request)))

(defn retrieve-response
  "Portal retrieval wrapper, returns either an error or a successful response."
  [request]
  (or (maybe-retrieve-errored request)
      (retrieve-portal request)))


;; Middleware
(defn wrap-portal-routes
  "A middleware which adds two endpoints for portal creation and retrieval."
  [handler db]
  (fn [request]
    (let [request      (assoc request :db db)
          create-resp* (-> create-response
                           shared/wrap-bearer-token
                           wrap-json-response)]
      (condp clout/route-matches request
        "/:service/:path" :>> (fn [{:keys [service path]}]
                                (-> request
                                    (assoc :query-string {:service service
                                                          :path    path})
                                    create-resp*))
        "/portal"             (retrieve-response request)
        (handler request)))))
