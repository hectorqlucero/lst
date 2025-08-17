(ns {{name}}.models.crud
  (:require
   [clojure.java.io :as io]
   [clojure.java.jdbc :as j]
   [clojure.string :as st]
   [{{name}}.models.db :as db]))

;; Reusable regex patterns (private constants)
(def ^:private true-re  #"(?i)^(true|on|1)$")
(def ^:private false-re #"(?i)^(false|off|0)$")
(def ^:private int-re   #"^-?\d+$")
(def ^:private float-re #"^-?\d+(\.\d+)?$")

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

;; Ensure SQLite enforces foreign keys on every connection by appending a
;; connection parameter to the JDBC URL. SQLite's PRAGMA foreign_keys is
;; per-connection and not persisted in the DB file, so setting it at the
;; URL level ensures all connections created by java.jdbc have it ON.
;; Supported by org.xerial/sqlite-jdbc.
(defn- sqlite-ensure-fk-param [subname]
  (let [s (str subname)]
    (if (re-find #"(?:\?|&)foreign_keys=(?:on|true|1)" s)
      s
      (str s (if (st/includes? s "?") "&" "?") "foreign_keys=on"))))

(defn build-db-spec [cfg]
  (let [dbtype (or (:db-type cfg) (:db-protocol cfg))
        base   {:user (:db-user cfg) :password (:db-pwd cfg)}]
    (letfn [(mysql-spec []
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
                      :zeroDateTimeBehavior            "CONVERT_TO_NULL"}))
            (postgres-spec []
              (merge base
                     {:classname   (or (:db-class cfg) "org.postgresql.Driver")
                      :subprotocol "postgresql"
                      :subname     (:db-name cfg)}))
            (sqlite-spec []
              (merge base
                     {:classname   (or (:db-class cfg) "org.sqlite.JDBC")
                      :subprotocol "sqlite"
                      :subname     (sqlite-ensure-fk-param (:db-name cfg))}))
            (sqlserver-spec []
              (merge base
                     {:classname   (or (:db-class cfg) "com.microsoft.sqlserver.jdbc.SQLServerDriver")
                      :subprotocol "sqlserver"
                      :subname     (:db-name cfg)}))
            (h2-spec []
              (merge base
                     {:classname   (or (:db-class cfg) "org.h2.Driver")
                      :subprotocol "h2"
                      :subname     (:db-name cfg)}))
            (oracle-spec []
              (merge base
                     {:classname   (or (:db-class cfg) "oracle.jdbc.OracleDriver")
                      :subprotocol "oracle:thin"
                      :subname     (:db-name cfg)}))]
      (cond
        (or (= dbtype "mysql") (= dbtype :mysql))                      (mysql-spec)
        (or (= dbtype "postgresql") (= dbtype :postgresql)
            (= dbtype "postgres") (= dbtype :postgres))                (postgres-spec)
        (or (= dbtype "sqlite") (= dbtype :sqlite)
            (= dbtype "sqlite3") (= dbtype :sqlite3))                  (sqlite-spec)
        (or (= dbtype "sqlserver") (= dbtype :sqlserver)
            (= dbtype "mssql") (= dbtype :mssql))                      (sqlserver-spec)
        (or (= dbtype "h2") (= dbtype :h2))                            (h2-spec)
        (or (= dbtype "oracle") (= dbtype :oracle))                    (oracle-spec)
        :else (throw (ex-info (str "Unsupported db-type: " dbtype) {:dbtype dbtype}))))))

;; Helper to resolve keyword indirection in :connections
(defn- resolve-conn [connections v]
  (loop [val v]
    (if (and (keyword? val) (contains? connections val))
      (recur (get connections val))
      val)))

(def dbs
  (if (and (:connections config) (map? (:connections config)))
    (into {}
          (keep (fn [[k v]]
                  (let [resolved (resolve-conn (:connections config) v)]
                    (when (map? resolved)
                      [k (build-db-spec resolved)])))
                (:connections config)))
    {:default (build-db-spec config)}))

(def db (or (get dbs :default) (first (vals dbs))))
(doseq [[k v] dbs]
  (when (not= k :default)
    (intern *ns* (symbol (str "db_" (name k))) v)))

;; --- helpers ---
(defn- resolve-db
  ([] db)
  ([conn] (or (get dbs (or conn :default)) db)))

(defn- normalize-insert-result [ins]
  (cond
    (map? ins) ins
    (sequential? ins) (first ins)
    :else ins))

;; --- small helpers extracted for Save / CRUD ---
(defn- update-count' [result]
  (cond
    (sequential? result) (long (or (first result) 0))
    (number? result) (long result)
    :else 0))

(defn- row-exists? [t-con db-spec t wherev qopts]
  (let [clause (first wherev)
        values (rest wherev)
        sql (str "SELECT 1 FROM " (db/table-sql-name db-spec t) " WHERE " clause " LIMIT 1")
        rs (j/query t-con (into [sql] values) qopts)]
    (seq rs)))

(defn- insert-with-id [t-con table row q-opts]
  (let [ins (j/insert! t-con table row (assoc q-opts :return-keys true))
        norm (normalize-insert-result ins)]
    (cond
      (map? norm) norm
      (number? norm) {:id norm}
      :else norm)))

(defn- save-with-db [db* table row where]
  (let [q-opts (db/q-opts db*)]
    (j/with-db-transaction [t-con db*]
      (let [result (j/update! t-con table row where q-opts)
            cnt (update-count' result)]
        (if (zero? cnt)
          (let [exists? (row-exists? t-con db* table where q-opts)]
            (if exists?
              true
              (let [ins-result (insert-with-id t-con table row q-opts)
                    fallback-id (db/last-insert-id t-con db*)]
                (or (when (map? ins-result) ins-result)
                    (when fallback-id {:id fallback-id})
                    true))))
          (pos? cnt))))))

;; --- CRUD wrappers (multi-arity) ---
(defn Query [& args]
  (cond
    ;; (Query db sql)
    (and (= 2 (count args)) (map? (first args)))
    (let [db* (first args)
          sql (second args)
          q-opts (db/q-opts db*)]
      (j/query db* sql q-opts))

    :else
    (let [sql (first args)
          opts (apply hash-map (rest args))
          db* (resolve-db (:conn opts))
          q-opts (db/q-opts db*)]
      (j/query db* sql q-opts))))

(defn Query! [& args]
  (cond
    ;; (Query! db sql)
    (and (= 2 (count args)) (map? (first args)))
    (let [db* (first args)
          sql (second args)
          q-opts (db/q-opts db*)]
      (j/execute! db* sql q-opts))

    :else
    (let [sql (first args)
          opts (apply hash-map (rest args))
          db* (resolve-db (:conn opts))
          q-opts (db/q-opts db*)]
      (j/execute! db* sql q-opts))))

(defn Insert [& args]
  (cond
    ;; (Insert db table row)
    (and (= 3 (count args)) (map? (first args)))
    (let [db* (first args)
          table (second args)
          row (nth args 2)
          q-opts (db/q-opts db*)]
      (j/insert! db* table row q-opts))

    :else
    (let [table (first args)
          row (second args)
          opts (apply hash-map (drop 2 args))
          db* (resolve-db (:conn opts))
          q-opts (db/q-opts db*)]
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
          q-opts (db/q-opts db*)]
      (j/update! db* table row where q-opts))

    :else
    (let [table (first args)
          row (second args)
          where (nth args 2)
          opts (apply hash-map (drop 3 args))
          db* (resolve-db (:conn opts))
          q-opts (db/q-opts db*)]
      (j/update! db* table row where q-opts))))

(defn Delete [& args]
  (try
    (cond
      ;; (Delete db table where)
      (and (= 3 (count args)) (map? (first args)))
      (let [db* (first args)
            table (second args)
            where (nth args 2)
            q-opts (db/q-opts db*)]
        (j/delete! db* table where q-opts))

      :else
      (let [table (first args)
            where (second args)
            opts (apply hash-map (drop 2 args))
            db* (resolve-db (:conn opts))
            q-opts (db/q-opts db*)]
        (j/delete! db* table where q-opts)))
    (catch Exception e
      (println "[ERROR] Delete failed:" (.getMessage e))
      (println "[ERROR] Exception details:" e)
      nil)))

(defn Save [& args]
  (cond
    ;; (Save db table row where)
    (and (= 4 (count args)) (map? (first args)))
    (let [db* (first args)
          table (second args)
          row (nth args 2)
          where (nth args 3)]
      (save-with-db db* table row where))

    :else
    (let [table (first args)
          row (second args)
          where (nth args 2)
          opts (apply hash-map (drop 3 args))
          db* (resolve-db (:conn opts))]
      (save-with-db db* table row where))))

;; --- schema discovery ---
(defn get-table-describe [table & {:keys [conn]}]
  (let [db* (resolve-db conn)
        rows (db/describe-table db* table (fn [sql] (Query db* sql)))]
    (when (and (db/postgres? db*) (empty? rows))
      (println "[WARN] No columns found for table" table "on Postgres. Check search_path and schema. Conn:" (or conn :default)))
    rows))

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

(defn- normalize-ftype [field-type]
  (-> field-type st/lower-case (st/replace #"\(.*\)" "") st/trim))

(defn- char->value [^String v]
  (let [v (st/trim v)
        vu (st/upper-case v)]
    (cond
      (st/blank? v) nil
      (re-matches true-re v)  "T"
      (re-matches false-re v) "F"
      :else vu)))

(defn- parse-int-like [^String s]
  (cond
    (st/blank? s) 0
    (re-matches true-re s)  1
    (re-matches false-re s) 0
    (re-matches int-re s) (try (Long/parseLong s) (catch Exception _ 0))
    :else 0))

(defn- parse-float-like [^String s]
  (cond
    (st/blank? s) 0.0
    (re-matches float-re s) (try (Double/parseDouble s) (catch Exception _ 0.0))
    :else 0.0))

(defn- parse-bool-like [^String s]
  (cond
    (st/blank? s) false
    (re-matches true-re s)  true
    (re-matches false-re s) false
    (re-matches int-re s) (not= s "0")
    :else false))

(defn process-field [params field field-type]
  (let [value (str ((keyword field) params))
        ftype (normalize-ftype field-type)]
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
      (char->value value)

      ;; Integer types
      (or (st/includes? field-type "int")
          (st/includes? field-type "tinyint")
          (st/includes? field-type "smallint")
          (st/includes? field-type "mediumint")
          (st/includes? field-type "bigint"))
      (parse-int-like value)

      ;; Floating point
      (or (st/includes? field-type "float")
          (st/includes? field-type "double")
          (st/includes? field-type "decimal"))
      (parse-float-like value)

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
      (parse-bool-like value)

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

;; Vendor-aware time field projection for SELECTs used by forms
(defn build-form-field [db* d]
  (db/time-projection db* (:field d) (:type d)))

(defn get-table-key [d]
  (when (seq d)
    (when-let [pk (first (filter #(or (= (:key %) "PRI") (= (:key %) "PRIMARY")) d))]
      (:field pk))))

(defn get-table-primary-keys
  ([table] (get-table-primary-keys table :conn :default))
  ([table & {:keys [conn]}]
   (let [db* (resolve-db conn)
         describe (get-table-describe table :conn conn)
         pks (db/primary-keys db* table describe (fn [sql] (Query db* sql)))]
     (vec (or (seq pks)
              (when (some #(= (name %) "id") (map (comp keyword :field) describe))
                ["id"])
              [])))))

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
       (let [db* (resolve-db conn)
             head "SELECT "
             body (apply str (interpose "," (map #(build-form-field db* %) describe)))
             pk-map (cond
                      (map? id-or-pk-map) id-or-pk-map
                      (= 1 (count pk-fields)) {(keyword (first pk-fields)) id-or-pk-map}
                      :else nil)]
         (when pk-map
           (let [[where-clause values] (build-pk-where-clause pk-map)
                 foot (str " FROM " table " WHERE " where-clause)
                 sql (str head body foot)
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
  (letfn [(single-id-where [id] (if (= id 0) ["1 = 0"] ["id = ?" id]))
          (pk-is-new? [m]
            (or (empty? m)
                (every? (fn [[_ v]]
                          (or (nil? v)
                              (and (string? v) (st/blank? v))
                              (and (number? v) (= v 0))
                              (= (str v) "0"))) m)))
          (try-save [db* table postvars where-clause]
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
                  (throw e)))))]
    (let [pk-fields (get-table-primary-keys table :conn conn)
          db* (resolve-db conn)]
      (if (= 1 (count pk-fields))
        (let [id (crud-fix-id (:id params))
              postvars (cond-> (-> (build-postvars table params :conn conn)
                                   blank->nil)
                         (= id 0) (dissoc :id))
              where-clause (single-id-where id)]
          (try-save db* table postvars where-clause))
        (let [pk-map (get-primary-key-map table params :conn conn)
              is-new? (pk-is-new? pk-map)
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
                (throw e)))))))))

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
                    :else nil)]
            (or (:generated_key m)
                (:generated-key m)
                (:id m)
                (:last_insert_rowid m)
                (:scope_identity m))))
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
                      :else nil)]
              (or (:generated_key m)
                  (:generated-key m)
                  (:id m)
                  (:last_insert_rowid m)
                  (:scope_identity m)
                  pk-values-or-id)))
          pk-values-or-id))

      :else pk-values-or-id)))

(defn process-upload-form [params table _folder & {:keys [conn]}]
  (letfn [(insert-then-upload-and-update! [db* table pk-name postvars file]
            (let [q-opts (db/q-opts db*)]
              (j/with-db-transaction [t-con db*]
                (let [ins (j/insert! t-con (keyword table) postvars (assoc q-opts :return-keys true))
                      norm (normalize-insert-result ins)
                      ins-id (or (:generated_key norm)
                                 (:generated-key norm)
                                 (:id norm)
                                 (:last_insert_rowid norm)
                                 (:scope_identity norm)
                                 (db/last-insert-id t-con db*))]
                  (when-not ins-id (throw (ex-info "Could not retrieve inserted ID" {:table table})))
                  (let [the-id (str ins-id)
                        path (str (:uploads config))
                        image-name (when (and the-id (not (st/blank? the-id)))
                                     (crud-upload-image table file the-id path))]
                    (when image-name
                      (j/update! t-con (keyword table) {:imagen image-name}
                                 [(str (name pk-name) " = ?") (try (Long/parseLong the-id) (catch Exception _ the-id))]
                                 q-opts))
                    true)))))
          (existing-or-composite-upload! [db* table pk-fields pk-map postvars is-new? file conn]
            (let [single-pk? (= 1 (count pk-fields))
                  the-id (if single-pk?
                           (str (or ((keyword (first pk-fields)) pk-map) ""))
                           (str (or (some identity (vals pk-map)) "")))
                  path (str (:uploads config))
                  image-name (when (and the-id (not (st/blank? the-id)))
                               (crud-upload-image table file the-id path))
                  effective-pk-map (if (and (not is-new?) single-pk?)
                                     {(keyword (first pk-fields)) (if (re-matches int-re the-id)
                                                                    (Long/parseLong the-id)
                                                                    the-id)}
                                     pk-map)
                  prev-row (when (and (not is-new?) (seq effective-pk-map))
                             (build-form-row table effective-pk-map :conn conn))
                  postvars (cond-> postvars image-name (assoc :imagen image-name))
                  [clause values] (build-pk-where-clause effective-pk-map)
                  where-clause (into [clause] values)
                  postvars* (apply dissoc postvars (map keyword pk-fields))
                  result (Save db* (keyword table) postvars* where-clause)]
              (when (and result image-name prev-row)
                (let [old (:imagen prev-row)]
                  (when (and (string? old) (not= old image-name))
                    (safe-delete-upload! old))))
              (boolean result)))]
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
            (boolean (insert-then-upload-and-update! db* table (keyword (first pk-fields)) postvars file))
            (existing-or-composite-upload! db* table pk-fields pk-map postvars is-new? file conn)))
        (let [[clause values] (build-pk-where-clause pk-map)
              result (Delete db* (keyword table) (into [clause] values))]
          (boolean result))))))

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

