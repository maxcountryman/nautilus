(ns nautilus.core
  "User authentication and management service."
  (:require [com.stuartsierra.component :as component]
            [nautilus.database          :as database]
            [nautilus.server            :as server])
  (:import [com.basho.riak.client IRiakClient]
           [org.eclipse.jetty.server Server])
  (:gen-class))


;; Database Component
(defrecord Database
  [^IRiakClient client host port]

  component/Lifecycle
  (start [this]
    (if client  ;; already started
      this
      (let [client          (database/new-client {:host host :port port})
            users-bucket    (database/users-bucket client)
            tokens-bucket   (database/tokens-bucket client)
            services-bucket (database/services-bucket client)]
        (-> this
            (assoc :client client)
            (assoc :users-bucket users-bucket)
            (assoc :tokens-bucket tokens-bucket)
            (assoc :services-bucket services-bucket)))))

  (stop [this]
    (if-not client
      this
      (.shutdown client))))

(defn new-database
  [host port]
  (map->Database {:host host
                  :port port}))


;; WebServer Component
(defrecord WebServer
  [^Server server ^Database database host port jetty-opts]

  component/Lifecycle
  (start [this]
    (if server  ;; already started
      this
      (let [server (server/new-server database host port jetty-opts)]
        (assoc this :server server))))

  (stop [this]
    (if-not server
      this
      (.stop server))))

(defn new-web-server
  [host port jetty-opts]
  (map->WebServer {:host       host
                   :port       port
                   :jetty-opts jetty-opts}))


;; Application System
(defn new-system
  [{:keys [web-host web-port web-jetty-opts db-host db-port]
    :or   {web-host       "localhost"
           web-port       3000
           web-jetty-opts {:join? false}
           db-host        "localhost"
           db-port        8087}}]

  (component/system-map
    :database   (new-database db-host db-port)
    :web-server (component/using
                  (new-web-server web-host web-port web-jetty-opts)
                  [:database])))

(defn start-system
  [& [opts]]
  (component/start (new-system opts)))


;; Main entrypoint
(defn -main
  [& _]
  (start-system)
  (while true))
