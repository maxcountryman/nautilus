(defproject nautilus "0.1.0-SNAPSHOT"
  :description "User authentication service"
  :url "https://github.com/maxcountryman/nautilus"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ^:replace ["-server"]
  :dependencies [[clout "1.2.0"]
                 [clj-http "1.0.0"]
                 [com.basho.riak/riak-client "1.4.2"]
                 [com.stuartsierra/component "0.2.1"]
                 [crypto-password "0.1.3"]
                 [fressian-clojure "0.2.0"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [liza "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.logging "0.3.0"]]
  :plugins [[com.palletops/uberimage "0.1.5"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}
             :uberjar {:aot :all}}
  :main nautilus.core)
