(ns nautilus.middleware.shared
  (:require [clojure.string  :as string]
            [ring.util.codec :refer [base64-decode]]))

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
