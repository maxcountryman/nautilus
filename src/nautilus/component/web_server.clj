(ns nautilus.component.web-server
  "Provides a WebServer component.

  This namespace provides a WebServer component which can be used in the
  context of a larger system. Here a component contains the Ring handler as
  well as the Jetty server.

  Our handler is wrapped with a composition of middlewares which provide
  routing and so forth. See nautilus.middleware.

  To construct a new WebServer, use the constructor fn:

    => (new-component \"localhost\" 3000 {})

  Calling the above function yields a new WebServer component where the Jetty
  server is listening on localhost:3000."
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty         :as ring-jetty]
            [nautilus.middleware        :as middleware])
  (:import [org.eclipse.jetty.server Server]))

(defn new-handler
  "Returns a new Ring handler, given a db (Database component)."
  [client db portals]
  (-> (constantly {:status 404 :body "Not Found"})  ;; default "handler"
      (middleware/wrap-middleware client db portals)))

(defrecord WebServer
  [^Server server client database portal host port jetty-opts]

  component/Lifecycle
  (start [this]
    (if server
      this
      (let [handler (new-handler client database portal)
            server  (ring-jetty/run-jetty handler (merge {:host  host
                                                          :port  port
                                                          :join? false}
                                                         jetty-opts))]
        (assoc this :server server))))

  (stop [this]
    (if server
      (do (.stop server)
          (dissoc this :server))
      this)))

(defn new-component
  "Returns a new WebServer component provided host, port, and jetty-opts."
  [host port jetty-opts]
  (map->WebServer {:host       host
                   :port       port
                   :jetty-opts jetty-opts}))
