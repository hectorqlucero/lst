
(ns {{name}}.migrations
  (:require
   [{{name}}.models.crud :as crud]
   [clojure.java.io :as io]
   [clojure.string :as st]
   [ragtime.jdbc :as jdbc]
   [ragtime.repl :as repl]))



;; Loads migration config for a given connection key (e.g., :main, :analytics, :localdb)


;; Find migration files for a given dbtype (e.g., "sqlite") using filesystem path for dev
(defn migration-files-for-dbtype [dbtype]
  (let [migdir "resources/migrations"
        files (->> (.listFiles (io/file migdir))
                   (map #(.getName %))
                   (filter #(re-find (re-pattern (str ".*\\." dbtype "\\.(up|down)\\.sql$")) %)))]
    (map #(str migdir "/" %) (sort files))))




;; Helper: parse migration file into {:id ... :up ...} or {:id ... :down ...}

;; Parse migration file into {:id ... :up ...} or {:id ... :down ...}





(defn parse-migration-file [filename]
  (let [content (slurp filename)
        fname (-> filename (st/split #"/") last)]
    (when-let [m (re-matches #"^(.+)\.([^.]+)\.(up|down)\.sql$" fname)]
      (let [[_ id _ direction] m
            dir-key (keyword direction)]
        {:id id dir-key content}))))

;; Group up/down migrations by id and build migration maps for Ragtime

;; Group up/down migrations by id and build migration maps for Ragtime

;; Improved: Ensure both :up and :down keys are present, and migration IDs are unique and sorted



(defn split-sql [sql]
  (->> (clojure.string/split sql #";[\r\n]*")
       (map clojure.string/trim)
       (filter seq)))

(defn build-migrations [migration-files]
  (let [parsed (map parse-migration-file migration-files)
        grouped (->> parsed
                     (group-by :id)
                     (sort-by (fn [[id _]] id)))]
    (let [migrations (mapv (fn [[id ms]]
                             (let [up   (->> ms (filter :up) (map :up) first)
                                   down (->> ms (filter :down) (map :down) first)
                                   up-stmts (when up (split-sql up))
                                   down-stmts (when down (split-sql down))
                                   m {:id id :up up-stmts :down down-stmts}]

                               (jdbc/sql-migration m)))
                           grouped)]
      migrations)))


(defn ensure-sqlite-db-file [conn]
  (when (and (= (:subprotocol conn) "sqlite")
             (string? (:subname conn)))
    (let [dbfile (:subname conn)]
      (when-not (.exists (io/file dbfile))
        (io/make-parents dbfile)
        (spit dbfile "")))))


(defn- normalize-conn-key [k]
  (cond
    (keyword? k) k
    (string? k) (-> (if (.startsWith ^String k ":") (subs k 1) k)
                    keyword)
    :else (-> (str k)
              (cond-> (.startsWith ^String (str k) ":") (subs 1))
              keyword)))

(defn load-config
  ([] (load-config :main))
  ([conn-key]
   (let [conn-key (normalize-conn-key conn-key)
         conn (or (get crud/dbs conn-key)
                  (get crud/dbs :default)
                  crud/db)
         dbtype-raw (or (:subprotocol conn)
                        (:db-type (get-in crud/config [:connections conn-key]))
                        "mysql")
         dbtype (if (#{"mysql" "postgresql" "sqlite" "sqlserver" "h2" "oracle"} dbtype-raw)
                  dbtype-raw
                  "mysql")

         migration-files (migration-files-for-dbtype dbtype)]
     (ensure-sqlite-db-file conn)
     (let [migrations (build-migrations migration-files)]
       {:datastore (jdbc/sql-database conn)
        :migrations migrations}))))



(defn migrate
  ([] (migrate :main))
  ([conn-key]
   (repl/migrate (load-config conn-key))))



(defn rollback
  ([] (rollback :main))
  ([conn-key]
   (repl/rollback (load-config conn-key))))


(comment
  ;; Run migrations on the main (default) database
  (migrate)
  (rollback)

  ;; Run migrations on a specific connection (e.g., :analytics, :localdb)
  (migrate :analytics)
  (rollback :localdb)

  ;; See which db-spec is being used
  (load-config)
  (load-config :analytics))
