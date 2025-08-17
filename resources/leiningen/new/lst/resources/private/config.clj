;; {{name}}/resources/private/config.clj
;;
;; Clean, intuitive configuration for single or multiple databases.
;;
;; For a single DB, use the top-level keys as shown in the :main example below.
;; For multiple DBs, use the :connections map, with a map for each DB connection.
;;
;; You can add any other app-specific keys (uploads, email, etc.) at the top level.

{:connections
 {;; --- Mysql database ---
  :mysql {:db-type   "mysql"                                 ;; "mysql", "postgresql", "sqlite", etc.
          :db-class  "com.mysql.cj.jdbc.Driver"              ;; JDBC driver class
          :db-name   "//localhost:3306/your_dbname"           ;; JDBC subname (host:port/db)
          :db-user   "root"
          :db-pwd    "your_password"}

  ;; --- Local SQLite database ---
  :sqlite {:db-type   "sqlite"
           :db-class  "org.sqlite.JDBC"
           :db-name   "db/your_dbname.sqlite"}                   ;; No user/pwd needed for SQLite

  ;; --- PostgreSQL database ---
  :postgres {:db-type   "postgresql"
             :db-class  "org.postgresql.Driver"
             :db-name   "//localhost:5432/your_dbname"
             :db-user   "root"
             :db-pwd    "your_password"}

  ;; --- Default connection used by the app ---
  :main :sqlite ; Used for migrations
  :default :sqlite ; Used for generators (lein grid, lein dashboard, etc.)
  :db :mysql
  :pg :postgres
  :localdb :sqlite}

 ;; --- Other global app settings ---
 :uploads      "./uploads/your_upload_folder/"      ;; Path for file uploads
 :site-name    "your_site_name"                 ;; App/site name
 :company-name "your_company_name"            ;; Company name
 :port         3000                        ;; App port
 :tz           "US/Pacific"                ;; Timezone
 :base-url     "http://0.0.0.0:3000/"      ;; Base URL
 :img-url      "https://0.0.0.0/uploads/"  ;; Image base URL
 :path         "/uploads/"                 ;; Uploads path (for web)
 :max-upload-mb 5                            ;; Optional: max image upload size in MB
 :allowed-image-exts ["jpg" "jpeg" "png" "gif" "bmp" "webp"] ;; Optional: allowed image extensions
 ;; --- Theme selection ---
 :theme "cerulean" ;; Options: "default" (Bootstrap), "cerulean", "slate", "minty", "lux", "cyborg", "sandstone", "superhero", "flatly", "yeti"
 ;; Optional email config
 :email-host   "smtp.example.com"
 :email-user   "user@example.com"
 :email-pwd    "emailpassword"}

;; --- USAGE NOTES ---
;; - For a single DB, you can use only :main in :connections, or remove :connections and use top-level keys.
;; - For multiple DBs, add more keys to :connections (e.g., :main, :analytics, :localdb, etc.).
;; - All DBs must specify :db-type, :db-class, and :db-name. :db-user and :db-pwd are optional for SQLite.
;; - The :uploads key is required for file upload features.
;; - All other keys are optional and for your app's use.
;;
;; --- EXAMPLES OF USAGE IN CODE ---
;; (Query db "SELECT * FROM users")
;; (Query pg "SELECT * FROM logs")
;; (Query localdb "SELECT * FROM settings")
;; (Insert db "users" {:name "Alice"})
;; (Insert pg "logs" {:event "login"})
;; (Insert localdb "settings" {:key "theme" :value "dark"})
;; (Query "SELECT * FROM logs" :conn :pg)
;; (Insert "logs" {:event "logout"} :conn :pg)

;; ----------------------------------------------------------------------
;; Example database configurations for different database engines
;; Place your configuration map above, or use :connections for multi-DB.
;;
;; --- MySQL ---
;; {:db-type "mysql"
;;  :db-class "com.mysql.cj.jdbc.Driver"
;;  :db-name "//localhost:3306/mydb"
;;  :db-user "root"
;;  :db-pwd  "password"
;;  :uploads "./uploads/"}

;; --- PostgreSQL ---
;; {:db-type "postgresql"
;;  :db-class "org.postgresql.Driver"
;;  :db-name "//localhost:5432/mydb"
;;  :db-user "postgres"
;;  :db-pwd  "password"
;;  :uploads "./uploads/"}

;; --- SQLite ---
;; {:db-type "sqlite"
;;  :db-class "org.sqlite.JDBC"
;;  :db-name "db/mydb.sqlite"
;;  :uploads "./uploads/"}

;; --- SQL Server ---
;; {:db-type "sqlserver"
;;  :db-class "com.microsoft.sqlserver.jdbc.SQLServerDriver"
;;  :db-name "//localhost:1433;databaseName=mydb"
;;  :db-user "sa"
;;  :db-pwd  "password"
;;  :uploads "./uploads/"}

;; --- H2 ---
;; {:db-type "h2"
;;  :db-class "org.h2.Driver"
;;  :db-name "~/mydb"
;;  :db-user "sa"
;;  :db-pwd  ""
;;  :uploads "./uploads/"}

;; --- Oracle ---
;; {:db-type "oracle"
;;  :db-class "oracle.jdbc.OracleDriver"
;;  :db-name "//localhost:1521/xe"
;;  :db-user "system"
;;  :db-pwd  "oracle"
;;  :uploads "./uploads/"}

;; --- Multiple Databases (multi-DB) ---
;; {:connections
;;  {:mysql {:db-type "mysql"
;;          :db-class "com.mysql.cj.jdbc.Driver"
;;          :db-name "//localhost:3306/mydb"
;;          :db-user "root"
;;          :db-pwd  "password"}
;;   :postgres {:db-type "postgresql"
;;               :db-class "org.postgresql.Driver"
;;               :db-name "//localhost:5432/analytics"
;;               :db-user "postgres"
;;               :db-pwd  "password"}}
;;  :uploads "./uploads/"}

;;
;; --- Configuration Help ---
;; - Only one top-level map is used. For a single DB, use the top-level keys as above.
;; - For multiple DBs, use the :connections map, with a map for each DB connection.
;; - :uploads is the path for file uploads and is required.
;; - :db-type and :db-class must match your JDBC driver and database.
;; - :db-name is the JDBC subname (host, port, db, and options as needed).
;; - For SQLite, :db-user and :db-pwd are not required.
;; - For Oracle, :db-name is usually in the form //host:port/service
;; - You can add other config keys as needed (email, site-name, etc.).
