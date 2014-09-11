(ns nautilus.test-middleware-oauth
  (:require [clojure.test               :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [nautilus.database          :as database]
            [nautilus.middleware.oauth  :as oauth]
            [nautilus.system            :as system]
            [nautilus.test-core         :as test-core]
            [nautilus.utils             :as utils]))

(use-fixtures :each test-core/fixtures-each)

;; TODO: test is shared with service middleware tests
(deftest test-ensure-client-authorized
  (let [client (:client test-core/system)]
    (is (nil? (oauth/ensure-client-authorized
                {:authorization {:username "foo"
                                 :password "bar"}
                 :client client})))
    (is (= (oauth/ensure-client-authorized {:client client})
           (-> (utils/error-response "unauthorized_client" "Invalid credentials")
               (assoc :status 401)
               (assoc :headers {"WWW-Authenticate" "Basic realm=\"nautilus\""}))))))

(deftest test-ensure-grant-type
  (is (nil? (oauth/ensure-grant-type {:form-params {"grant_type" "password"}})))
  (is (= (oauth/ensure-grant-type {:form-params {"grant_type" "bogus"}})
         (utils/error-response "unsupported_grant_type" "Invalid grant_type"))))

(deftest test-ensure-scope
  (is (nil? (oauth/ensure-scope {:form-params {}})))
  (is (= (oauth/ensure-scope {:form-params {"scope" "bogus"}})
         (utils/invalid-request "Invalid scope"))))

(deftest test-ensure-password-grant
  (is (nil? (oauth/ensure-password-grant {:form-params {"username" "foo"
                                                        "password" "bar"}})))
  (is (= (oauth/ensure-password-grant {:form-params {"username" "foo"}})
         (utils/invalid-request "Missing: password")))
  (is (= (oauth/ensure-password-grant {:form-params {"password" "bar"}})
         (utils/invalid-request "Missing: username"))))

(deftest test-ensure-user-authorized
  (let [db (:database test-core/system)]
    (database/new-user! db "foo@bar.tld" "hunter2")
    (is (nil? (oauth/ensure-user-authorized {:db db
                                             :form-params {"username" "foo@bar.tld"
                                                           "password" "hunter2"}})))
    (is (= (oauth/ensure-user-authorized {:db db
                                          :form-params {"username" "foo@bar.tld"
                                                        "password" "bogus"}})
           (utils/access-denied "Invalid credentials")))))

(deftest test-create-token
  (with-redefs [oauth/new-token (constantly 42)]
    (let [db (:database test-core/system)]
      (database/new-user! db "foo@bar.tld" "hunter2")
      (is (= (oauth/create-token {:db db
                                  :form-params {"username" "foo@bar.tld"}})
             {:status 201
              :body {:token_type "Bearer"
                     :access_token 42
                     :user {:login "foo@bar.tld"}}}))
      (is (true? (database/token-exists? db 42)))
      (is (= (database/get-token db 42)
             "foo@bar.tld")))))
