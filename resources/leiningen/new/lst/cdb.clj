(ns {{name}}.models.cdb
  (:require
   [clojure.string :as st]
   [buddy.hashers :as hashers]
   [{{name}}.models.crud :as crud :refer [Insert-multi Query!]]))

(def users-rows
  [{:lastname  "User"
    :firstname "Regular"
    :username  "user@example.com"
    :password  (hashers/derive "user")
    :dob       "1957-02-07"
    :email     "user@example.com"
    :level     "U"
    :active    "T"}
   {:lastname "User"
    :firstname "Admin"
    :username "admin@example.com"
    :password (hashers/derive "admin")
    :dob "1957-02-07"
    :email "admin@example.com"
    :level "A"
    :active "T"}
   {:lastname "User"
    :firstname "System"
    :username "system@example.com"
    :password (hashers/derive "system")
    :dob "1957-02-07"
    :email "system@example.com"
    :level "S"
    :active "T"}])


(defn- normalize-token [s]
  (some-> s str st/trim (st/replace #"^:+" "") st/lower-case))

(def ^:private vendor->subprotocol
  {"mysql"     #(or (= % "mysql") (= % :mysql))
   "postgres"  #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "postgresql" #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "pg"        #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "sqlite"    #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))
   "sqlite3"   #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))})

(defn- choose-conn-key
  "Resolve a user token (e.g., nil, pg, :pg, localdb, mysql) to a key in crud/dbs.
  Prefers exact connection keys (e.g., :pg, :localdb, :main, :default). Falls back to
  the first connection whose subprotocol matches a known vendor token. Defaults to :default."
  [token]
  (let [t (normalize-token token)
        dbs crud/dbs
        keys* (set (keys dbs))
        ;; map some common nicknames directly to configured keys
        t->key {"default" :default
                "mysql"   :default   ; assume default is mysql per config
                "main"    :main
                "pg"      :pg
                "postgres" :pg
                "postgresql" :pg
                "local"   :localdb
                "localdb" :localdb
                "sqlite"  :localdb
                "sqlite3" :localdb}
        direct (when (seq t)
                 (some (fn [k] (when (= (name k) t) k)) keys*))
        mapped (get t->key t)
        by-vendor (when (seq t)
                    (let [pred (get vendor->subprotocol t)]
                      (when pred
                        (some (fn [[k v]] (when (pred (:subprotocol v)) k)) dbs))))]
    (or direct mapped by-vendor :default)))

(defn populate-tables
  "Populate a table with rows on the selected connection. This version avoids vendor-specific
  locking and uses simple DELETE + batch insert wrapped in a transaction by Insert-multi."
  [table rows & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))
        ;; coerce row values to DB-appropriate types using schema introspection
        typed-rows (mapv (fn [row]
                           (crud/build-postvars table-s row :conn conn*))
                         rows)]
    (println (format "[database] Seeding %s on connection %s" table-s (name conn*)))
    (try
      ;; Clear existing rows (portable across MySQL/Postgres/SQLite)
      (Query! (str "DELETE FROM " table-s) :conn conn*)
      ;; Batch insert rows
      (Insert-multi (keyword table-s) typed-rows :conn conn*)
      (println (format "[database] Seeded %d rows into %s (%s)"
                       (count typed-rows) table-s (name conn*)))
      (catch Exception e
        (println "[ERROR] Seeding failed for" table-s "on" (name conn*) ":" (.getMessage e))
        (throw e)))))

(defn database
  "Usage:
   - lein database                 ; seeds default (mysql per config)
   - lein database pg              ; seeds Postgres (:pg)
   - lein database :pg             ; same as above
   - lein database localdb         ; seeds SQLite (:localdb)"
  [& args]
  (let [token (first args)
        conn  (choose-conn-key token)
        dbspec (get crud/dbs conn)
        sp (:subprotocol dbspec)]
    (println (format "[database] Using connection: %s (subprotocol=%s)" (name conn) sp))
    ;; add other tables here if needed
    (populate-tables "users" users-rows :conn conn)
    (println "[database] Done.")))
