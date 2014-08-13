(ns nautilus.portal
  (:require [clj-http.client   :as http]
            [nautilus.database :as db]
            [nautilus.utils    :as utils])
  (:import [java.util UUID]))


(def ^{:const true} path "/portal")

;; TODO: runtime configuration of TTL
(def ^{:const true :private true} ttl (* 1000 60 20))

;; Active portals
(defonce ^{:private true}
  portals
  (atom nil))


;; Utils
(defn create-id [] (str (UUID/randomUUID)))

(defn create-ttl [] (+ (utils/now) ttl))

(defn create-portal-id
  []
  (let [id     (create-id)
        ttl    (create-ttl)
        id-map {id {:ttl ttl}}]
    id))


;; Service format:
;; 
;; {:host      "localhost"
;;  :port      8000
;;  :endpoints #{"/bar" "/baz" "/qux"}}

(defn add-portal!
  [id host port endpoint ttl]
  (swap! portals merge {id {:endpoint endpoint
                            :host     host
                            :port     port
                            :ttl      ttl}}))

(defn request->uri
  [{:keys [scheme server-name server-port]}]
  (str (name scheme)
       "://"
       server-name
       ":"
       server-port
       path))


;; Request validators
(defn ensure-token
  [{:keys [database bearer-token]}]
  (or (when-not bearer-token
        (utils/invalid-request "Missing: Bearer token"))
      (when-not (db/valid-token? database bearer-token)
        (-> (utils/access-denied "Invalid Bearer token")
            (assoc :status 401)))))

(defn ensure-service
  [{:keys [database] {:keys [service]} :request-params :as request}]
  (when-not (db/service-exists? database service)
    (utils/invalid-request (str "No such service: " service))))

(def new-portal-maybe-errored
  (some-fn ensure-token
           ensure-service))


;; Request handlers
(defn new-portal-response
  [{:keys [database] {:keys [service endpoint]} :request-params :as request}]
  (prn database request)
  (let [id  (create-id)
        ttl (create-ttl)
        {:keys [host port]} (db/get-service database service)]

    ;; Create the portal
    (add-portal! id host port endpoint ttl)

    {:status  201
     :body    {:uri     (request->uri request)
               :ttl     ttl
               :headers {:X-Portal-Id id}}}))

(defn new-portal-request
  [request]
  (or (new-portal-maybe-errored request)
      (new-portal-response request)))


;; Utils
(defn expired?
  [ttl]
  (let [now (utils/now)]
    (>= now ttl)))

(defn request->portal-id
  [{:keys [headers]}]
  (get headers "x-portal-id"))


;; Request validators
(defn ensure-portal
  [request]
  (let [portal-id (request->portal-id request)]
    (or (when-not portal-id
          (utils/invalid-request "Missing: X-Portal-Id"))
        (when-not (get @portals portal-id)
          (utils/invalid-request (str "No such portal ID: " portal-id))))))

(defn ensure-liveness
  [request]
  (let [portal-id (request->portal-id request)]
    (when (expired? (:ttl (get @portals portal-id)))
      (utils/invalid-request "Expired portal"))))

(def portal-maybe-errored
  (some-fn ensure-portal
           ensure-liveness))


(defn service-request
  [host port endpoint method]
  ;; TODO: Scheme should come from service registration
  (let [url  (str "http://" host ":" port "/" endpoint)
        resp (http/request {:method           method
                            :url              url
                            :throw-exceptions false})]

    ;; TODO: actually handle errors
    (when (= (:status resp) 200)
      (:body resp))))

;; Request handlers
(defn portal-response
  [{:keys [request-method]
    :as   request}]
  (let [portal-id (request->portal-id request)
        {:keys [host port endpoint]} (get @portals portal-id)]
    {:status 200
     :body   (service-request host port endpoint request-method)}))

(defn portal-request
  [request]
  (or (portal-maybe-errored request)
      (portal-response request)))
