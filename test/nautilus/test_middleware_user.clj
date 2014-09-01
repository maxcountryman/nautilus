(ns nautilus.test-middleware-user
  (:require [clojure.test               :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [liza.store                 :refer [in-memory-store]]
            [nautilus.database          :as database]
            [nautilus.middleware.user   :as user]
            [nautilus.system            :as system]
            [nautilus.utils             :as utils]))

(def system nil)
(alter-var-root #'system
  (constantly (system/new-system {:web-port 3100})))

(deftest test-valid-email
  (is (true? (user/valid-email? "foo@bar.tld")))
  (is (false? (user/valid-email? ""))))

(deftest test-json-request
  (is (true? (user/json-request? {:content-type "application/json"})))
  (is (true? (user/json-request? {:content-type "application/json; charset=utf-8"})))
  (is (false? (user/json-request? {:content-type "text/html"}))))

(deftest test-ensure-content-type
  (is (nil? (user/ensure-content-type {:content-type "application/json"})))
  (is (= (user/ensure-content-type {})
         (utils/invalid-request "Non-JSON Content-Type"))))

(deftest test-ensure-args
  (is (nil? (user/ensure-args {:body {:email "user" :password "pass"}})))
  (is (= (user/ensure-args {:body {:password "pass"}})
         (utils/invalid-request "Missing: email")))
  (is (= (user/ensure-args {:body {:email "user"}})
         (utils/invalid-request "Missing: password"))))

(deftest test-ensure-email
  (is (nil? (user/ensure-email {:body {:email "foo@bar.tld"}})))
  (is (= (user/ensure-email {:body {:email "user"}})
         (utils/invalid-request "Invalid email"))))

(deftest test-ensure-unique
  (with-redefs [nautilus.database/connect-bucket (constantly (in-memory-store))]
    (alter-var-root #'system component/start))

  (let [db (:database system)]
    (database/new-user! db "foo@bar.tld" "hunter2")
    (is (nil? (user/ensure-unique {:db db :body {:email "maxc@me.com"}})))
    (is (= (user/ensure-unique {:db db :body {:email "foo@bar.tld"}})
           (utils/invalid-request "User exists")))

    (alter-var-root #'system component/stop)))

(deftest test-create-user
  (with-redefs [nautilus.database/connect-bucket (constantly (in-memory-store))]
    (alter-var-root #'system component/start))

  (let [db    (:database system)
        login "baz@qux.tld"]
    (is (= (user/create-user {:db db :body {:email login}})
           {:status 201 :body {:ok true}}))
    (is (true? (database/user-exists? db login))))

  (alter-var-root #'system component/stop))
