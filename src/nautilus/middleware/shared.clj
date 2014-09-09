(ns nautilus.middleware.shared
  "Provides Ring middlewares which are shared between other middlewares."
  (:require [clojure.string  :as string]
            [ring.util.codec :refer [base64-decode]]))

(defn- get-auth
  "Retrieves the value of an authorization header from a given request map."
  [{:keys [headers]}]
  (some-> headers
          (get "authorization")
          (string/split #" ")))

(defn- decode-user-pass
  "Decodes a username and password from a Basic Auth header."
  [encoded]
  (-> encoded
      base64-decode
      String.
      (string/split #":")))

(defn wrap-basic-auth
  "Decodes Basic Auth and attaches the username and password in the
  authorization key to a request."
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
  "Decodes a Bearer Token and attaches the token in the bearer-token key to a
  request."
  [handler]
  (fn [request]
    (let [[kind token] (get-auth request)]
      (if (= kind "Bearer")
        (-> request
            (assoc :bearer-token token)
            handler)
        (handler request)))))
