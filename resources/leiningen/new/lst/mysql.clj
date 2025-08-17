(ns {{name}}.models.db.mysql
  (:require [clojure.java.jdbc :as j]))

(defn q-opts [] {:entities (j/quoted \`)})

(defn table-sql-name [tname]
  (str "`" tname "`"))

(defn time-projection [field]
  (str "TIME_FORMAT(" field ", '%H:%i') AS " field))

(defn describe-table [table query-fn]
  (query-fn (str "DESCRIBE " table)))

(defn primary-keys-fallback [_table _query-fn]
  [])

(defn last-insert-id [t-con]
  (some-> (j/query t-con ["SELECT last_insert_id() AS id"]) first :id))

;; Cascade helpers
(defn- has-imagen? [child-table query-fn]
  (boolean
   (seq (query-fn [(str "SELECT 1 FROM information_schema.COLUMNS "
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                        "AND COLUMN_NAME='imagen' LIMIT 1")
                   child-table]))))

(defn- child-fks [table parent-col query-fn]
  (let [fk-sql (str
                "SELECT TABLE_NAME AS child_table, COLUMN_NAME AS child_column, REFERENCED_COLUMN_NAME AS parent_column "
                "FROM information_schema.KEY_COLUMN_USAGE "
                "WHERE REFERENCED_TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = ? AND REFERENCED_COLUMN_NAME = ?")]
    (query-fn [fk-sql table parent-col])))

(defn- select-imagen-rows [child-table child-column parent-val query-fn]
  (let [sql (str "SELECT imagen FROM " child-table " WHERE " child-column " = ?")]
    (query-fn [sql parent-val])))

(defn cascade-delete-child-images! [table row query-fn delete-fn]
  (let [pk-sql (str "SELECT k.COLUMN_NAME as col "
                    "FROM information_schema.KEY_COLUMN_USAGE k "
                    "WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = ? "
                    "AND k.CONSTRAINT_NAME = 'PRIMARY' LIMIT 1")
        pk-row (first (query-fn [pk-sql table]))
        parent-col (or (:col pk-row) "id")
        parent-val ((keyword parent-col) row)
        fks        (child-fks table parent-col query-fn)]
    (doseq [{:keys [child_table child_column]} fks]
      (when (and child_table child_column parent-val (has-imagen? child_table query-fn))
        (doseq [cr (select-imagen-rows child_table child_column parent-val query-fn)]
          (when-let [im (:imagen cr)] (delete-fn im)))))))
