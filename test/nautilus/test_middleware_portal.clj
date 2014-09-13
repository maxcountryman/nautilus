(ns nautilus.test-middleware-portal
  (:require [clojure.test               :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [nautilus.database          :as database]
            [nautilus.middleware.portal :as portal]
            [nautilus.system            :as system]
            [nautilus.test-core         :as test-core]
            [nautilus.utils             :as utils]))

(use-fixtures :each test-core/fixtures-each)

(deftest test-ensure-token
  (let [db (:database test-core/system)]
    (database/new-user! db "foo@bar.tld" "hunter2")
    (database/new-token! db "foo@bar.tld" "foo")
    (is (nil? (portal/ensure-token {:db db :bearer-token "foo"})))
    (is (= (portal/ensure-token {})
           (utils/invalid-request "Missing: Bearer token")))
    (is (= (portal/ensure-token {:db db :bearer-token 0})
           (-> (utils/access-denied "Invalid Bearer token")
               (assoc :status 401))))))

(deftest test-ensure-service
  (let [db (:database test-core/system)]
    (database/new-service! db "foo" {:host "http://example.com/"})
    (is (nil? (portal/ensure-service {:db db :path-params {:service "foo"}})))
    (is (= (portal/ensure-service {:db db :path-params {:service "bogus"}})
           (utils/invalid-request "No such service: bogus")))))

(deftest test-ensure-portal
  (let [portal (:portal test-core/system)]
    (swap! (:cache portal) assoc "foo" "bar")
    (is (nil? (portal/ensure-portal {:portal portal
                                     :headers {"x-portal-id" "foo"}})))
    (is (= (portal/ensure-portal {:portal portal})
           (utils/invalid-request "Missing: X-Portal-Id")))
    (is (= (portal/ensure-portal {:portal portal
                                  :headers {"x-portal-id" "bogus"}})
           (-> (utils/invalid-request "No such portal ID: bogus")
               (assoc :status 404))))))

(deftest test-ensure-liveness
  (let [portal (:portal test-core/system)]
    (with-redefs [utils/now (constantly 0)]
      (swap! (:cache portal) assoc "foo" {:ttl 1})

      (is (nil? (portal/ensure-liveness {:portal portal
                                         :headers {"x-portal-id" "foo"}})))

      ;; Set an expired TTL
      (swap! (:cache portal) assoc "foo" {:ttl 0})
      (is (= (portal/ensure-liveness {:portal portal
                                      :headers {"x-portal-id" "foo"}})
             (-> (utils/invalid-request "Expired portal")
                 (assoc :status 410)))))))

(deftest test-create-portal
  (let [portal       (:portal test-core/system)
        db           (:database test-core/system)
        bearer-token "foo"
        login        "foo@bar.tld"
        portal-uri   "http://localhost/portal"]

    (swap! (:cache portal) assoc "foo" {:ttl 0})
    (database/new-user! db login "hunter2")
    (database/new-token! db login bearer-token)
    (database/new-service! db "foo" {:host "http://example.com/"})

    (let [request {:db db
                   :portal portal
                   :bearer-token bearer-token}]
      (with-redefs [portal/create-id           (constantly 0)
                    portal/create-ttl          (constantly 0)
                    portal/request->portal-uri (constantly portal-uri)]
        (is (= (portal/create-portal request "foo" "bar")
               {:status 201
                :body   {:uri     portal-uri
                         :ttl     0
                         :headers {"X-Portal-Id" 0}}}))))))

(deftest test-retrieve-portal
  (let [db     (:database test-core/system)
        portal (:portal test-core/system)
        login  "foo@bar.tld"]
    (with-redefs [portal/proxy-request (constantly {})]
      (swap! (:cache portal) assoc "foo" {:login   login
                                          :service "bar"
                                          :path    "baz"})
      (is (= (portal/retrieve-portal {:db      db
                                      :portal  portal
                                      :headers {"x-portal-id" "foo"}})
             {:headers {"X-Portal-Login" login}})))))
