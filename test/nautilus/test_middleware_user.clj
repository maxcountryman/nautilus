(ns nautilus.test-middleware-user
  (:require [clojure.test               :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [nautilus.database          :as database]
            [nautilus.middleware.user   :as user]
            [nautilus.system            :as system]
            [nautilus.test-core         :as test-core]
            [nautilus.utils             :as utils]))

(use-fixtures :each test-core/fixtures-each)

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

(deftest test-ensure-create-args
  (is (nil? (user/ensure-create-args {:body {:email "user" :password "pass"}})))
  (is (= (user/ensure-create-args {:body {:password "pass"}})
         (utils/invalid-request "Missing: email")))
  (is (= (user/ensure-create-args {:body {:email "user"}})
         (utils/invalid-request "Missing: password"))))

(deftest test-ensure-email
  (is (nil? (user/ensure-email {:body {:email "foo@bar.tld"}})))
  (is (= (user/ensure-email {:body {:email "user"}})
         (utils/invalid-request "Invalid email"))))

(deftest test-ensure-unique
  (let [db (:database test-core/system)]
    (database/new-user! db "foo@bar.tld" "hunter2")
    (is (nil? (user/ensure-unique {:db db :body {:email "maxc@me.com"}})))
    (is (= (user/ensure-unique {:db db :body {:email "foo@bar.tld"}})
           (utils/invalid-request "User exists")))))

(deftest test-ensure-update-args
  (is (nil? (user/ensure-update-args {:body {:email "user" :metadata {}}})))
  (is (= (user/ensure-update-args {:body {:metadata {}}})
         (utils/invalid-request "Missing: email")))
  (is (= (user/ensure-update-args {:body {:email "user"}})
         (utils/invalid-request "Missing: metadata"))))

(deftest test-ensure-get-args
  (is (nil? (user/ensure-get-args {:body {:email "user"}})))
  (is (= (user/ensure-get-args {:body {}})
         (utils/invalid-request "Missing: email"))))

(deftest test-create-user
  (let [db    (:database test-core/system)
        login "foo@bar.tld"]
    (is (= (user/create-user {:db   db
                              :body {:email    login
                                     :password "hunter2"}})
           {:status 201 :body {}}))
    (is (true? (database/user-exists? db login)))))

(deftest test-update-user
  (let [db       (:database test-core/system)
        login    "foo@bar.tld"
        metadata {:foo :bar}]
    (is (= (user/update-user {:db   db
                              :body {:email login
                                     :metadata  metadata}})
           {:status 200 :body {:meta metadata}}))))

(deftest test-get-user
  (let [db       (:database test-core/system)
        login    "foo@bar.tld"
        metadata {:foo :bar}]
    ;; Create user.
    (user/create-user {:db db :body {:email login :password "hunter2"}})

    (is (= (user/get-user {:db   db
                           :body {:email login}})
           {:status 200 :body {}}))

    ;; Update user.
    (user/update-user {:db db :body {:email login :metadata metadata}})

    (is (= (user/get-user {:db db :body {:email login}})
           {:status 200 :body {:meta metadata}}))))
