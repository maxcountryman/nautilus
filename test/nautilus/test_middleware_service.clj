(ns nautilus.test-middleware-service
 (:require [clojure.test                :refer [deftest is]]
           [com.stuartsierra.component  :as component]
           [nautilus.database           :as database]
           [nautilus.middleware.service :as service]
           [nautilus.system             :as system]
           [nautilus.test-core          :refer [memory-bucket]]
           [nautilus.utils              :as utils]))

(def system nil)
(alter-var-root #'system
  (constantly (system/new-system {:web-port 3100})))

(deftest test-ensure-client-authorized
  (with-redefs [database/connect-bucket (constantly (memory-bucket))]
    (alter-var-root #'system component/start))

  (let [client (:client system)]
    (is (nil? (service/ensure-client-authorized
                {:authorization {:username "foo"
                                 :password "bar"}
                 :client client})))
    (is (= (service/ensure-client-authorized {:client client})
           (-> (utils/error-response "unauthorized_client" "Invalid credentials")
               (assoc :status 401)
               (assoc :headers {"WWW-Authenticate" "Basic realm=\"nautilus\""})))))

  (alter-var-root #'system component/stop))

(deftest test-ensure-params
  (is (nil? (service/ensure-params {:body {:host "localhost"
                                           :service "foo"}})))
  (is (= (service/ensure-params {:body {:host "localhost"}})
         (utils/invalid-request "Missing: service")))
  (is (= (service/ensure-params {:body {:service "foo"}})
         (utils/invalid-request "Missing: host"))))

(deftest test-ensure-unique
  (with-redefs [database/connect-bucket (constantly (memory-bucket))]
    (alter-var-root #'system component/start))

  (let [db (:database system)]
    (database/new-service! db "foo" {})
    (is (nil? (service/ensure-unique {:db db :body {:service "bar"}})))
    (is (= (service/ensure-unique {:db db :body {:service "foo"}})
           (utils/invalid-request "Service exists"))))

  (alter-var-root #'system component/stop))

(deftest test-create-service
  (with-redefs [database/connect-bucket (constantly (memory-bucket))]
    (alter-var-root #'system component/start))

  (let [db      (:database system)
        service "foo"]
    (is (= (service/create-service {:db db :body {:host "localhost" 
                                                  :service "foo"}})
           {:status 201 :body {:host "localhost"}}))
    (is (true? (database/service-exists? db service))))
  
  (alter-var-root #'system component/stop))
