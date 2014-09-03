(ns nautilus.system
  "Provides a system.

  This namespace composes the application's components into a unified system.
  It can be used via the new-system constructor:

    => (def sys (new-system {}))

  Optionally a map of configuration values may be passed in to overload
  defaults.

  Once a system is setup it should be started:

    => (component/start sys)

  It may also be stopped in a samiliar fashion:

    => (component/stop sys)"
  (:require [com.stuartsierra.component    :as component]
            [nautilus.component.client     :as client]
            [nautilus.component.database   :as database]
            [nautilus.component.portal     :as portal]
            [nautilus.component.web-server :as web-server]))

(defn new-system
  "Returns a new system given a map of configuration values.

  Available configuration keys:

    :web-host       - A string, defaults to localhost.
    :web-port       - A long, defaults to 3000.
    :web-jetty-opts - A map of Jetty configuration values; see ring-jetty.
    :db-host        - A string, defaults to localhost.
    :db-port        - A long, defaults to 8087."
  ([]
   (new-system {}))
  ([{:keys [web-host web-port web-jetty-opts db-host db-port]
     :or   {web-host       "localhost"
            web-port       3000
            web-jetty-opts {:join? false}
            db-host        "localhost"
            db-port        8087}}]

   (component/system-map
     :database   (database/new-component db-host db-port)
     :portal     (portal/new-component)
     :client     (component/using
                   (client/new-component)
                   [:database])
     :web-server (component/using
                   (web-server/new-component web-host web-port web-jetty-opts)
                   [:client :database :portal]))))
