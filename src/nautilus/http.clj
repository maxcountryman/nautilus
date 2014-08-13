(ns nautilus.http)

(defn response
  "Creates a response map."
  [status body & [headers]]
  {:status  status
   :headers headers
   :body    body})
