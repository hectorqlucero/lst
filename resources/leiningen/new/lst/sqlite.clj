(ns {{name}}.models.db.sqlite
  (:require [clojure.java.jdbc :as j]))

(defn q-opts [] {})

(defn table-sql-name [tname]
  tname)

(defn time-projection [field]
  (str "strftime('%H:%M', " field ") AS " field))

(defn describe-table [table query-fn]
  (let [rows (query-fn (str "PRAGMA table_info(" table ")"))]
    (map (fn [r]
           {:field (:name r)
            :type  (:type r)
            :null  (if (= 1 (:notnull r)) "NO" "YES")
            :default (:dflt_value r)
            :extra ""
            :privileges ""
            :comment ""
            :key (when (= 1 (:pk r)) "PRI")})
         rows)))

(defn primary-keys-fallback [_table _query-fn]
  [])

(defn last-insert-id [t-con]
  (some-> (j/query t-con ["SELECT last_insert_rowid() AS id"]) first :id))

(defn cascade-delete-child-images! [table row query-fn delete-fn]
  (let [ti         (query-fn (str "PRAGMA table_info(" table ")"))
        pk-field   (:name (first (filter #(= 1 (:pk %)) ti)))
        parent-col (or pk-field "id")
        tables     (map :name (query-fn ["SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name <> ?" table]))]
    (doseq [t tables]
      (let [fklist (query-fn (str "PRAGMA foreign_key_list(" t ")"))
            refs   (filter #(= (name table) (name (:table %))) fklist)]
        (doseq [r* refs]
          (let [fkcol      (or (:from r*) (str (name table) "_id"))
                tocol      (or (:to r*) parent-col)
                parent-val ((keyword tocol) row)
                cols       (query-fn (str "PRAGMA table_info(" t ")"))
                has-imagen (boolean (some #(= (name (:name %)) "imagen") cols))]
            (when (and has-imagen parent-val)
              (doseq [cr (query-fn [(str "SELECT imagen FROM " t " WHERE " fkcol " = ?") parent-val])]
                (when-let [im (:imagen cr)] (delete-fn im))))))))))
