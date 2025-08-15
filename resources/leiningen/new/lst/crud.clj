(ns {{name}}.models.crud
  (:require
   [clojure.java.io :as io]
   [clojure.java.jdbc :as j]
   [clojure.string :as st]))

;; Try to load optional drivers eagerly (safe if absent)
(try (Class/forName "org.sqlite.JDBC") (catch Throwable _))
(try (Class/forName "org.postgresql.Driver") (catch Throwable _))

;; --- configuration and connection management ---
(defn get-config []
  (try
    (binding [*read-eval* false]
      (some-> (io/resource "private/config.clj") slurp read-string))
    (catch Throwable e
      (println "[WARN] Failed to load config:" (.getMessage e))
      nil)))

(def config (or (get-config) {}))

;; 16-byte session encryption key for Ring cookie-store
(defn- ensure-16-bytes [^String s]
  (let [^bytes bs (.getBytes (or s "") "UTF-8")]
    (if (>= (alength bs) 16)
      (java.util.Arrays/copyOf bs 16)
      (let [padded (byte-array 16)]
        (System/arraycopy bs 0 padded 0 (alength bs))
        padded))))

(def KEY
  (let [secret (or (:session-secret config)
                   (get-in config [:security :session-secret])
                   "{{name}}-session-key")]
    (ensure-16-bytes secret)))

(defn build-db-spec [cfg]
  (let [dbtype (or (:db-type cfg) (:db-protocol cfg))
        base   {:user (:db-user cfg) :password (:db-pwd cfg)}]
    (cond
      (or (= dbtype "mysql") (= dbtype :mysql))
      (merge base
             {:classname    (or (:db-class cfg) "com.mysql.cj.jdbc.Driver")
              :subprotocol  "mysql"
              :subname      (:db-name cfg)
              :useSSL                          false
              :useTimezone                     true
              :useLegacyDatetimeCode           false
              :serverTimezone                  "UTC"
              :noTimezoneConversionForTimeType true
              :dumpQueriesOnException          true
              :autoDeserialize                 true
              :useDirectRowUnpack              false
              :cachePrepStmts                  true
              :cacheCallableStmts              true
              :cacheServerConfiguration        true
              :useLocalSessionState            true
              :elideSetAutoCommits             true
              :alwaysSendSetIsolation          false
              :enableQueryTimeouts             false
              :zeroDateTimeBehavior            "CONVERT_TO_NULL"})

      (or (= dbtype "postgresql") (= dbtype :postgresql) (= dbtype "postgres") (= dbtype :postgres))
      (merge base
             {:classname   (or (:db-class cfg) "org.postgresql.Driver")
              :subprotocol "postgresql"
              :subname     (:db-name cfg)})

      (or (= dbtype "sqlite") (= dbtype :sqlite) (= dbtype "sqlite3") (= dbtype :sqlite3))
      (merge base
             {:classname   (or (:db-class cfg) "org.sqlite.JDBC")
              :subprotocol "sqlite"
              :subname     (:db-name cfg)})

      (or (= dbtype "sqlserver") (= dbtype :sqlserver) (= dbtype "mssql") (= dbtype :mssql))
      (merge base
             {:classname   (or (:db-class cfg) "com.microsoft.sqlserver.jdbc.SQLServerDriver")
              :subprotocol "sqlserver"
              :subname     (:db-name cfg)})

      (or (= dbtype "h2") (= dbtype :h2))
      (merge base
             {:classname   (or (:db-class cfg) "org.h2.Driver")
              :subprotocol "h2"
              :subname     (:db-name cfg)})

      (or (= dbtype "oracle") (= dbtype :oracle))
      (merge base
             {:classname   (or (:db-class cfg) "oracle.jdbc.OracleDriver")
              :subprotocol "oracle:thin"
              :subname     (:db-name cfg)})

      :else (throw (ex-info (str "Unsupported db-type: " dbtype) {:dbtype dbtype})))))


;; Helper to resolve keyword indirection in :connections
(defn- resolve-conn [connections v]
  (loop [val v]
    (if (and (keyword? val) (contains? connections val))
      (recur (get connections val))
      val)))

(def dbs
  (if (and (:connections config) (map? (:connections config)))
    (into {}
          (for [[k v] (:connections config)]
            (let [resolved (resolve-conn (:connections config) v)]
              (when (map? resolved)
                [k (build-db-spec resolved)]))))
    {:default (build-db-spec config)}))

(def db (or (get dbs :default) (first (vals dbs))))
(doseq [[k v] dbs]
  (when (not= k :default)
    (intern *ns* (symbol (str "db_" (name k))) v)))

