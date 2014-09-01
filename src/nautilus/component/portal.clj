(ns nautilus.component.portal
  "Provides a Portal component."
  (:require [com.stuartsierra.component :as component]))

(defrecord Portal
  [cache path]

  component/Lifecycle
  (start [this]
    (if cache
      this
      (-> this
          (assoc :cache (atom nil))
          (assoc :path path))))

  (stop [this]
    (if-not cache
      this
      (reset! cache nil))))

(defn new-component
  "Returns a new Portal component."
  ([]
   (new-component "/portal"))
  ([path]
   (map->Portal {:path path})))
