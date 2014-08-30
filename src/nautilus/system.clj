(ns nautilus.system
  (:require [com.stuartsierra.component    :as component]
            [nautilus.component.database   :as database]
            [nautilus.component.web-server :as web-server]))

(defn new-system
  [{:keys [web-host web-port web-jetty-opts db-host db-port]
    :or   {web-host       "localhost"
           web-port       3000
           web-jetty-opts {:join? false}
           db-host        "localhost"
           db-port        8087}}]

  (component/system-map
    :database   (database/new-component db-host db-port)
    :web-server (component/using
                  (web-server/new-component web-host web-port web-jetty-opts)
                  [:database])))
