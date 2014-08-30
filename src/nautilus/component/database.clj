(ns nautilus.component.database
  "Provides a Database component.

  This namespace provides a Database component which can be used in the context
  of a larger system. Here a component contains the client object as well as
  relevant buckets for a Riak backend.

  To construct a new Database component, use the constructor fn:

    => (new-component \"localhost\" 8000)

  Calling the above function yields a new Database component where the Riak
  client will connect on localhost:8000."
  (:require [com.stuartsierra.component :as component]
            [nautilus.database          :as db])
  (:import [com.basho.riak.client IRiakClient]))

(defrecord Database
  [^IRiakClient client host port]

  component/Lifecycle
  (start [this]
    (if client
      this
      (let [client         (db/connect-client {:host host :port port})
            user-bucket    (db/connect-user-bucket client)
            token-bucket   (db/connect-token-bucket client)
            service-bucket (db/connect-service-bucket client)]
        (-> this
            (assoc :client client)
            (assoc :user-bucket user-bucket)
            (assoc :token-bucket token-bucket)
            (assoc :service-bucket service-bucket)))))

  (stop [this]
    (if-not client
      this
      (.shutdown client))))

(defn new-component
  "Returns a new Database component provided host and port."
  [host port]
  (map->Database {:host host
                  :port port}))