(defn- select-row [db* table id-or-pk pk-fields]
  (if (= 1 (count pk-fields))
    (first (Query db* (into [(str "SELECT * FROM " table " WHERE id = ?")] [(crud-fix-id id-or-pk)])))
    (when (map? id-or-pk)
      (let [[clause values] (build-pk-where-clause id-or-pk)]
        (first (Query db* (into [(str "SELECT * FROM " table " WHERE " clause)] values)))))))

(defn- cascade-delete-images! [db* table row]
  (let [query-fn (fn [sql]
                   (if (vector? sql)
                     (Query db* sql)
                     (Query db* sql)))]
    (try
      (db/cascade-delete-child-images! db* table row query-fn safe-delete-upload!)
      (when-let [childs (get (:cascade-image-delete config) (keyword table))]
        (doseq [{:keys [table fk image-col]} childs]
          (let [fkcol (or fk "id")
                icol  (or image-col "imagen")
                pval  ((keyword (or (first (get-table-primary-keys table)) "id")) row)
                sql   (str "SELECT " icol " FROM " table " WHERE " fkcol " = ?")
                rows  (Query db* [sql pval])]
            (doseq [r rows]
              (when-let [im ((keyword icol) r)]
                (safe-delete-upload! im))))))
      (catch Exception _ nil))))

