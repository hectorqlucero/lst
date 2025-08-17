(ns {{name}}.models.db
  (:require [clojure.string :as st]
            [{{name}}.models.db.mysql :as mysql]
            [{{name}}.models.db.postgres :as pg]
            [{{name}}.models.db.sqlite :as sqlite]))

;; ---------- Vendor detection helpers ----------
(defn subprotocol [db-spec]
  (:subprotocol db-spec))

(defn mysql? [db-spec]
  (let [sp (subprotocol db-spec)]
    (contains? #{"mysql" :mysql} sp)))

(defn postgres? [db-spec]
  (let [sp (subprotocol db-spec)]
    (contains? #{"postgresql" :postgresql "postgres" :postgres} sp)))

(defn sqlite? [db-spec]
  (let [sp (subprotocol db-spec)]
    (contains? #{"sqlite" :sqlite "sqlite3" :sqlite3} sp)))

;; ---------- Small utilities ----------
(defn- name-str [x]
  (cond
    (keyword? x) (name x)
    (symbol? x)  (name x)
    :else        (str x)))

(defn- safe-call [f & args]
  (try
    (apply f args)
    (catch Exception _ nil)))

;; ---------- Quoting / query options per vendor ----------
(defn q-opts [db-spec]
  (cond
    (mysql? db-spec)    (mysql/q-opts)
    :else {}))

(defn table-sql-name [db-spec t]
  (let [tname (name-str t)]
    (cond
      (mysql? db-spec)    (mysql/table-sql-name tname)
      :else tname)))

;; ---------- Field projections (used for form SELECTs) ----------
(defn- time-type? [type-str]
  (let [t (some-> type-str st/lower-case st/trim)]
    (and t (re-find #"(^|\s)time(\s|$)" t) (not (st/includes? t "timestamp")))))

(defn time-projection [db-spec field type-str]
  (if (time-type? type-str)
    (cond
      (mysql? db-spec)    (mysql/time-projection field)
      (postgres? db-spec) (pg/time-projection field)
      (sqlite? db-spec)   (sqlite/time-projection field)
      :else field)
    field))

;; ---------- Schema discovery ----------
(defn describe-table [db-spec table query-fn]
  (cond
    (mysql? db-spec)    (mysql/describe-table table query-fn)
    (postgres? db-spec) (pg/describe-table table query-fn)
    (sqlite? db-spec)   (sqlite/describe-table table query-fn)
    :else (throw (ex-info (str "Unsupported dbtype for describe: " (subprotocol db-spec))
                          {:dbtype (subprotocol db-spec)}))))

;; ---------- Primary key discovery ----------
(defn- pks-from-describe [described-cols]
  (->> described-cols
       (filter #(or (= (:key %) "PRI") (= (:key %) "PRIMARY")))
       (map :field)
       vec))

(defn primary-keys [db-spec table described-cols query-fn]
  (let [from-describe (pks-from-describe described-cols)]
    (if (seq from-describe)
      from-describe
      (cond
        (postgres? db-spec) (pg/primary-keys-fallback table query-fn)
        :else []))))

;; ---------- Last insert id helpers ----------
(defn last-insert-id [t-con db-spec]
  (cond
    (sqlite? db-spec) (sqlite/last-insert-id t-con)
    (mysql? db-spec)  (mysql/last-insert-id t-con)
    :else nil))

(defn cascade-delete-child-images!
  "Best-effort removal of child table images referencing a deleted parent row.
  - query-fn: executes SQL (string or [sql & params]) against db-spec.
  - delete-fn: function of one arg (image filename) that performs deletion.
  - row: the parent row map about to be deleted."
  [db-spec table row query-fn delete-fn]
  (safe-call
   (fn []
     (cond
       (sqlite? db-spec)   (sqlite/cascade-delete-child-images! table row query-fn delete-fn)
       (postgres? db-spec) (pg/cascade-delete-child-images! table row query-fn delete-fn)
       (mysql? db-spec)    (mysql/cascade-delete-child-images! table row query-fn delete-fn)
       :else nil))))
