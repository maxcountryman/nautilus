(ns nautilus.core
  "Main entrypoint."
  (:require [clojure.tools.cli          :refer [cli]]
            [com.stuartsierra.component :as component]
            [nautilus.system            :as system]))

(def specs
  [["--web-host"
    "Web server host"
    :default "localhost"]
   ["--web-port"
    "Web server port"
    :default 3000
    :parse-fn #(Integer. %)]
   ["--web-jetty-opts"
    "Jetty opts"
    :default {}
    :parse-fn read-string]
  ["--db-host"
    "Riak cluster host"
    :default "localhost"]
   ["--db-port"
    "Riak cluster port"
    :default 8087
    :parse-fn #(Integer. %)]
   ["-h"
    "--help"
    "Print this help"
    :default false
    :flag true]])

(defn -main
  [& args]
  (let [[opts args banner] (apply cli args specs)]
    (when (:help opts)
      (println banner))

    (when-not (:help opts)
      (-> (dissoc opts :help)
          system/new-system
          component/start)

      (while true))))