(defn- perform-delete [db* table id-or-pk pk-fields]
  (if (= 1 (count pk-fields))
    (let [id (crud-fix-id id-or-pk)]
      (Delete db* (keyword table) ["id = ?" id]))
    (if (map? id-or-pk)
      (let [pk-map id-or-pk
            [clause values] (build-pk-where-clause pk-map)]
        (Delete db* (keyword table) (into [clause] values)))
      nil)))

(defn- build-form-delete* [db* table id-or-pk pk-fields]
  (try
    (let [row (select-row db* table id-or-pk pk-fields)]
      (when-let [img (:imagen row)] (safe-delete-upload! img))
      (when row (cascade-delete-images! db* table row))
      (boolean (seq (perform-delete db* table id-or-pk pk-fields))))
    (catch Exception e
      (println "[ERROR] build-form-delete failed:" (.getMessage e))
      (println "[ERROR] Exception details:" e)
      false)))

(defn build-form-delete
  ([table id-or-pk]
   (let [pk-fields (get-table-primary-keys table)]
     (build-form-delete* db table id-or-pk pk-fields)))
  ([table id-or-pk & {:keys [conn]}]
   (let [pk-fields (get-table-primary-keys table :conn conn)
         db* (resolve-db conn)]
     (build-form-delete* db* table id-or-pk pk-fields))))

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
