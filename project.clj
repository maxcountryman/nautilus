(defproject nautilus "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ^:replace ["-server"]
  :dependencies [[clout "1.2.0"]
                 [clj-http "1.0.0"]
                 [com.stuartsierra/component "0.2.1"]
                 [crypto-password "0.1.3"]
                 [liza-riak "0.6.0-SNAPSHOT"]
                 [ring/ring-jetty-adapter "1.3.0"]
                 [ring/ring-json "0.3.1"]
                 [ring-mock "0.1.5"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}})
