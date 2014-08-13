(ns nautilus.route
  "Routing logic."
  (:require [clout.core    :as clout]
            [nautilus.http :as http]))


(def ^{:dynamic true
       :doc "Thread-local request map."}
  *request*)

(defonce ^{:private true
           :doc "Application routes."}
  routes (atom nil))


;; Route helpers
(defn add-route
  "Adds a route to a global, private atom."
  [route-name path allowed-methods handler]
  (let [compiled-route (clout/route-compile path)]
    (swap! routes assoc route-name {:methods allowed-methods
                                    :route   compiled-route
                                    :handler handler})))

(defn allowed-method?
  "Determines if a request map contains an allowed method."
  [{:keys [request-method]} allowed-methods]
  (let [allowed (set allowed-methods)]
    (or (some? (:any allowed))
        (some? (request-method allowed)))))

(defn request->bindings
  "Returns a vector of k bound to k's value in a request."
  [request k]
  [k `(get-in ~request [:request-params ~(keyword k)])])

(defmacro with-params
  "Creates locals from bindings where they exist in request as keywords."
  [bindings request & body]
  `(let [~@(mapcat (partial request->bindings request) `~bindings)]
     ~@body))


;; Route constructor
(defmacro defroute
  "Defines a route."
  [route-name path opts bindings & body]
  `(let [allowed-methods# (-> ~opts :methods set)
         ~route-name      (fn [request#]
                            (binding [*request* request#]
                              (if (allowed-method? request# allowed-methods#)

                                ;; TODO: Probably should support destructuring
                                ;; like Compojure
                                (with-params ~bindings request#
                                  ~@body)
                                (http/response 405 "Method Not Allowed"))))]

     (add-route ~(keyword route-name) ~path allowed-methods# ~route-name)))


;; Route abstractions
(defmacro GET
  [route-name path bindings & body]
  `(defroute ~route-name ~path {:methods [:get]} ~bindings ~@body))

(defmacro POST
  [route-name path bindings & body]
  `(defroute ~route-name ~path {:methods [:post]} ~bindings ~@body))

(defmacro ANY
  [route-name path bindings & body]
  `(defroute ~route-name ~path {:methods [:any]} ~bindings ~@body))

;; Handler
(defn- params-and-handler
  "Returns both the result of a matching route and the related handler."
  [request]
  (let [routes      (vals @routes)
        keep-filter (juxt (comp first keep) (comp :handler first filter))]
    (keep-filter #(clout/route-matches (:route %) request) routes)))

(defn new-handler
  "Creates a new handler."
  [request]
  (let [[params handler] (params-and-handler request)]
    (if params
      (-> request
          (assoc :request-params params)
          handler)
      (http/response 404 "Not Found"))))
