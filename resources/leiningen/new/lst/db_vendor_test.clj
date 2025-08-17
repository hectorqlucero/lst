(ns {{name}}.db-vendor-test
  (:require [clojure.test :refer [deftest is testing]]
            [{{name}}.models.db :as db]
            [clojure.string :as str]
            [clojure.java.jdbc :as j]))

(def mysql-spec {:subprotocol "mysql"})
(def pg-spec    {:subprotocol "postgresql"})
(def sqlite-spec {:subprotocol "sqlite"})

(deftest describe-table-mysql-call-test
  (testing "mysql describe-table delegates and passes DESCRIBE SQL"
    (let [captured (atom nil)
          rows     [{:field "id" :key "PRI"}]
          qfn      (fn [sql]
                     (reset! captured sql)
                     rows)
          out      (db/describe-table mysql-spec "people" qfn)]
      (is (= rows out))
      (is (= (str "DESCRIBE " "people") @captured)))))

(deftest describe-table-postgres-call-test
  (testing "postgres describe-table delegates and uses parameter vector"
    (let [captured (atom nil)
          rows     [{:field "id"}]
          qfn      (fn [arg]
                     (reset! captured arg)
                     rows)
          out      (db/describe-table pg-spec "people" qfn)]
      (is (= rows out))
      (is (vector? @captured))
      (is (= "people" (second @captured)))
      (is (str/includes? (first @captured) "information_schema.columns")))))

(deftest last-insert-id-tests
  (testing "last-insert-id for mysql and sqlite via jdbc stub"
    (with-redefs [j/query (fn [_ _] [{:id 101}])]
      (is (= 101 (db/last-insert-id {} mysql-spec)))
      (is (= 101 (db/last-insert-id {} sqlite-spec))))
    (testing "postgres returns nil"
      (is (nil? (db/last-insert-id {} pg-spec))))))

(deftest cascade-delete-child-images-mysql-test
  (testing "deletes child images for mysql vendor"
    (let [calls (atom [])
          delete-fn (fn [imagen] (swap! calls conj imagen))
          qfn (fn [sql]
                (cond
                  ;; PK name for parent table
                  (and (vector? sql)
                       (str/starts-with? (first sql) "SELECT k.COLUMN_NAME as col "))
                  [{:col "id"}]

                  ;; FKs referencing parent(id)
                  (and (vector? sql)
                       (str/starts-with? (first sql) "SELECT TABLE_NAME AS child_table"))
                  [{:child_table "c1" :child_column "parent_id" :parent_column "id"}]

                  ;; has imagen column in child
                  (and (vector? sql)
                       (str/starts-with? (first sql) "SELECT 1 FROM information_schema.COLUMNS "))
                  [{}]

                  ;; select images from child by fk
                  (and (vector? sql)
                       (let [[s v] sql]
                         (and (= s "SELECT imagen FROM c1 WHERE parent_id = ?") (= v 42))))
                  [{:imagen "m1a.jpg"} {:imagen "m1b.png"}]

                  :else []))
          parent-row {:id 42}
          _ (db/cascade-delete-child-images! mysql-spec "parent" parent-row qfn delete-fn)]
      (is (= ["m1a.jpg" "m1b.png"] @calls)))))

(deftest cascade-delete-child-images-postgres-test
  (testing "deletes child images for postgres vendor"
    (let [calls (atom [])
          delete-fn (fn [imagen] (swap! calls conj imagen))
          qfn (fn [sql]
                (cond
                  ;; parent PK name
                  (and (vector? sql)
                       (str/starts-with? (first sql) "SELECT a.attname as field "))
                  [{:field "id"}]

                  ;; FKs referencing parent(id)
                  (and (vector? sql)
                       (str/includes? (first sql) "FROM information_schema.table_constraints"))
                  [{:child_table "c1" :child_column "parent_id" :parent_column "id"}]

                  ;; child has imagen column
                  (and (vector? sql)
                       (= (first sql) "SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'imagen' LIMIT 1"))
                  [{}]

                  ;; select images from child by fk
                  (and (vector? sql)
                       (let [[s v] sql]
                         (and (= s "SELECT imagen FROM c1 WHERE parent_id = ?") (= v 42))))
                  [{:imagen "p1a.jpg"} {:imagen "p1b.png"}]

                  :else []))
          parent-row {:id 42}
          _ (db/cascade-delete-child-images! pg-spec "parent" parent-row qfn delete-fn)]
      (is (= ["p1a.jpg" "p1b.png"] @calls)))))
