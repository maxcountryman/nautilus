(ns nautilus.middleware
  (:require [nautilus.middleware.user    :refer [wrap-user-routes]]
            [nautilus.middleware.oauth   :refer [wrap-oauth-routes]]
            [nautilus.middleware.service :refer [wrap-service-routes]]
            [nautilus.middleware.portal  :refer [wrap-portal-routes]]))

(defn wrap-middleware
  "A middleware which itself wraps other middleware.
  
  This is a convenience wrapper which provides an easy way to wrap a handler
  with the middleware which provide the routes Nautilus implements. All routes
  are implemented as middleware components which are composed with a handler
  at this level.
  
  Takes a handler and db (a Database component) and returns a fn which takes a
  request map."
  [handler db]
  (-> handler
      (wrap-user-routes db)
      (wrap-oauth-routes db)
      (wrap-service-routes db)
      (wrap-portal-routes db)))
