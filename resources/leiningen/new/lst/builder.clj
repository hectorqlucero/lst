(ns {{name}}.builder
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [{{name}}.models.crud :as crud :refer [get-table-columns]]
            [{{name}}.models.routes :as routes]))
;; --- GRID TEMPLATES (existing) ---


;; Controller template with rights check
(def controller-template
  "(ns {{name}}.handlers.admin._table_.controller
  (:require
   [{{name}}.handlers.admin._table_.model :refer [get-_table_ get-_table_-id]]
   [{{name}}.handlers.admin._table_.view :refer [_table_-view _table_-form-view]]
   [{{name}}.layout :refer [application error-404]]
   [{{name}}.models.crud :refer [build-form-delete build-form-save]]
   [{{name}}.models.util :refer [get-session-id user-level]]
   [hiccup.core :refer [html]]))

(def allowed-rights _rights_)

(defn _table_
  [request]
  (let [title \"_TableTitle_\"
        ok (get-session-id request)
        js nil
        rows (get-_table_)
        content (_table_-view title rows)
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
      (application request title ok js content)
      (application request title ok nil (str \"Not authorized to access this item! (level(s) \" allowed-rights \")\")))))

(defn _table_-add-form
  [_]
  (let [title \"New _TableTitle_\"
        row nil
        content (_table_-form-view title row)]
    (html content)))

(defn _table_-edit-form
  [_ id]
  (let [title \"Edit _TableTitle_\"
        row (get-_table_-id id)
        content (_table_-form-view title row)]
    (html content)))

(defn _table_-save
  [{params :params}]
  (let [table \"_table_\"
  result (build-form-save params table :conn _conn_)]
    (if result
      {:status 200 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":true}\"}
      {:status 500 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":false}\"})))

(defn _table_-delete
  [_ id]
  (let [table \"_table_\"
  result (build-form-delete table id :conn _conn_)]
    (if result
      {:status 302 :headers {\"Location\" \"/admin/_table_\"}}
      (error-404 \"Unable to process record!\" \"/admin/_table_\"))))
")

(def view-template
  "(ns {{name}}.handlers.admin._table_.view
  (:require [{{name}}.models.form :refer [form build-field build-modal-buttons]]
            [{{name}}.models.grid :refer [build-grid build-grid-with-subgrids]]))

;; _table_-view: If you want to show subgrids, pass a :subgrids vector in args.
;; Example:
;;   (let [args {:new true
;;               :edit true
;;               :delete true
;;               :subgrids [{:title \"Phones\"
;;                           :table-name \"phones\"
;;                           :foreign-key \"user_id\"
;;                           :href \"/admin/phonesusers\"
;;                           :icon \"bi bi-telephone\"
;;                           :label \"Phones\"}]}]
;;     (build-grid-with-subgrids title rows table-id fields href args))
;; Otherwise, just use build-grid as usual.

(defn _table_-view
  [title rows & [args]]
  (let [labels [_labels_]
        db-fields [_dbfields_]
        fields (apply array-map (interleave db-fields labels))
        table-id \"_table__table\"
        href \"/admin/_table_\"
        args (or args {:new true :edit true :delete true})]
    (if (and (map? args) (contains? args :subgrids))
      (build-grid-with-subgrids title rows table-id fields href args)
      (build-grid title rows table-id fields href args))))

(defn build-_table_-fields
  [row]
  (list
    (build-field {:id \"id\" :type \"hidden\" :name \"id\" :value (:id row)})
    _fields_
  ))

(defn _table_-form-view
  [title row]
  (form \"/admin/_table_/save\" (build-_table_-fields row) (build-modal-buttons) title {:bare true}))
")

(def model-template
  "(ns {{name}}.handlers.admin._table_.model
  (:require [{{name}}.models.crud :refer [Query]]))

(def get-_table_-sql
  (str \"SELECT * FROM _table_\"))

(defn get-_table_
  []
  (Query get-_table_-sql :conn _conn_))

(defn get-_table_-id
  [id]
  (first (Query (str \"SELECT * FROM _table_ WHERE id=\" id) :conn _conn_)))
")

;; --- DASHBOARD TEMPLATES (new) ---

(def controller-dashboard-template
  "(ns {{name}}.handlers._table_.controller
  (:require
   [{{name}}.handlers._table_.model :refer [get-_table_]]
   [{{name}}.handlers._table_.view :refer [_table_-view]]
   [{{name}}.layout :refer [application]]
   [{{name}}.models.util :refer [get-session-id user-level]]))

(def allowed-rights _rights_)

(defn _table_
  [request]
  (let [title \"_TableTitle_\"
        ok (get-session-id request)
        js nil
        rows (get-_table_)
        content (_table_-view title rows)
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
      (application request title ok js content)
      (application request title ok nil (str \"Not authorized to access this item! (level(s) \" allowed-rights \")\")))))
")

(def view-dashboard-template
  "(ns {{name}}.handlers._table_.view
  (:require [{{name}}.models.grid :refer [build-dashboard]]))

(defn _table_-view
  [title rows]
  (let [labels [_labels_]
        db-fields [_dbfields_]
        fields (apply array-map (interleave db-fields labels))
        table-id \"_table__table\"]
    (build-dashboard title rows table-id fields)))
")

(def model-dashboard-template
  "(ns {{name}}.handlers._table_.model
  (:require [{{name}}.models.crud :refer [Query]]))

(def get-_table_-sql
  (str \"SELECT * FROM _table_\"))

(defn get-_table_
  []
  (Query get-_table_-sql :conn _conn_))
")

;; --- REPORT TEMPLATES (new) ---

(def controller-report-template
  "(ns {{name}}.handlers.reports._table_.controller
  (:require
   [{{name}}.handlers.reports._table_.model :refer [get-_table_]]
   [{{name}}.handlers.reports._table_.view :refer [_table_-view]]
   [{{name}}.layout :refer [application]]
   [{{name}}.models.util :refer [get-session-id user-level]]))

(def allowed-rights _rights_)

(defn _table_
  [request]
  (let [title \"_TableTitle_ Report\"
        ok (get-session-id request)
        js nil
        rows (get-_table_)
        content (_table_-view title rows)
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
      (application request title ok js content)
      (application request title ok nil (str \"Not authorized to access this item! (level(s) \" allowed-rights \")\")))))
")

(def view-report-template
  "(ns {{name}}.handlers.reports._table_.view
  (:require [{{name}}.models.grid :refer [build-dashboard]]))

(defn _table_-view
  [title rows]
  (let [table-id \"_table__table\"
        labels []
        db-fields []
        fields (apply array-map (interleave db-fields labels))]
    (build-dashboard title rows table-id fields)))
")

(def model-report-template
  "(ns {{name}}.handlers.reports._table_.model
  (:require [{{name}}.models.crud :refer [Query]]))

(def get-_table_-sql
  (str \"SELECT * FROM _table_\"))

(defn get-_table_
  []
  (Query get-_table_-sql :conn _conn_))
")

;; --- SUBGRID TEMPLATES (new) ---

(def controller-subgrid-template
  "(ns {{name}}.handlers.admin._subgrid-ns_.controller
  (:require
   [{{name}}.handlers.admin._subgrid-ns_.model :refer [get-_table_ get-_table_-id]]
   [{{name}}.handlers.admin._subgrid-ns_.view :refer [_table_-view build-_table_-form]]
   [{{name}}.models.crud :refer [build-form-delete build-form-save crud-fix-id]]
   [{{name}}.models.util :refer [user-level]]
   [hiccup.core :refer [html]]))

(def allowed-rights _rights_)

;; Main subgrid endpoint - this is what the subgrid AJAX calls
(defn _subgrid-ns_-grid
  [request]
  (let [params (:params request)
  parent-id (crud-fix-id (get params :parent_id))
        title \" _TableTitle_ \"
        rows (get-_table_ parent-id)
        content (_table_-view title rows parent-id)
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
      {:status 200
       :headers {\"Content-Type\" \"text/html\"}
       :body (html content)}
      {:status 403
       :headers {\"Content-Type\" \"text/html\"}
       :body \"Not authorized to access this item!\"})))

(defn _subgrid-ns_-add-form
  [request parent-id]
  (let [title \"New _TableTitle_\"
  row {:_parent_key_ (crud-fix-id parent-id)}
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
      (html (build-_table_-form title row))
      {:status 403 :headers {\"Content-Type\" \"text/html\"} :body \"Not authorized to access this item!\"})))

(defn _subgrid-ns_-edit-form
  [request id]
  (let [title \"Edit _TableTitle_\"
  row (get-_table_-id (crud-fix-id id))
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
      (html (build-_table_-form title row))
      {:status 403 :headers {\"Content-Type\" \"text/html\"} :body \"Not authorized to access this item!\"})))

(defn _subgrid-ns_-save
  [request]
  (let [params (:params request)
        table \"_table_\"
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
  (let [result (build-form-save params table :conn _conn_)]
        (if result
          {:status 200 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":true}\"}
          {:status 500 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":false}\"}))
      {:status 403 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":false,\\\"error\\\":\\\"Not authorized\\\"}\"})))

(defn _subgrid-ns_-delete
  [request id]
  (let [table \"_table_\"
        user-r (user-level request)]
    (if (some #(= user-r %) allowed-rights)
  (let [result (build-form-delete table (crud-fix-id id) :conn _conn_)]
        (if result
          {:status 200 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":true}\"}
          {:status 500 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":false}\"}))
      {:status 403 :headers {\"Content-Type\" \"application/json\"} :body \"{\\\"ok\\\":false,\\\"error\\\":\\\"Not authorized\\\"}\"})))
")

(def view-subgrid-template
  "(ns {{name}}.handlers.admin._subgrid-ns_.view
  (:require [{{name}}.models.form :refer [form build-field build-modal-buttons]]
            [{{name}}.models.grid :refer [build-grid build-grid-with-custom-new build-grid]]))

(defn _table_-view
  ([title rows]
   ;; If no parent-id, fallback to normal grid
   (build-grid title rows \"_table__table\" (apply array-map (interleave [_dbfields_] [_labels_])) \"/admin/_table_\" {:new true :edit true :delete true}))
  ([title rows parent-id]
   (let [labels [_labels_]
         db-fields [_dbfields_]
         fields (apply array-map (interleave db-fields labels))
         table-id \"_table__table\"
         href \"/admin/_subgrid-ns_\"
         args {:new true :edit true :delete true}
         new-record-href (if parent-id
                           (str href \"/add-form/\" parent-id)
                           (str href \"/add-form\"))]
     (if parent-id
       (build-grid-with-custom-new title rows table-id fields href args new-record-href)
       (build-grid title rows table-id fields href args)))))

(defn build-_table_-fields
  [row]
  (list
    (build-field {:id \"id\" :type \"hidden\" :name \"id\" :value (:id row)})
    (build-field {:id \"_parent_key_\" :type \"hidden\" :name \"_parent_key_\" :value (:_parent_key_ row)})
    _fields_
  ))

(defn build-_table_-form
  [title row]
  (form \"/admin/_subgrid-ns_/save\" (build-_table_-fields row) (build-modal-buttons) title {:bare true}))
")

(def model-subgrid-template
  "(ns {{name}}.handlers.admin._subgrid-ns_.model
  (:require [{{name}}.models.crud :refer [Query crud-fix-id]]))

(def get-_table_-sql
  (str \"SELECT _sql_fields_\n         FROM _table_ t\n         WHERE t._parent_key_ = ?\n         ORDER BY _order_field_\"))

(defn get-_table_
  [parent-id]
  (Query [get-_table_-sql parent-id] :conn _conn_))

(def get-_table_-id-sql
  (str \"SELECT _sql_fields_\n         FROM _table_\n         WHERE id = ?\"))

(defn get-_table_-id
  [id]
  (first (Query [get-_table_-id-sql (crud-fix-id id)] :conn _conn_)))
")

;; --- TEMPLATE RENDERING ---
(defn render-template [template m]
  (reduce (fn [s [k v]]
            (let [pattern (case k
                            :table "_table_"
                            :subgrid-ns "_subgrid-ns_"
                            :TableTitle "_TableTitle_"
                            :ParentTitle "_ParentTitle_"
                            :parent_table "_parent_table_"
                            :parent_key "_parent_key_"
                            :parent_id "_parent_id_"
                            :labels "_labels_"
                            :dbfields "_dbfields_"
                            :fields "_fields_"
                            :sql_fields "_sql_fields_"
                            :order_field "_order_field_"
                            :rights "_rights_"
                            :conn_kw "_conn_"
                            nil)]
              (if pattern
                (str/replace s pattern v)
                s)))
          template
          m))

(defn field-block [fields]
  (apply str
         (for [[label field] fields]
           (str "(build-field {:label \"" label "\""
                " :type \"text\" :id \"" field "\""
                " :name \"" field "\""
                " :placeholder \"" label " here...\" :required false :value (get row :" field ")})\n"))))

(defn auto-label [field]
  (-> field
      (clojure.string/replace #"_" " ")
      (clojure.string/capitalize)))

;; --- FILE TOUCH UTILITY ---
(defn touch-file [path]
  (let [f (io/file path)]
    (when (.exists f)
      (let [orig (slurp f)
            marker (str "\n;; reload " (System/currentTimeMillis) "\n")]
        (spit f (str orig marker))
        (spit f orig)))))

;; --- FILE GENERATION ---

;; Normalize rights tokens from CLI (e.g., :rights [U A S] or :rights ["U" "A" "S"]) to
;; a proper Clojure vector literal string like ["U" "A" "S"].
(defn normalize-rights
  [tokens]
  (let [joined (str/join " " tokens)
        inside (-> joined
                   (str/replace #"^\s*\[\s*" "")
                   (str/replace #"\s*\]\s*$" ""))
        parts (->> (str/split inside #"[\s,]+")
                   (remove str/blank?)
                   (map #(str/replace % #"^\"|\"$" "")))]
    (if (seq parts)
      (str "[" (str/join " " (map #(str "\"" % "\"") parts)) "]")
      "[\"U\" \"A\" \"S\"]")))

;; --- DB selection helpers and config default update ---
(defn- normalize-token [s]
  (some-> s str str/trim (str/replace #"^:+" "") str/lower-case))

(def ^:private vendor->pred
  {"mysql"     #(or (= % "mysql") (= % :mysql))
   "postgres"  #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "postgresql" #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "pg"        #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "sqlite"    #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))
   "sqlite3"   #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))})

(defn- choose-conn-key [token]
  (let [t (normalize-token token)
        dbs crud/dbs
        direct (when (seq t)
                 (some (fn [k] (when (= (name k) t) k)) (keys dbs)))
        mapped ({"default" :default
                 "mysql"   :default
                 "main"    :main
                 "pg"      :pg
                 "postgres" :pg
                 "postgresql" :pg
                 "local"   :localdb
                 "localdb" :localdb
                 "sqlite"  :localdb
                 "sqlite3" :localdb} t)
        by-vendor (when (seq t)
                    (let [pred (get vendor->pred t)]
                      (when pred
                        (some (fn [[k v]] (when (pred (:subprotocol v)) k)) dbs))))]
    (or direct mapped by-vendor :default)))

(defn- parse-db-and-args
  "If first arg looks like a DB token (pg, mysql, sqlite, :pg, :localdb, or a configured key),
  use it as connection and drop it from args. Recognizes optional :set-default flag to update
  resources/private/config.clj's :connections :default to the chosen connection's map."
  [args]
  (let [argsv (vec args)
        first-token (first argsv)
        conn (choose-conn-key first-token)
        used-first? (or (not= conn :default)
                        (= (normalize-token first-token) "default"))
        tail (if used-first? (subvec argsv (min 1 (count argsv))) argsv)
        set-default? (some #(= % ":set-default") tail)
        tail (vec (remove #(= % ":set-default") tail))]
    {:conn (if used-first? conn :default)
     :args tail
     :set-default? set-default?}))

(defn- update-config-default!
  [conn]
  (try
    (let [path "resources/private/config.clj"
          ^String s (slurp path)
          chosen (get-in crud/config [:connections conn])]
      (when-not chosen (throw (ex-info (str "Unknown connection key: " conn) {})))
      (let [start (str/index-of s ":default")
            open (when start (str/index-of s "{" start))
            close (when open
                    (loop [i (inc open) depth 1]
                      (when (< i (count s))
                        (let [ch (.charAt s i)
                              depth' (cond (= ch \{) (inc depth)
                                           (= ch \}) (dec depth)
                                           :else depth)]
                          (if (zero? depth')
                            i
                            (recur (inc i) depth'))))))]
        (when (and start open close)
          (let [before (subs s 0 start)
                after (subs s (inc close))
                replacement (str ":default " (pr-str chosen))]
            (spit path (str before replacement after))
            (println (str "[builder] Updated default connection to " (name conn)))))))
    (catch Exception e
      (println "[builder] WARNING: Could not update config default:" (.getMessage e)))))

;; Small util: find the index of x in a sequential collection; returns nil if not found
(defn- find-index [xs x]
  (first (keep-indexed (fn [i v] (when (= v x) i)) xs)))


;; Accept rights argument, default to ["U" "A" "S"]
(defn generate-files [table fields conn & [rights]]
  (let [TableTitle (str/capitalize table)
        labels (str/join " " (map (fn [[label _]] (str "\"" label "\"")) fields))
        dbfields (str/join " " (map (fn [[_ field]] (str ":" field)) fields))
        fields-block (field-block fields)
        rights-str (or rights "[\"U\" \"A\" \"S\"]")
        m {:table table
           :TableTitle TableTitle
           :labels labels
           :dbfields dbfields
           :fields fields-block
           :rights rights-str
           :conn_kw (str ":" (name conn))}
        base-path (str "src/{{name}}/handlers/admin/" table "/")]
    (io/make-parents (str base-path "controller.clj"))
    (spit (str base-path "controller.clj") (render-template controller-template m))
    (spit (str base-path "view.clj") (render-template view-template m))
    (spit (str base-path "model.clj") (render-template model-template m))
    (routes/process-grid table)
    ;; Load the newly generated namespaces to ensure wrap-reload recognizes them
    (require (symbol (str "{{name}}.handlers.admin." table ".controller")))
    (require (symbol (str "{{name}}.handlers.admin." table ".view")))
    (require (symbol (str "{{name}}.handlers.admin." table ".model")))
    ;; Ensure all file operations are complete by reading the files back
    (doseq [file-type ["controller.clj" "view.clj" "model.clj"]]
      (slurp (str base-path file-type)))
    (println (str "Code generated in: src/{{name}}/handlers/admin/" table))))


;; Accept rights argument for dashboard
(defn generate-dashboard-files [table fields conn & [rights]]
  (let [TableTitle (str/capitalize table)
        labels (str/join " " (map (fn [[label _]] (str "\"" label "\"")) fields))
        dbfields (str/join " " (map (fn [[_ field]] (str ":" field)) fields))
        rights-str (or rights "[\"U\" \"A\" \"S\"]")
        m {:table table
           :TableTitle TableTitle
           :labels labels
           :dbfields dbfields
           :rights rights-str
           :conn_kw (str ":" (name conn))}
        base-path (str "src/{{name}}/handlers/" table "/")]
    (io/make-parents (str base-path "controller.clj"))
    (spit (str base-path "controller.clj") (render-template controller-dashboard-template m))
    (spit (str base-path "view.clj") (render-template view-dashboard-template m))
    (spit (str base-path "model.clj") (render-template model-dashboard-template m))
    (routes/process-dashboard table)
    ;; Load the newly generated namespaces to ensure wrap-reload recognizes them
    (require (symbol (str "{{name}}.handlers." table ".controller")))
    (require (symbol (str "{{name}}.handlers." table ".view")))
    (require (symbol (str "{{name}}.handlers." table ".model")))
    ;; Ensure all file operations are complete by reading the files back
    (doseq [file-type ["controller.clj" "view.clj" "model.clj"]]
      (slurp (str base-path file-type)))
    (println (str "Dashboard code generated in: src/{{name}}/handlers/" table))))


;; Accept rights argument for report
(defn generate-report-files [table conn & [rights]]
  (let [TableTitle (str/capitalize table)
        rights-str (or rights "[\"U\" \"A\" \"S\"]")
        m {:table table
           :TableTitle TableTitle
           :rights rights-str
           :conn_kw (str ":" (name conn))}
        base-path (str "src/{{name}}/handlers/reports/" table "/")]
    (io/make-parents (str base-path "controller.clj"))
    (spit (str base-path "controller.clj") (render-template controller-report-template m))
    (spit (str base-path "view.clj") (render-template view-report-template m))
    (spit (str base-path "model.clj") (render-template model-report-template m))
    (routes/process-report table)
    ;; Load the newly generated namespaces to ensure wrap-reload recognizes them
    (require (symbol (str "{{name}}.handlers.reports." table ".controller")))
    (require (symbol (str "{{name}}.handlers.reports." table ".view")))
    (require (symbol (str "{{name}}.handlers.reports." table ".model")))
    ;; Ensure all file operations are complete by reading the files back
    (doseq [file-type ["controller.clj" "view.clj" "model.clj"]]
      (slurp (str base-path file-type)))
    (println (str "Report code generated in: src/{{name}}/handlers/reports/" table))))



;; Accept rights argument for subgrid
(defn generate-subgrid-files
  [table parent-table parent-key view-fields sql-fields conn & [rights]]
  ;; Use composite subgrid name for handler dir and routes
  (let [subgrid-name (str table parent-table)
        TableTitle (str/capitalize table)
        ParentTitle (str/capitalize parent-table)
        labels (str/join " " (map (fn [[label _]] (str "\"" label "\"")) view-fields))
        dbfields (str/join " " (map (fn [[_ field]] (str ":" field)) view-fields))
        fields-block (field-block view-fields)
        sql-fields-list (str/join ", " (concat ["id" parent-key] sql-fields))
        order-field (if (seq sql-fields)
                      (str "t." (first sql-fields))
                      "t.id")
        rights-str (or rights "[\"U\" \"A\" \"S\"]")
        m {:table table
           :subgrid-ns subgrid-name
           :TableTitle TableTitle
           :ParentTitle ParentTitle
           :parent_table parent-table
           :parent_key parent-key
           :parent_id (str parent-table "_id")
           :labels labels
           :dbfields dbfields
           :fields fields-block
           :sql_fields sql-fields-list
           :order_field order-field
           :rights rights-str
           :conn_kw (str ":" (name conn))}
        base-path (str "src/{{name}}/handlers/admin/" subgrid-name "/")]
    (io/make-parents (str base-path "controller.clj"))
    (spit (str base-path "controller.clj") (render-template controller-subgrid-template m))
    (spit (str base-path "view.clj") (render-template view-subgrid-template m))
    (spit (str base-path "model.clj") (render-template model-subgrid-template m))
    (routes/process-subgrid subgrid-name parent-table)
    ;; Load the newly generated namespaces to ensure wrap-reload recognizes them
    (require (symbol (str "{{name}}.handlers.admin." subgrid-name ".controller")))
    (require (symbol (str "{{name}}.handlers.admin." subgrid-name ".view")))
    (require (symbol (str "{{name}}.handlers.admin." subgrid-name ".model")))
    ;; Ensure all file operations are complete by reading the files back
    (doseq [file-type ["controller.clj" "view.clj" "model.clj"]]
      (slurp (str base-path file-type)))
    (println (str "Subgrid code generated in: src/{{name}}/handlers/admin/" subgrid-name "/"))))

;; --- USAGE ---

(defn usage []
  (println "Usage: lein run -m builder grid <table> <Label1>:<field1> <Label2>:<field2> ...")
  (println "       lein run -m builder dashboard <table> <Label1>:<field1> <Label2>:<field2> ...")
  (println "       lein run -m builder report <table>")
  (println "       lein run -m builder subgrid <table> <parent-table> <parent-key> <Label1>:<field1> <Label2>:<field2> ...")
  (println "")
  (println "Lein aliases available:")
  (println "       lein grid [db] <table> <Label1>:<field1> <Label2>:<field2> ... [:rights U A S] [:set-default]")
  (println "       lein dashboard [db] <table> <Label1>:<field1> <Label2>:<field2> ... [:rights U A S] [:set-default]")
  (println "       lein report [db] <table> [:rights U A S] [:set-default]")
  (println "       lein subgrid [db] <table> <parent-table> <parent-key> <Label1>:<field1> <Label2>:<field2> ... [:rights U A S] [:set-default]")
  (println "")
  (println "Example: lein grid users Name:name Email:email")
  (println "         lein grid pg users Name:name Email:email ; generate for Postgres and set it default with :set-default")
  (println "         lein grid users Name:name :conn pg        ; alternative form without changing default")
  (println "         lein dashboard users Name:name Email:email")
  (println "         lein report users")
  (println "         lein subgrid user_contacts users user_id \"Contact Name\":contact_name Email:email"))

;; --- MAIN ENTRYPOINTS ---


;; Accept :rights [..] as last argument
(defn build-grid
  [& args]
  (let [{:keys [conn args set-default?]} (parse-db-and-args args)
        [table & rest-args-seq] args
        rest-args (vec rest-args-seq)
        rights-idx (find-index rest-args ":rights")
        ;; allow alternative form: ... :conn pg
        conn-flag-idx (find-index rest-args ":conn")
        conn2 (when (some? conn-flag-idx)
                (choose-conn-key (nth rest-args (inc conn-flag-idx) nil)))
        rest-args (if (some? conn-flag-idx)
                    (vec (concat (subvec rest-args 0 conn-flag-idx)
                                 (subvec rest-args (min (count rest-args) (+ conn-flag-idx 2)))))
                    rest-args)
        conn (or conn2 conn)
        [field-pairs rights] (if (some? rights-idx)
                               (let [after (subvec rest-args (inc rights-idx))]
                                 [(subvec rest-args 0 rights-idx)
                                  (normalize-rights after)])
                               [rest-args nil])]
    (cond
      (nil? table) (usage)
      ;; Check if grid already exists
      (.exists (io/file (str "src/{{name}}/handlers/admin/" table)))
      (println (str "Grid for '" table "' already exists. Skipping generation."))
      (empty? field-pairs)
      ;; If no fields given, auto-generate from DB
      (let [fields (for [field (map name (get-table-columns table :conn conn))]
                     [(auto-label field) field])]
        (when set-default? (update-config-default! conn))
        (generate-files table (rest fields) conn rights)
        ;; Ensure file operations are complete
        (Thread/sleep 100)
        ;; Reload the routes namespaces to pick up the new routes
        (require '{{name}}.routes.proutes :reload)
        (require '{{name}}.routes.routes :reload)
        ;; Reload core to ensure the dynamic app picks up changes
        (require '{{name}}.core :reload)
        (println (str "Grid '" table "' generated successfully! The routes should be available immediately.")))
      :else
      (let [fields (map #(let [[label field] (str/split % #":")]
                           [label field])
                        field-pairs)]
        (when set-default? (update-config-default! conn))
        (generate-files table fields conn rights)
        ;; Ensure file operations are complete
        (Thread/sleep 200)
        ;; Reload the routes namespaces to pick up the new routes
        (require '{{name}}.routes.proutes :reload)
        (require '{{name}}.routes.routes :reload)
        ;; Reload core to re-evaluate the app with new routes
        (require '{{name}}.core :reload)
        ;; Force a more aggressive reload by touching and reloading again
        (touch-file "src/{{name}}/core.clj")
        (Thread/sleep 200)
        (require '{{name}}.core :reload)
        ;; Final reload to ensure everything is picked up
        (Thread/sleep 100)
        (require '{{name}}.core :reload)
        (println (str "Grid '" table "' generated successfully! You may need to refresh your browser."))))))


;; Accept :rights [..] as last argument
(defn build-dashboard
  [& args]
  (let [{:keys [conn args set-default?]} (parse-db-and-args args)
        [table & rest-args-seq] args
        rest-args (vec rest-args-seq)
        rights-idx (find-index rest-args ":rights")
        [field-pairs rights] (if (some? rights-idx)
                               (let [after (subvec rest-args (inc rights-idx))]
                                 [(subvec rest-args 0 rights-idx)
                                  (normalize-rights after)])
                               [rest-args nil])]
    (cond
      (nil? table) (usage)
      ;; Check if dashboard already exists
      (.exists (io/file (str "src/{{name}}/handlers/" table)))
      (println (str "Dashboard for '" table "' already exists. Skipping generation."))
      (empty? field-pairs)
      ;; If no fields given, auto-generate from DB
      (let [fields (for [field (map name (get-table-columns table :conn conn))]
                     [(auto-label field) field])]
        (when set-default? (update-config-default! conn))
        (generate-dashboard-files table (rest fields) conn rights)
        ;; Ensure file operations are complete
        (Thread/sleep 100)
        ;; Reload the routes namespaces to pick up the new routes
        (require '{{name}}.routes.proutes :reload)
        (require '{{name}}.routes.routes :reload)
        ;; Reload core to re-evaluate the app with new routes
        (require '{{name}}.core :reload)
        ;; Force a more aggressive reload by touching and reloading again
        (touch-file "src/{{name}}/core.clj")
        (Thread/sleep 100)
        (require '{{name}}.core :reload))
      :else
      (let [fields (map #(let [[label field] (str/split % #":")]
                           [label field])
                        field-pairs)]
        (when set-default? (update-config-default! conn))
        (generate-dashboard-files table fields conn rights)
        ;; Ensure file operations are complete
        (Thread/sleep 100)
        ;; Reload the routes namespaces to pick up the new routes
        (require '{{name}}.routes.proutes :reload)
        (require '{{name}}.routes.routes :reload)
        ;; Reload core to re-evaluate the app with new routes
        (require '{{name}}.core :reload)
        ;; Force a more aggressive reload by touching and reloading again
        (touch-file "src/{{name}}/core.clj")
        (Thread/sleep 100)
        (require '{{name}}.core :reload)))))


;; Accept :rights [..] as last argument
(defn build-report
  [& args]
  (let [{:keys [conn args set-default?]} (parse-db-and-args args)
        [table & rest-args-seq] args
        rest-args (vec rest-args-seq)
        rights-idx (find-index rest-args ":rights")
        rights (when (some? rights-idx)
                 (normalize-rights (subvec rest-args (inc rights-idx))))]
    (if (nil? table)
      (usage)
      (if (.exists (io/file (str "src/{{name}}/handlers/reports/" table)))
        (println (str "Report for '" table "' already exists. Skipping generation."))
        (do
          (when set-default? (update-config-default! conn))
          (generate-report-files table conn rights)
          ;; Ensure file operations are complete
          (Thread/sleep 100)
          ;; Reload the routes namespaces to pick up the new routes
          (require '{{name}}.routes.proutes :reload)
          (require '{{name}}.routes.routes :reload)
          ;; Reload core to re-evaluate the app with new routes
          (require '{{name}}.core :reload)
          ;; Force a more aggressive reload by touching and reloading again
          (touch-file "src/{{name}}/core.clj")
          (Thread/sleep 100)
          (require '{{name}}.core :reload))))))



;; Accept :rights [..] as last argument
(defn build-subgrid
  [& args]
  (let [{:keys [conn args set-default?]} (parse-db-and-args args)
        [table parent-table parent-key & rest-args-seq] args
        rest-args (vec rest-args-seq)
        rights-idx (find-index rest-args ":rights")
        [field-pairs rights] (if (some? rights-idx)
                               (let [after (subvec rest-args (inc rights-idx))]
                                 [(subvec rest-args 0 rights-idx)
                                  (normalize-rights after)])
                               [rest-args nil])]
    (cond
      (or (nil? table) (nil? parent-table) (nil? parent-key))
      (do
        (println "Usage: lein run -m builder subgrid <table> <parent-table> <parent-key> <Label1>:<field1> <Label2>:<field2> ...")
        (println "Example: lein run -m builder subgrid appointments patients patient_id \"Date:date\" Reason:reason"))
      ;; Check if subgrid already exists
      (.exists (io/file (str "src/{{name}}/handlers/admin/" table parent-table)))
      (println (str "Subgrid for '" table "' (parent: '" parent-table "') already exists. Skipping generation."))
      (empty? field-pairs)
      ;; If no fields given, auto-generate from DB
      (let [all-fields (map name (get-table-columns table :conn conn))
            filtered-fields (remove #(or (= % "id") (= % parent-key)) all-fields)
            view-fields (map #(vector (auto-label %) %) filtered-fields)
            sql-fields filtered-fields]
        (when set-default? (update-config-default! conn))
        (generate-subgrid-files table parent-table parent-key view-fields sql-fields conn rights)
        ;; Ensure file operations are complete
        (Thread/sleep 100)
        ;; Reload the routes namespaces to pick up the new routes
        (require '{{name}}.routes.proutes :reload)
        (require '{{name}}.routes.routes :reload)
        ;; Reload core to re-evaluate the app with new routes
        (require '{{name}}.core :reload)
        ;; Force a more aggressive reload by touching and reloading again
        (touch-file "src/{{name}}/core.clj")
        (Thread/sleep 100)
        (require '{{name}}.core :reload))
      :else
      (let [view-fields (map #(let [[label field] (str/split % #":")]
                                [label field])
                             field-pairs)
            sql-fields (map second view-fields)]
        (when set-default? (update-config-default! conn))
        (generate-subgrid-files table parent-table parent-key view-fields sql-fields conn rights)
        ;; Ensure file operations are complete
        (Thread/sleep 100)
        ;; Reload the routes namespaces to pick up the new routes
        (require '{{name}}.routes.proutes :reload)
        (require '{{name}}.routes.routes :reload)
        ;; Reload core to re-evaluate the app with new routes
        (require '{{name}}.core :reload)
        ;; Force a more aggressive reload by touching and reloading again
        (touch-file "src/{{name}}/core.clj")
        (Thread/sleep 100)
        (require '{{name}}.core :reload)))))

;; --- DOCS / QUICK REFERENCE -------------------------------------------------
(comment
  ;; Multi-DB builder and seeding quick reference
  ;; Connections per resources/private/config.clj:
  ;;   :default (MySQL), :pg (PostgreSQL), :localdb (SQLite)

  ;; Seeding the database
  ;; - MySQL (default)
  ;;   lein database
  ;; - PostgreSQL
  ;;   lein database pg
  ;;   lein database postgres
  ;; - SQLite
  ;;   lein database localdb
  ;;   lein database sqlite
  ;;   lein database sqlite3

  ;; Grid generation (DB token as first argument)
  ;; - MySQL
  ;;   lein grid users Name:name Email:email
  ;;   lein grid mysql users Name:name Email:email
  ;; - PostgreSQL
  ;;   lein grid pg users Name:name Email:email
  ;;   lein grid postgres users Name:name Email:email
  ;; - SQLite
  ;;   lein grid sqlite users Name:name Email:email
  ;;   lein grid sqlite3 users Name:name Email:email
  ;;   lein grid localdb users Name:name Email:email

  ;; Alternative: keep current default but target a specific DB with :conn
  ;;   lein grid users Name:name Email:email :conn pg

  ;; Optionally update the default DB in config (writes resources/private/config.clj)
  ;;   lein grid pg users Name:name :set-default

  ;; Rights examples (default is ["U" "A" "S"]) – pass after :rights
  ;;   lein grid pg users Name:name Email:email :rights [U A]
  ;;   lein grid users Name:name :rights ["U" "S"]

  ;; Dashboard
  ;;   lein dashboard products Name:name Price:price            ; MySQL (default)
  ;;   lein dashboard pg products Name:name Price:price         ; PostgreSQL
  ;;   lein dashboard localdb products Name:name Price:price    ; SQLite

  ;; Report
  ;;   lein report users                                        ; MySQL
  ;;   lein report pg users                                     ; PostgreSQL
  ;;   lein report sqlite users                                 ; SQLite

  ;; Subgrid
  ;;   lein subgrid phones users user_id "Number":number Type:type       ; MySQL
  ;;   lein subgrid pg phones users user_id "Number":number Type:type    ; PostgreSQL
  ;;   lein subgrid sqlite phones users user_id "Number":number Type:type ; SQLite
  )

