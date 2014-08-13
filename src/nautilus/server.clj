(ns nautilus.server
  "Server application logic."
  (:require [clojure.string         :as string]
            [clojure.tools.logging  :as log]
            [ring.adapter.jetty     :as ring-jetty]
            [ring.middleware.json   :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]] 
            [ring.util.codec        :refer [base64-decode]]
            [nautilus.http          :as http]
            [nautilus.oauth         :as oauth]
            [nautilus.portal        :as portal]
            [nautilus.route         :as route]
            [nautilus.users         :as users]))


;; User creation endpoint
(route/POST user "/users" []
  (users/create-request route/*request*))

;; Token retrieval endpoint
(route/POST token "/token" []
  (oauth/token-request route/*request*))

;; Portal retrieval endpoints
(route/POST new-portal "/:service/:endpoint" []
  (portal/new-portal-request route/*request*))

(route/ANY portal "/portal" []
  (portal/portal-request route/*request*))

;; Service health check endpoint
(route/GET health "/health" []
  (http/response 200 {:ok true}))


;; "authorization" "Basic dXNlcjpwYXNz",

;; Middlewares
(defn- get-auth
  [{:keys [headers]}]
  (some-> headers
          (get "authorization")
          (string/split #" ")))

(defn- decode-user-pass
  [encoded]
  (-> encoded
      base64-decode
      String.
      (string/split #":")))

(defn wrap-basic-auth
  [handler]
  (fn [request]
    (let [[kind auth] (get-auth request)]
      (if (= kind "Basic")
        ;; TOOD: Make sure this doesn't blow up!
        (let [[user pass] (decode-user-pass auth)]
          (-> request
              (assoc :authorization {:username user
                                     :password pass})
              handler))
        (handler request)))))

(defn wrap-bearer-token
  [handler]
  (fn [request]
    (let [[kind token] (get-auth request)]
      (if (= kind "Bearer")
        (-> request
            (assoc :bearer-token token)
            handler)
        (handler request)))))

(defn wrap-database
  [handler database]
  (fn [request]
    (-> request
        (assoc :database database)
        handler)))

(defn wrap-logging
  [handler]
  (fn [{:keys [uri]
        :as   request}]
    (log/info uri)
    (handler request)))


;; Server constructor
(defn new-server
  "Creates a new Jetty server instance."
  [database host port jetty-opts]

  ;; TODO: Pluggable serialization?
  (let [app (-> route/new-handler
                wrap-logging
                wrap-params
                wrap-basic-auth
                wrap-bearer-token
                (wrap-database database)
                (wrap-json-body {:keywords? true})
                (wrap-json-response {:pretty true}))]
    (ring-jetty/run-jetty app (merge {:host  host
                                      :port  port
                                      :join? false}
                                     jetty-opts))))
