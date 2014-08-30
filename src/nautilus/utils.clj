(ns nautilus.utils
  (:import [java.util UUID]))

(def c "0123456789bcdfghjklmnpqrstvwxyzBCDFGHJKLMNPQRSTVWXYZ")

(def rand-char #(rand-nth c))

(defn rand-alnum
  "Returns a random alphanumeric string of length n."
  [n]
  (apply str (take n (repeatedly rand-char))))

(defn now
  "Return the current time in milliseconds."
  []
  (System/currentTimeMillis))

(defn random-uuid
  []
  (str (UUID/randomUUID)))


;; Responses
(defn error-response
  "Returns a formatted Ring response map with an error body. Takes an error, an
  error description, and optionally an HTTP status code."
  ([error error-desc]
   (error-response error error-desc 400))
  ([error error-desc status]
   {:body {:error error
           :error_decription error-desc}
    :status status}))

(def invalid-request (partial error-response "invalid_request"))
(def access-denied (partial error-response "access_denied"))

(defn error-response?
  "Returns true if the given Ring response map contains a key :error in its
  body."
  [response]
  (not (nil? (get-in response [:body :error]))))

(defn response
  [status body]
  {:status status :body body})


(defn maybe-wrong-method
  [{:keys [request-method]} allowed-methods]
  (when-not (some? (allowed-methods request-method))
    {:status 405 :body "Method Not Allowed"}))
