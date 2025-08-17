(ns {{name}}.models.db.postgres)

(defn q-opts [] {})

(defn table-sql-name [tname]
  tname)

(defn time-projection [field]
  (str "to_char(" field ", 'HH24:MI') AS " field))

(defn describe-table [table query-fn]
  (let [sql (str
             "SELECT column_name as field, data_type as type, is_nullable as null, column_default as default, '' as extra, '' as privileges, '' as comment, '' as key "
             "FROM information_schema.columns "
             "WHERE table_name = ? "
             "AND table_schema = ANY (current_schemas(true)) "
             "ORDER BY ordinal_position")]
    (query-fn [sql table])))

(defn primary-keys-fallback [table query-fn]
  (let [sql (str "SELECT a.attname as field "
                 "FROM pg_index i "
                 "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                 "WHERE i.indrelid = '" table "'::regclass AND i.indisprimary;")]
    (mapv :field (query-fn sql))))

(defn last-insert-id [_t-con]
  nil)

(defn cascade-delete-child-images! [table row query-fn delete-fn]
  (let [parent-col (or (:field (first (query-fn [(str "SELECT a.attname as field FROM pg_index i JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) WHERE i.indrelid = '" table "'::regclass AND i.indisprimary LIMIT 1")]))) "id")
        parent-val ((keyword parent-col) row)
        fk-sql (str
                "SELECT kcu.table_name AS child_table, kcu.column_name AS child_column, ccu.column_name AS parent_column "
                "FROM information_schema.table_constraints tc "
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema "
                "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema "
                "WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = ? AND ccu.column_name = ?")
        fks (query-fn [fk-sql table parent-col])]
    (doseq [{:keys [child_table child_column]} fks]
      (when (and child_table child_column parent-val
                 (seq (query-fn [(str "SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'imagen' LIMIT 1") child_table])))
        (doseq [cr (query-fn [(str "SELECT imagen FROM " child_table " WHERE " child_column " = ?") parent-val])]
          (when-let [im (:imagen cr)] (delete-fn im)))))))
