(ns nautilus.users
  "Users request handlers."
  (:require [nautilus.database :as database]
            [nautilus.utils    :as utils]))


;; Utils
(defn valid-email?
  [email]
  (if (nil? email)
    false
    (boolean
      (re-matches #"^\S+@\S+$" email))))  ;; Let's be lenient here


;; Request validators
(defn ensure-args
  [{{:keys [email password]} :body}]
  (or (when-not email
        (utils/invalid-request "Missing: email"))
      (when-not password
        (utils/invalid-request "Missing: password"))))

(defn ensure-email
  [{{:keys [email]} :body}]
  (when-not (valid-email? email)
    (utils/invalid-request "Invalid email")))

(defn ensure-unique
  [{:keys [database] {:keys [email]} :body}]
  (when (database/user-exists? database email)
    (utils/invalid-request "User exists")))

(def maybe-errored
  (some-fn ensure-args
           ensure-email
           ensure-unique))


;; Request handlers
(defn create-response
  [{:keys [database] {:keys [email password]} :body}]
  (database/new-user! database email password)
  {:status 201 :body {:ok true}})

(defn create-request
  [request]
  (or (maybe-errored request)
      (create-response request)))
