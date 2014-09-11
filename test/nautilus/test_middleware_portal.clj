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
    (database/new-token! db 42 "foo@bar.tld")
    (is (nil? (portal/ensure-token {:db db :bearer-token 42})))))