;; --- helpers ---
(defn- resolve-db
  ([] db)
  ([conn] (or (get dbs (or conn :default)) db)))

(defn- mysql? [db-spec]
  (let [sp (:subprotocol db-spec)]
    (or (= sp "mysql") (= sp :mysql))))


(defn- normalize-insert-result [ins]
  (cond
    (map? ins) ins
    (sequential? ins) (first ins)
    :else ins))

;; --- CRUD wrappers (multi-arity) ---
(defn Query [& args]
  (cond
    ;; (Query db sql)
    (and (= 2 (count args)) (map? (first args)))
    (let [db* (first args)
          sql (second args)
          q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
      (j/query db* sql q-opts))

    :else
    (let [sql (first args)
          opts (apply hash-map (rest args))
          db* (resolve-db (:conn opts))
          q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
      (j/query db* sql q-opts))))

(defn Query! [& args]
  (cond
    ;; (Query! db sql)
    (and (= 2 (count args)) (map? (first args)))
    (let [db* (first args)
          sql (second args)
          q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
      (j/execute! db* sql q-opts))

    :else
    (let [sql (first args)
          opts (apply hash-map (rest args))
          db* (resolve-db (:conn opts))
          q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
      (j/execute! db* sql q-opts))))

(defn Insert [& args]
  (cond
    ;; (Insert db table row)
    (and (= 3 (count args)) (map? (first args)))
    (let [db* (first args)
          table (second args)
          row (nth args 2)
          q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
      (j/insert! db* table row q-opts))

    :else
    (let [table (first args)
          row (second args)
          opts (apply hash-map (drop 2 args))
          db* (resolve-db (:conn opts))
          q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
      (j/insert! db* table row q-opts))))

(defn Insert-multi [& args]
  (cond
    ;; (Insert-multi db table rows)
    (and (= 3 (count args)) (map? (first args)))
    (j/with-db-transaction [t-con (first args)]
      (j/insert-multi! t-con (second args) (nth args 2)))

    :else
    (let [table (first args)
          rows (second args)
          opts (apply hash-map (drop 2 args))
          db* (resolve-db (:conn opts))]
      (j/with-db-transaction [t-con db*]
        (j/insert-multi! t-con table rows)))))

(defn Update [& args]
  (cond
    ;; (Update db table row where)
    (and (= 4 (count args)) (map? (first args)))
    (let [db* (first args)
          table (second args)
          row (nth args 2)
          where (nth args 3)
          q-opts (when (mysql? db*) {:entities (j/quoted \`)})]
      (j/update! db* table row where q-opts))

    :else
    (let [table (first args)
          row (second args)
          where (nth args 2)
          opts (apply hash-map (drop 3 args))
          db* (resolve-db (:conn opts))
          q-opts (when (mysql? db*) {:entities (j/quoted \`)})]
      (j/update! db* table row where q-opts))))

(defn Delete [& args]
  (try
    (cond
      ;; (Delete db table where)
      (and (= 3 (count args)) (map? (first args)))
      (let [db* (first args)
            table (second args)
            where (nth args 2)
            q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
        (j/delete! db* table where q-opts))

      :else
      (let [table (first args)
            where (second args)
            opts (apply hash-map (drop 2 args))
            db* (resolve-db (:conn opts))
            q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
        (j/delete! db* table where q-opts)))
    (catch Exception e
      (println "[ERROR] Delete failed:" (.getMessage e))
      (println "[ERROR] Exception details:" e)
      nil)))

(defn Save [& args]
  (letfn [(table-sql-name [db-spec t]
            (let [tname (if (keyword? t) (name t) (str t))]
              (if (mysql? db-spec) (str "`" tname "`") tname)))
          (exists-row? [t-con db-spec t wherev qopts]
            (let [clause (first wherev)
                  values (rest wherev)
                  sql (str "SELECT 1 FROM " (table-sql-name db-spec t) " WHERE " clause " LIMIT 1")
                  rs (j/query t-con (into [sql] values) qopts)]
              (seq rs)))
          (update-count [result]
            (cond
              (sequential? result) (long (or (first result) 0))
              (number? result) (long result)
              :else 0))]
    (cond
      ;; (Save db table row where)
      (and (= 4 (count args)) (map? (first args)))
      (let [db* (first args)
            table (second args)
            row (nth args 2)
            where (nth args 3)
            q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
        (j/with-db-transaction [t-con db*]
          (let [result (j/update! t-con table row where q-opts)
                cnt (update-count result)]

            (if (zero? cnt)
              (let [exists? (exists-row? t-con db* table where q-opts)]

                (if exists?
                  true
                  (let [ins-opts (assoc q-opts :return-keys true)
                        ins (j/insert! t-con table row ins-opts)
                        norm (normalize-insert-result ins)
                        sp (:subprotocol db*)
                        fallback-id (cond
                                      (or (= sp "sqlite") (= sp :sqlite) (= sp "sqlite3") (= sp :sqlite3))
                                      (some-> (j/query t-con ["SELECT last_insert_rowid() AS id"]) first :id)
                                      (or (= sp "mysql") (= sp :mysql))
                                      (some-> (j/query t-con ["SELECT last_insert_id() AS id"]) first :id)
                                      :else nil)
                        result-map (cond
                                     (map? norm) norm
                                     (number? norm) {:id norm}
                                     fallback-id {:id fallback-id}
                                     :else nil)]

                    (or result-map true))))
              (pos? cnt)))))

      :else
      (let [table (first args)
            row (second args)
            where (nth args 2)
            opts (apply hash-map (drop 3 args))
            db* (resolve-db (:conn opts))
            q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})]
        (j/with-db-transaction [t-con db*]
          (let [result (j/update! t-con table row where q-opts)
                cnt (update-count result)]

            (if (zero? cnt)
              (let [exists? (exists-row? t-con db* table where q-opts)]

                (if exists?
                  true
                  (let [ins-opts (assoc q-opts :return-keys true)
                        ins (j/insert! t-con table row ins-opts)
                        norm (normalize-insert-result ins)
                        sp (:subprotocol db*)
                        fallback-id (cond
                                      (or (= sp "sqlite") (= sp :sqlite) (= sp "sqlite3") (= sp :sqlite3))
                                      (some-> (j/query t-con ["SELECT last_insert_rowid() AS id"]) first :id)
                                      (or (= sp "mysql") (= sp :mysql))
                                      (some-> (j/query t-con ["SELECT last_insert_id() AS id"]) first :id)
                                      :else nil)
                        result-map (cond
                                     (map? norm) norm
                                     (number? norm) {:id norm}
                                     fallback-id {:id fallback-id}
                                     :else nil)]

                    (or result-map true))))
              (pos? cnt))))))))

;; --- schema discovery ---
(defn get-table-describe [table & {:keys [conn]}]
  (let [db* (resolve-db conn)
        dbtype (:subprotocol db*)]
    (cond
      (or (= dbtype "mysql") (= dbtype :mysql))
      (Query db* (str "DESCRIBE " table))

      (or (= dbtype "postgresql") (= dbtype :postgresql) (= dbtype "postgres") (= dbtype :postgres))
      (let [sql (str "SELECT column_name as field, data_type as type, is_nullable as null, column_default as default, '' as extra, '' as privileges, '' as comment, '' as key "
                     "FROM information_schema.columns "
                     "WHERE table_name = '" table "' "
                     "AND table_schema = ANY (current_schemas(true)) "
                     "ORDER BY ordinal_position")
            rows (Query db* sql)]
        (when (empty? rows)
          (println "[WARN] No columns found for table" table "on Postgres. Check search_path and schema. Conn:" (or conn :default)))
        rows)

      (or (= dbtype "sqlite") (= dbtype :sqlite) (= dbtype "sqlite3") (= dbtype :sqlite3))
      (let [sql (str "PRAGMA table_info(" table ")")
            rows (Query db* sql)]
        (map (fn [r]
               {:field (:name r)
                :type  (:type r)
                :null  (if (= 1 (:notnull r)) "NO" "YES")
                :default (:dflt_value r)
                :extra ""
                :privileges ""
                :comment ""
                :key (when (= 1 (:pk r)) "PRI")})
             rows))

      :else (throw (ex-info (str "Unsupported dbtype for describe: " dbtype) {:dbtype dbtype})))))

(defn get-table-columns [table & {:keys [conn]}]
  (map #(keyword (:field %)) (get-table-describe table :conn conn)))

(defn get-table-types [table & {:keys [conn]}]
  (map #(keyword (:type %)) (get-table-describe table :conn conn)))

;; --- temporal parsers ---
(defn- parse-sql-date [s]
  (let [s (some-> s st/trim)]
    (when (and s (not (st/blank? s)))
      (or (when (re-matches #"\d{4}-\d{2}-\d{2}" s)
            (java.sql.Date/valueOf s))
          (try
            (let [fmt (java.time.format.DateTimeFormatter/ofPattern "MM/dd/yyyy")
                  ld  (java.time.LocalDate/parse s fmt)]
              (java.sql.Date/valueOf ld))
            (catch Exception _ nil))
          (try (-> s java.time.LocalDate/parse java.sql.Date/valueOf)
               (catch Exception _ nil))
          (when (>= (count s) 10)
            (let [x (subs s 0 10)]
              (when (re-matches #"\d{4}-\d{2}-\d{2}" x)
                (java.sql.Date/valueOf x))))))))

(defn- parse-sql-time [s]
  (let [s (some-> s st/trim)]
    (when (and s (not (st/blank? s)))
      (or (when (re-matches #"\d{2}:\d{2}:\d{2}" s)
            (java.sql.Time/valueOf s))
          (when (re-matches #"\d{2}:\d{2}" s)
            (java.sql.Time/valueOf (str s ":00")))))))

(defn- parse-sql-timestamp [s]
  (let [s (some-> s st/trim)]
    (when (and s (not (st/blank? s)))
      (or (try
            (let [fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS")
                  ldt (java.time.LocalDateTime/parse s fmt)]
              (java.sql.Timestamp/valueOf ldt))
            (catch Exception _ nil))
          (try
            (let [fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")
                  ldt (java.time.LocalDateTime/parse s fmt)]
              (java.sql.Timestamp/valueOf ldt))
            (catch Exception _ nil))
          (try
            (let [fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm")
                  ldt (java.time.LocalDateTime/parse s fmt)]
              (java.sql.Timestamp/valueOf ldt))
            (catch Exception _ nil))
          (try
            (-> s java.time.OffsetDateTime/parse .toLocalDateTime java.sql.Timestamp/valueOf)
            (catch Exception _ nil))
          (try
            (-> s java.time.LocalDateTime/parse java.sql.Timestamp/valueOf)
            (catch Exception _ nil))))))

;; --- field processing ---

(defn process-field [params field field-type]
  (let [value (str ((keyword field) params))
        ftype (-> field-type st/lower-case (st/replace #"\(.*\)" "") st/trim)]
    (cond
      ;; String-like (include Postgres types)
      (or (st/includes? ftype "varchar")
          (= ftype "character varying")
          (st/includes? ftype "character varying")
          (= ftype "character")
          (st/includes? ftype "text")
          (st/includes? ftype "enum")
          (st/includes? ftype "set")) value

      ;; Strict CHAR only (likely MySQL char(N)). Normalize booleans if applicable; otherwise, don't truncate unless it is clearly a single char input.
      (= ftype "char")
      (let [v (st/trim value)
            vu (st/upper-case v)]
        (cond
          (st/blank? v) nil
          (re-matches #"(?i)true" v) "T"
          (re-matches #"(?i)on" v)   "T"
          (re-matches #"(?i)1" v)    "T"
          (re-matches #"(?i)false" v) "F"
          (re-matches #"(?i)off" v)   "F"
          (re-matches #"(?i)0" v)     "F"
          ;; preserve full string to avoid accidental truncation for non-boolean CHAR columns
          :else vu))

      ;; Integer types
      (or (st/includes? field-type "int")
          (st/includes? field-type "tinyint")
          (st/includes? field-type "smallint")
          (st/includes? field-type "mediumint")
          (st/includes? field-type "bigint"))
      (cond
        (st/blank? value) 0
        (re-matches #"(?i)true" value) 1
        (re-matches #"(?i)on" value)   1
        (re-matches #"(?i)false" value) 0
        (re-matches #"(?i)off" value)   0
        (re-matches #"^-?\d+$" value) (try (Long/parseLong value) (catch Exception _ 0))
        :else 0)

      ;; Floating point
      (or (st/includes? field-type "float")
          (st/includes? field-type "double")
          (st/includes? field-type "decimal"))
      (cond
        (st/blank? value) 0.0
        (re-matches #"^-?\d+(\.\d+)?$" value) (try (Double/parseDouble value) (catch Exception _ 0.0))
        :else 0.0)

      ;; Year
      (st/includes? field-type "year")
      (if (st/blank? value) nil (subs value 0 (min 4 (count value))))

      ;; Date/timestamp
      (or (st/includes? field-type "date")
          (st/includes? field-type "datetime")
          (st/includes? field-type "timestamp"))
      (cond
        (st/blank? value) nil
        (st/includes? field-type "date")      (parse-sql-date value)
        (st/includes? field-type "timestamp") (parse-sql-timestamp value)
        (st/includes? field-type "datetime")  (or (parse-sql-timestamp value)
                                                  (parse-sql-date value))
        :else nil)

      ;; Time
      (st/includes? field-type "time")
      (if (st/blank? value) nil (or (parse-sql-time value) value))

      ;; Binary/JSON
      (or (st/includes? field-type "blob")
          (st/includes? field-type "binary")
          (st/includes? field-type "varbinary")) value

      (or (st/includes? field-type "json") (st/includes? field-type "jsonb"))
      (if (st/blank? value) nil value)

      ;; Boolean
      (or (st/includes? field-type "bit")
          (st/includes? field-type "bool")
          (st/includes? field-type "boolean"))
      (cond
        (st/blank? value) false
        (re-matches #"(?i)true" value)  true
        (re-matches #"(?i)on" value)    true
        (re-matches #"(?i)false" value) false
        (re-matches #"(?i)off" value)   false
        (re-matches #"^-?\d+$" value) (not= value "0")
        :else false)

      :else value)))

(defn build-postvars [table params & {:keys [conn]}]
  (let [td (get-table-describe table :conn conn)
        ;; normalize keys
        params (into {} (map (fn [[k v]] [(if (keyword? k) k (keyword k)) v]) params))]
    (when (empty? td)
      (println "[WARN] get-table-describe returned no columns for table" table "conn" (or conn :default)))
    (let [m (into {}
                  (keep (fn [x]
                          (when ((keyword (:field x)) params)
                            {(keyword (:field x))
                             (process-field params (:field x) (:type x))}))
                        td))]
      (when (empty? m)
        (println "[WARN] build-postvars empty for" table "params" (keys params) "cols" (map :field td) "conn" (or conn :default)))
      m)))

(defn build-form-field [d]
  (let [field (:field d)
        field-type (:type d)]
    (cond
      (= field-type "time") (str "TIME_FORMAT(" field "," "'%H:%i') as " field)
      :else field)))

(defn get-table-key [d]
  (when (seq d)
    (when-let [pk (first (filter #(or (= (:key %) "PRI") (= (:key %) "PRIMARY")) d))]
      (:field pk))))

(defn get-table-primary-keys
  ([table] (get-table-primary-keys table :conn :default))
  ([table & {:keys [conn]}]
   (let [describe (get-table-describe table :conn conn)]
     (cond
       ;; MySQL describe has :key
       (some #(or (= (:key %) "PRI") (= (:key %) "PRIMARY")) describe)
       (->> describe (filter #(or (= (:key %) "PRI") (= (:key %) "PRIMARY"))) (map :field) vec)

       ;; Postgres: pg_index
       (seq describe)
       (let [db* (resolve-db conn)
             sql (str "SELECT a.attname as field "
                      "FROM pg_index i "
                      "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                      "WHERE i.indrelid = '" table "'::regclass AND i.indisprimary;")
             pk-cols (Query db* sql)
             pks (mapv :field pk-cols)
             pks (if (seq pks)
                   pks
                   (let [fields (map :field describe)]
                     (when (some #(= % "id") fields) ["id"])))]

         (or pks []))

       :else []))))

(defn get-primary-key-map
  ([table params] (get-primary-key-map table params :conn :default))
  ([table params & {:keys [conn]}]
   (let [pk-fields (get-table-primary-keys table :conn conn)]
     (into {}
           (keep (fn [field]
                   (when-let [value ((keyword field) params)]
                     [(keyword field) value]))
                 pk-fields)))))

(defn build-pk-where-clause [pk-map]
  (when (seq pk-map)
    (let [conditions (map (fn [[k _]] (str (name k) " = ?")) pk-map)
          values (vals pk-map)]
      [(str (st/join " AND " conditions)) values])))

(defn build-form-row
  ([table id-or-pk-map] (build-form-row table id-or-pk-map :conn :default))
  ([table id-or-pk-map & {:keys [conn]}]
   (let [describe (get-table-describe table :conn conn)
         pk-fields (get-table-primary-keys table :conn conn)]
     (when (seq pk-fields)
       (let [head "SELECT "
             body (apply str (interpose "," (map #(build-form-field %) describe)))
             pk-map (cond
                      (map? id-or-pk-map) id-or-pk-map
                      (= 1 (count pk-fields)) {(keyword (first pk-fields)) id-or-pk-map}
                      :else nil)]
         (when pk-map
           (let [[where-clause values] (build-pk-where-clause pk-map)
                 foot (str " FROM " table " WHERE " where-clause)
                 sql (str head body foot)
                 db* (resolve-db conn)
                 row (Query db* (into [sql] values))]
             (first row)))))
     (when (empty? pk-fields)
       (try (println "[WARN] No primary key detected for table" table "conn" (or conn :default) "describe fields" (map :field describe)) (catch Throwable _))))))

(defn blank->nil [m]
  (into {} (for [[k v] m] [k (if (and (string? v) (st/blank? v)) nil v)])))

(defn crud-fix-id [id]
  (cond
    (nil? id) 0
    (number? id) (long id)
    (string? id) (let [s (st/trim id)]
                   (if (or (empty? s) (= s "0"))
                     0
                     (try (Long/parseLong s) (catch Exception _ 0))))
    :else 0))

(defn remove-emptys [postvars]
  (if (map? postvars)
    (apply dissoc postvars (for [[k v] postvars :when (nil? v)] k))
    {}))

(defn process-regular-form [params table & {:keys [conn]}]
  (let [pk-fields (get-table-primary-keys table :conn conn)
        db* (resolve-db conn)]
    (if (= 1 (count pk-fields))
      (let [id (crud-fix-id (:id params))
            postvars (cond-> (-> (build-postvars table params :conn conn)
                                 blank->nil)
                       (= id 0) (dissoc :id))
            where-clause (if (= id 0) ["1 = 0"] ["id = ?" id])]
        (try

          (if (and (map? postvars) (seq postvars))
            (boolean (Save db* (keyword table) postvars where-clause))
            false)
          (catch Exception e
            (let [cause (or (.getCause e) e)
                  sqlstate (try (when (instance? java.sql.SQLException cause)
                                  (.getSQLState ^java.sql.SQLException cause))
                                (catch Throwable _ nil))]
              (try (println "[ERROR] Save failed for" table "where" where-clause "sqlstate" sqlstate "message" (.getMessage cause)) (catch Throwable _))
              (throw e)))))
      (let [pk-map (get-primary-key-map table params :conn conn)
            is-new? (or (empty? pk-map)
                        (every? (fn [[_ v]]
                                  (or (nil? v)
                                      (and (string? v) (st/blank? v))
                                      (and (number? v) (= v 0))
                                      (= (str v) "0"))) pk-map))
            base-postvars (-> (build-postvars table params :conn conn) blank->nil)
            postvars (if is-new?
                       (apply dissoc base-postvars (map keyword pk-fields))
                       base-postvars)]
        (try

          (if (and (map? postvars) (seq postvars))
            (let [[clause values] (when-not is-new? (build-pk-where-clause pk-map))
                  where-clause (if is-new? ["1 = 0"] (into [clause] values))]
              (boolean (Save db* (keyword table) postvars where-clause)))
            false)
          (catch Exception e
            (let [cause (or (.getCause e) e)
                  sqlstate (try (when (instance? java.sql.SQLException cause)
                                  (.getSQLState ^java.sql.SQLException cause))
                                (catch Throwable _ nil))]
              (try (println "[ERROR] Save failed for" table "sqlstate" sqlstate "message" (.getMessage cause)) (catch Throwable _))
              (throw e))))))))

(defn crud-upload-image [table file id path]
  (let [cfg-exts (set (map st/lower-case (or (:allowed-image-exts config) ["jpg" "jpeg" "png" "gif" "bmp" "webp"])))
        valid-exts cfg-exts
        max-mb (long (or (:max-upload-mb config) 0))
        tempfile   (:tempfile file)
        size       (:size file)
        orig-name  (:filename file)
        ext-from-name (when orig-name
                        (-> orig-name (st/split #"\.") last st/lower-case))]
    (when (and tempfile (pos? (or size 0)))
      (when (and (pos? max-mb) (> size (* max-mb 1024 1024)))
        (throw (ex-info (str "File too large (max " max-mb "MB)") {:type :upload-too-large :maxMB max-mb})))
      (let [ext (if (and ext-from-name (valid-exts ext-from-name))
                  (if (= ext-from-name "jpeg") "jpg" ext-from-name)
                  "jpg")
            image-name (str table "_" id "." ext)
            target-file (io/file (str path image-name))]
        (io/make-parents target-file)
        (io/copy tempfile target-file)
        image-name))))

;; --- uploads housekeeping ---
(defn- safe-delete-upload! [^String imagen]
  (when (and (string? imagen)
             (not (st/blank? imagen))
             (not (re-find #"[\\/]" imagen)))
    (let [f (io/file (str (:uploads config) imagen))]
      (when (.exists f)
        (try
          (.delete f)
          (catch Exception _))))))

(defn get-id [pk-values-or-id postvars table & {:keys [conn]}]
  (let [pk-fields (get-table-primary-keys table :conn conn)
        db* (resolve-db conn)]
    (cond
      (and (= 1 (count pk-fields)) (number? pk-values-or-id))
      (if (= pk-values-or-id 0)
        (when (map? postvars)
          (let [res (Save db* (keyword table) postvars ["1 = 0"]) ; Force insert
                m (cond
                    (map? res) res
                    (sequential? res) (first res)
                    :else nil)
                sp (:subprotocol db*)
                fallback (cond
                           (or (= sp "sqlite") (= sp :sqlite) (= sp "sqlite3") (= sp :sqlite3))
                           (some-> (Query db* ["SELECT last_insert_rowid() AS id"]) first :id)
                           (or (= sp "mysql") (= sp :mysql))
                           (some-> (Query db* ["SELECT last_insert_id() AS id"]) first :id)
                           :else nil)]
            (or (:generated_key m)
                (:generated-key m)
                (:id m)
                (:last_insert_rowid m)
                (:scope_identity m)
                fallback)))
        pk-values-or-id)

      (map? pk-values-or-id)
      (let [is-new? (every? (fn [[_ v]] (or (nil? v) (= v 0)
                                            (and (string? v) (st/blank? v))))
                            pk-values-or-id)]
        (if is-new?
          (when (map? postvars)
            (let [res (Save db* (keyword table) postvars ["1 = 0"]) ; Force insert
                  m (cond
                      (map? res) res
                      (sequential? res) (first res)
                      :else nil)
                  sp (:subprotocol db*)
                  fallback (cond
                             (or (= sp "sqlite") (= sp :sqlite) (= sp "sqlite3") (= sp :sqlite3))
                             (some-> (Query db* ["SELECT last_insert_rowid() AS id"]) first :id)
                             (or (= sp "mysql") (= sp :mysql))
                             (some-> (Query db* ["SELECT last_insert_id() AS id"]) first :id)
                             :else nil)]
              (or (:generated_key m)
                  (:generated-key m)
                  (:id m)
                  (:last_insert_rowid m)
                  (:scope_identity m)
                  fallback
                  pk-values-or-id)))
          pk-values-or-id))

      :else pk-values-or-id)))

(defn process-upload-form [params table _folder & {:keys [conn]}]
  (let [pk-fields (get-table-primary-keys table :conn conn)
        pk-map (get-primary-key-map table params :conn conn)
        file (:file params)
        postvars (dissoc (build-postvars table params :conn conn) :file)
        is-new? (or (empty? pk-map)
                    (every? (fn [[_ v]]
                              (or (nil? v)
                                  (and (string? v) (st/blank? v))
                                  (and (number? v) (= v 0))
                                  (= (str v) "0"))) pk-map))
        postvars (-> (if is-new?
                       (apply dissoc postvars (map keyword pk-fields))
                       postvars)
                     blank->nil)
        db* (resolve-db conn)]

    (if (and (map? postvars) (seq postvars))
      (let [single-pk? (= 1 (count pk-fields))]
        (if (and is-new? single-pk?)
          ;; New record with single PK: insert first to get ID in this transaction; then upload and update imagen
          (let [pk-name (keyword (first pk-fields))
                q-opts (if (mysql? db*) {:entities (j/quoted \`)} {})
                result (j/with-db-transaction [t-con db*]
                         (let [ins (j/insert! t-con (keyword table) postvars (assoc q-opts :return-keys true))
                               norm (normalize-insert-result ins)
                               sp (:subprotocol db*)
                               ins-id (or (:generated_key norm)
                                          (:generated-key norm)
                                          (:id norm)
                                          (:last_insert_rowid norm)
                                          (:scope_identity norm)
                                          (when (or (= sp "sqlite") (= sp :sqlite) (= sp "sqlite3") (= sp :sqlite3))
                                            (some-> (j/query t-con ["SELECT last_insert_rowid() AS id"]) first :id))
                                          (when (or (= sp "mysql") (= sp :mysql))
                                            (some-> (j/query t-con ["SELECT last_insert_id() AS id"]) first :id)))]
                           (when-not ins-id (throw (ex-info "Could not retrieve inserted ID" {:table table})))
                           (let [the-id (str ins-id)
                                 path (str (:uploads config))
                                 image-name (when (and the-id (not (st/blank? the-id)))
                                              (crud-upload-image table file the-id path))]

                             (when image-name
                               (j/update! t-con (keyword table) {:imagen image-name}
                                          [(str (name pk-name) " = ?") (try (Long/parseLong the-id) (catch Exception _ the-id))]
                                          q-opts))
                             true)))]
            (boolean result))
          ;; Existing record or composite key: behave like before
          (let [the-id (if single-pk?
                         (str (or ((keyword (first pk-fields)) pk-map) ""))
                         (str (or (some identity (vals pk-map)) "")))
                path (str (:uploads config))
                image-name (when (and the-id (not (st/blank? the-id)))
                             (crud-upload-image table file the-id path))
                effective-pk-map (if (and (not is-new?) single-pk?)
                                   {(keyword (first pk-fields)) (if (re-matches #"^\\d+$" the-id)
                                                                  (Long/parseLong the-id)
                                                                  the-id)}
                                   pk-map)
                prev-row (when (and (not is-new?) (seq effective-pk-map))
                           (build-form-row table effective-pk-map :conn conn))
                postvars (cond-> postvars
                           image-name (assoc :imagen image-name))
                [clause values] (build-pk-where-clause effective-pk-map)
                where-clause (into [clause] values)
                postvars* (apply dissoc postvars (map keyword pk-fields))
                result (Save db* (keyword table) postvars* where-clause)]
            (when (and result image-name prev-row)
              (let [old (:imagen prev-row)]
                (when (and (string? old) (not= old image-name))
                  (safe-delete-upload! old))))
            (boolean result))))
      (let [[clause values] (build-pk-where-clause pk-map)
            result (Delete db* (keyword table) (into [clause] values))]
        (boolean result)))))

;; --- public API ---
(defn build-form-save
  ([params table] (build-form-save params table :conn nil))
  ([params table & {:keys [conn]}]
   (let [file* (or (:file params) (get params "file"))
         non-empty-file? (and (map? file*) (pos? (or (:size file*) 0)))]

     (if non-empty-file?
       ;; normalize to keyword key to keep downstream logic consistent
       (process-upload-form (assoc params :file file*) table table :conn conn)
       (process-regular-form params table :conn conn)))))

(defn build-form-delete
  ([table id-or-pk]
   (try
     (let [pk-fields (get-table-primary-keys table)
           row (if (= 1 (count pk-fields))
                 (first (Query db (into [(str "SELECT * FROM " table " WHERE id = ?")] [(crud-fix-id id-or-pk)])))
                 (when (map? id-or-pk)
                   (let [[clause values] (build-pk-where-clause id-or-pk)]
                     (first (Query db (into [(str "SELECT * FROM " table " WHERE " clause)] values))))))
           _ (when-let [img (:imagen row)] (safe-delete-upload! img))
           result (if (= 1 (count pk-fields))
                    (let [id (crud-fix-id id-or-pk)]
                      (Delete db (keyword table) ["id = ?" id]))
                    (if (map? id-or-pk)
                      (let [pk-map id-or-pk
                            [clause values] (build-pk-where-clause pk-map)]
                        (Delete db (keyword table) (into [clause] values)))
                      nil))]
       (boolean (seq result)))
     (catch Exception e
       (println "[ERROR] build-form-delete failed:" (.getMessage e))
       (println "[ERROR] Exception details:" e)
       false)))
  ([table id-or-pk & {:keys [conn]}]
   (try
     (let [pk-fields (get-table-primary-keys table :conn conn)
           db* (resolve-db conn)
           row (if (= 1 (count pk-fields))
                 (first (Query db* (into [(str "SELECT * FROM " table " WHERE id = ?")] [(crud-fix-id id-or-pk)])))
                 (when (map? id-or-pk)
                   (let [[clause values] (build-pk-where-clause id-or-pk)]
                     (first (Query db* (into [(str "SELECT * FROM " table " WHERE " clause)] values))))))
           _ (when-let [img (:imagen row)] (safe-delete-upload! img))
           result (if (= 1 (count pk-fields))
                    (let [id (crud-fix-id id-or-pk)]
                      (Delete db* (keyword table) ["id = ?" id]))
                    (if (map? id-or-pk)
                      (let [pk-map id-or-pk
                            [clause values] (build-pk-where-clause pk-map)]
                        (Delete db* (keyword table) (into [clause] values)))
                      nil))]
       (boolean (seq result)))
     (catch Exception e
       (println "[ERROR] build-form-delete failed:" (.getMessage e))
       (println "[ERROR] Exception details:" e)
       false))))

;; --- small helpers for composite keys ---
(defn has-composite-primary-key?
  ([table] (has-composite-primary-key? table :conn :default))
  ([table & {:keys [conn]}] (> (count (get-table-primary-keys table :conn conn)) 1)))

(defn validate-primary-key-params
  ([table params] (validate-primary-key-params table params :conn :default))
  ([table params & {:keys [conn]}]
   (let [pk-fields (get-table-primary-keys table :conn conn)
         pk-map (get-primary-key-map table params :conn conn)]
     (= (count pk-fields) (count pk-map)))))

(defn build-pk-string [pk-map]
  (when (seq pk-map)
    (st/join "_" (map (fn [[k v]] (str (name k) "-" v)) pk-map))))
