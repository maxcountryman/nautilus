(ns nautilus.middleware.health
  "Provides a Ring middleware providing a health check endpoint."
  (:require [clout.core :as clout]))

(defn wrap-health-routes
  "A middleware which adds a health check endpoint."
  [handler]
  (fn [request]
    (condp clout/route-matches request
      "/health" {:status 200 :body "ok"}
      (handler request))))
