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
            [ring.middleware.params     :refer [wrap-params]]
            [nautilus.database          :as database]
            [nautilus.middleware.shared :as shared]
            [nautilus.utils             :as utils]))


;; Utils
(defn create-id
  "Returns a random UUID as a string."
  []
  (utils/random-uuid))

(defn create-ttl
  "Returns the Unix time in milliseconds plus a TTL."
  [ttl]
  (+ (utils/now) ttl))

(defn add-portal!
  "Adds a portal to the portals atom."
  [portal id service path ttl login]
  (swap! (:cache portal) merge {id {:service service
                                    :path    path
                                    :ttl     ttl
                                    :login   login}}))

(defn get-portal
  "Gets a portal from the portals atom."
  [portal id]
  (get (-> portal :cache deref) id))

(defn request->portal-uri
  "Given a request and portal, returns the URI of the portal endpoint."
  [{:keys [scheme server-name server-port]} {:keys [path]}]
  (str (name scheme)
       "://"
       server-name
       ":"
       server-port
       path))

(defn request->portal-id
  "Returns the value of the X-Portal-Id header."
  [{:keys [headers]}]
  (get headers "x-portal-id"))

(defn expired?
  "Returns true if the Unix time in milliseconds is greater than or equal to
  ttl, otherwise false."
  [ttl]
  (>= (utils/now) ttl))

(defn expired-portal?
  "Returns true if the given portal-id is expired."
  [portal portal-id]
  (-> (get-portal portal portal-id)
      :ttl
      expired?))

(defn slurp-binary
  "Reads is, an InputStream, into a ByteArray buffer. Returns buffer."
  [^java.io.InputStream is len]
  (with-open [rdr is]
    (let [buf (byte-array len)]
      (.read rdr buf)
      buf)))

(defn proxy-request
  "Proxies a request via clj-http."
  [{:keys [request-method query-string headers body] :as request} to]
  (http/request {:method  request-method
                 :url     (or (when query-string
                                (str to "?" query-string))
                              to)
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
  [{:keys [db] {:keys [service]} :path-params :as request}]
  (when-not (database/service-exists? db service)
    (utils/invalid-request (str "No such service: " service))))

(defn ensure-portal
  "Returns nil if request contains X-Portal-Id header and that ID is a valid
  portal, otherwise an error response."
  [{:keys [portal] :as request}]
  (let [portal-id (request->portal-id request)]
    (or (when-not portal-id
          (utils/invalid-request "Missing: X-Portal-Id"))
        (when-not (get-portal portal portal-id)
          (-> (utils/invalid-request (str "No such portal ID: " portal-id))
              (assoc :status 404))))))

(defn ensure-liveness
  "Returns nil if request contains a live X-Portal-Id header, otherwise an
  error response."
  [{:keys [portal] :as request}]
  (let [portal-id (request->portal-id request)]
    (when (expired-portal? portal portal-id)
      (-> (utils/invalid-request "Expired portal")
          (assoc :status 410)))))


(def maybe-create-errored
  (some-fn ensure-token
           ensure-service))

(def maybe-retrieve-errored
  ;; Wrap resp here since successful request cannot be wrapped (it's proxied)
  (wrap-json-response
    (some-fn ensure-portal
             ensure-liveness)))


;; Request handlers
(defn create-portal
  "Creates a new portal. Also expects a db key. Returns a successful response
  containing the portal URI, TTL, and necessary headers."
  [{:keys [db portal bearer-token] {:strs [ttl]} :query-params :as request}
   service path]
  (let [portal-id  (create-id)
        ttl        (or (when ttl (Long/parseLong ttl))
                       (* 1000 60 20))
        portal-ttl (create-ttl ttl)
        login      (database/get-token db bearer-token)]

    ;; Create the portal
    (add-portal! portal portal-id service path portal-ttl login)

    {:status  201
     :body    {:uri     (request->portal-uri request portal)
               :ttl     portal-ttl
               :headers {:X-Portal-Id portal-id}}}))

(defn create-response
  "Portal creation wrapper, returns either an error or a successful response."
  [{{:keys [service path]} :path-params :as request}]
  (or (utils/maybe-wrong-method request #{:post})
      (maybe-create-errored request)
      (create-portal request service path)))

(defn retrieve-portal
  "Retrieves a portal, proxying the request through the service backend it
  maps to. Returns the proxy result."
  [{:keys [db portal] :as request}]
  (let [portal-id                    (request->portal-id request)
        {:keys [service path login]} (get-portal portal portal-id)
        {:keys [host]}               (database/get-service db service)]

    ;; TODO: There is a race here, although unlikely in practice:
    ;; A service can be deleted between here and ensure-service.
    (-> request
        (proxy-request (str host "/" path))
        (assoc-in [:headers "X-Portal-Login"] login))))

(defn retrieve-response
  "Portal retrieval wrapper, returns either an error or a successful response."
  [request]
  (or (maybe-retrieve-errored request)
      (retrieve-portal request)))


;; Middleware
(defn wrap-portal-routes
  "A middleware which adds two endpoints for portal creation and retrieval."
  [handler db portal]
  (fn [request]
    (let [request      (assoc request :db db :portal portal)
          create-resp* (-> create-response
                           shared/wrap-bearer-token
                           wrap-json-response
                           wrap-params)]
      (condp clout/route-matches request
        "/:service/:path" :>> (fn [{:keys [service path]}]
                                (-> request
                                    (assoc :path-params {:service service
                                                         :path    path})
                                    create-resp*))
        "/portal"             (retrieve-response request)
        (handler request)))))
