(ns nautilus.middleware
  "Provides a composition of middlewares."
  (:require [clojure.tools.logging       :as log]
            [nautilus.middleware.health  :refer [wrap-health-routes]]
            [nautilus.middleware.user    :refer [wrap-user-routes]]
            [nautilus.middleware.oauth   :refer [wrap-oauth-routes]]
            [nautilus.middleware.service :refer [wrap-service-routes]]
            [nautilus.middleware.portal  :refer [wrap-portal-routes]]
            [nautilus.utils              :as utils]))

(defn wrap-cors
  "A middleware which attaches CORS-related headers to responses and responds
  appropriately to OPTIONS."
  [handler]
  (fn [request]
    (let [resp (if (= :options (:request-method request))
                 {:status 200 :body nil}
                 (handler request))]
    (-> resp
        (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
        (assoc-in [:headers "Access-Control-Allow-Headers"] "X-Portal-Id")))))

(defn wrap-request-id
  "A middleware which attaches a request identifier to responses."
  [handler]
  (fn [request]
    (-> request
        handler
        (assoc-in [:headers "X-Request-Id"] (utils/random-uuid)))))

(defn wrap-request-logging
  "A middleware for logging requests."
  [handler]
  (fn [{:keys [remote-addr request-method uri] :as request}]
    (let [start  (System/nanoTime)
          resp   (handler request)
          finish (System/nanoTime)
          total  (quot (- finish start) 1000)  ;; microseconds
          rid    (get-in resp [:headers "X-Request-Id"])
          status (:status resp)]
      (log/infof "Completed %s %s %s for %s with HTTP %s in %dμs"
                 rid request-method uri remote-addr status total)
      resp)))

(defn wrap-middleware
  "A middleware which itself wraps other middleware.
  
  This is a convenience wrapper which provides an easy way to wrap a handler
  with the middleware which provide the routes Nautilus implements. All routes
  are implemented as middleware components which are composed with a handler
  at this level.
  
  Takes a handler and db (a Database component) and returns a fn which takes a
  request map."
  [client handler db portal]
  (-> handler
      (wrap-user-routes db)
      (wrap-oauth-routes client db)
      (wrap-service-routes client db)
      (wrap-portal-routes db portal)
      wrap-health-routes
      wrap-cors
      wrap-request-id
      wrap-request-logging))
