(ns nautilus.test-middleware-service
 (:require [clojure.test                :refer [deftest is use-fixtures]]
           [com.stuartsierra.component  :as component]
           [nautilus.database           :as database]
           [nautilus.middleware.service :as service]
           [nautilus.system             :as system]
           [nautilus.test-core          :as test-core]
           [nautilus.utils              :as utils]))

(use-fixtures :each test-core/fixtures-each)

;; TODO: test is shared with oauth middleware tests
(deftest test-ensure-client-authorized
  (let [client (:client test-core/system)]
    (is (nil? (service/ensure-client-authorized
                {:authorization {:username "foo"
                                 :password "bar"}
                 :client client})))
    (is (= (service/ensure-client-authorized {:client client})
           (-> (utils/error-response "unauthorized_client" "Invalid credentials")
               (assoc :status 401)
               (assoc :headers {"WWW-Authenticate" "Basic realm=\"nautilus\""}))))))

(deftest test-ensure-params
  (is (nil? (service/ensure-params {:body {:host "localhost"
                                           :service "foo"}})))
  (is (= (service/ensure-params {:body {:host "localhost"}})
         (utils/invalid-request "Missing: service")))
  (is (= (service/ensure-params {:body {:service "foo"}})
         (utils/invalid-request "Missing: host"))))

(deftest test-ensure-unique
  (let [db (:database test-core/system)]
    (database/new-service! db "foo" {})
    (is (nil? (service/ensure-unique {:db db :body {:service "bar"}})))
    (is (= (service/ensure-unique {:db db :body {:service "foo"}})
           (utils/invalid-request "Service exists")))))

(deftest test-create-service
  (let [db      (:database test-core/system)
        service "foo"]
    (is (= (service/create-service {:db db :body {:host "localhost" 
                                                  :service "foo"}})
           {:status 201 :body {:host "localhost"}}))
    (is (true? (database/service-exists? db service)))))
