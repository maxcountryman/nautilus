(ns nautilus.component.client
  "Provides a component encapsulating client-related state, e.g. client id
  secret pairs which may be obtained from a persistence layer."
  (:require [com.stuartsierra.component :as component]))

(defrecord Client
  [credentials database]

  component/Lifecycle
  (start [this]
    (if credentials
      this
      ;; TODO: This should come from the persistence layer.
      (assoc this :credentials #{["foo" "bar"]})))

  (stop [this]
    (if credentials
      (dissoc this :credentials)
      this)))

(defn new-component
  "Returns a new Client component."
  []
  (map->Client {}))
