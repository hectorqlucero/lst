(ns {{name}}.core
  "Main Ring application entry point and HTTP middleware.

  Provides:
  - Authentication gate (wrap-login)
  - Centralized exception handling with JSON for AJAX (wrap-exception-handling)
  - Routing setup (public + private routes)
  - App builder and -main launcher"
  (:require
   [compojure.core :refer [defroutes routes]]
   [compojure.route :as route]
   [{{name}}.models.crud :refer [config KEY]]
   [{{name}}.routes.proutes :refer [proutes]]
   [{{name}}.routes.routes :refer [open-routes]]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.util.response :refer [redirect]])
  (:gen-class))

;; Middleware for handling login
(defn wrap-login
  "Protects private routes by requiring a session :user_id.
  If missing, redirects to the login page; otherwise forwards the request."
  [handler]
  (fn [request]
    (if (nil? (get-in request [:session :user_id]))
      (redirect "home/login")
      (handler request))))

;; Middleware for handling exceptions
(defn wrap-exception-handling
  "Catches all exceptions and returns friendly responses.

  Behavior:
  - For AJAX (X-Requested-With: XMLHttpRequest): returns application/json with concise error details.
  - For non-AJAX: returns a plain text body with an appropriate HTTP status.
  - Logs the root cause and stack trace to the console.

  Classifies common SQL errors across MySQL/PostgreSQL/SQLite to improve messages (unique/FK/not-null/too-long)."
  [handler]
  (letfn [(ajax? [req]
            (= "XMLHttpRequest" (get-in req [:headers "x-requested-with"])))
          (root-cause [^Throwable t]
            (loop [c t]
              (if-let [cause (.getCause c)]
                (recur cause)
                c)))
          ;; Attempt to extract duplicate key details (field/value/constraint)
          (dup-details [^String msg]
            (let [m (or msg "")]
              (or
               ;; MySQL: Extract value and key in two steps for robustness
               (when-let [[_ v] (re-find #"Duplicate entry '([^']+)'" m)]
                 (when-let [[_ k] (re-find #"for key '([^']+)'" m)]
                   (let [field (-> k (str/replace #".*\." "") (str/replace #"_UNIQUE$" ""))]
                     {:field field :value v :constraint k})))
               ;; Postgres detail (often present): Key (column)=(value) already exists.
               (when-let [[_ f v] (re-find #"Key \(([^\)]+)\)=\(([^\)]+)\) already exists" m)]
                 {:field f :value v})
               ;; SQLite: UNIQUE constraint failed: table.column
               (when-let [[_ qualified] (re-find #"UNIQUE constraint failed: ([^\s]+)" m)]
                 (let [field (last (str/split qualified #"\\."))]
                   {:field field})))))
          (sql-state [^Throwable t]
            (when (instance? java.sql.SQLException t)
              (.getSQLState ^java.sql.SQLException t)))
          (sql-code [^Throwable t]
            (when (instance? java.sql.SQLException t)
              (.getErrorCode ^java.sql.SQLException t)))
          (msg-has? [^String msg re]
            (boolean (re-find re (or msg ""))))
          ;; Best-effort cross-DB classification
          (classify-sql [^Throwable rc ^String msg]
            (let [state (sql-state rc)
                  code  (sql-code rc)]
              (cond
                ;; Unique violations
                (= state "23505") :unique          ; Postgres
                (= code 1062)      :unique          ; MySQL duplicate entry
                (msg-has? msg #"(?i)UNIQUE constraint failed") :unique ; SQLite

                ;; Foreign key
                (= state "23503") :fk             ; Postgres FK
                (or (= code 1451) (= code 1452)) :fk ; MySQL FK
                (msg-has? msg #"(?i)foreign key constraint failed") :fk ; SQLite

                ;; Not null
                (= state "23502") :not-null       ; Postgres NOT NULL
                (= code 1048)      :not-null       ; MySQL column cannot be null
                (msg-has? msg #"(?i)NOT NULL constraint failed") :not-null

                ;; Check constraint
                (= state "23514") :check          ; Postgres CHECK

                ;; Data too long / truncation
                (= state "22001") :too-long       ; Postgres string data right truncation
                (= code 1406)      :too-long       ; MySQL Data too long
                (msg-has? msg #"(?i)string or blob too big|too long") :too-long

                :else :other-sql)))]
    (fn [request]
      (try
        (handler request)
        (catch Exception e
          (let [rc (root-cause e)
                exd (ex-data e)
                csrf? (true? (:invalid-anti-forgery-token exd))
                sql? (instance? java.sql.SQLException rc)
                msg (.getMessage rc)
                kind (when sql? (classify-sql rc msg))
                dd (when (= kind :unique) (dup-details msg))
                ;; Decide status and friendly message
                [status plain] (cond
                                 csrf? [403 "Invalid or missing CSRF token"]
                                 (= kind :unique) [409 (if-let [f (:field dd)] (str "Duplicate value for " f) "Duplicate value")]
                                 (= kind :fk)     [409 "Foreign key constraint violation"]
                                 (= kind :not-null) [422 "Required field is missing"]
                                 (= kind :check)  [422 "Value violates a check constraint"]
                                 (= kind :too-long) [422 "Value too long"]
                                 sql? [400 "Invalid data"]
                                 :else [400 "Invalid data"])
                body-json (let [base {:ok false :error plain}
                                base (if dd (merge base dd) base)]
                            (json/write-str base))]
            (try
              (println "[ERROR]" (.getName (class rc)) "-" msg
                       "->" (name (:request-method request)) (:uri request))
              (.printStackTrace rc)
              (catch Throwable _))
            (if (ajax? request)
              {:status status :headers {"Content-Type" "application/json"} :body body-json}
              {:status status :body plain})))))))

;; Middleware to wrap public and private routes
(defn wrap-routes
  "Tiny helper to wrap route groups consistently."
  [route-fn]
  (fn [routes]
    (route-fn routes)))

;; Define the application routes
;; NOTE: Route order matters; more specific routes should come before generic ones.
(defroutes app-routes
  (route/resources "/")
  (route/files (:path config) {:root (:uploads config)})
  (wrap-routes open-routes)
  (wrap-login (wrap-routes proutes))
  (route/not-found "Not Found"))

;; Ensure the uploads directory (and parents) exist, based on config
(defn ensure-upload-dirs! []
  (try
    (when-let [p (:uploads config)]
      (let [f (io/file (str p))]
        (when-not (.exists f)
          (.mkdirs f))))
    (catch Throwable _)))

;; Application configuration
;; The order of middleware matters: defaults/multipart first, exception handling outermost.
(def app
  "Ring handler composed of routes + middleware (multipart, defaults with anti-forgery, sessions, exception handling)."
  (-> (routes #'app-routes)
      (wrap-multipart-params)
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] true)
                         (assoc-in [:session :store] (cookie-store {:key KEY}))
                         (assoc-in [:session :cookie-attrs] {:max-age 28800})
                         (assoc-in [:session :cookie-name] "LS")))
      (wrap-exception-handling)))

;; Main entry point
(defn -main
  "Starts the Jetty HTTP server using the configured port."
  []
  (ensure-upload-dirs!)
  (jetty/run-jetty app {:port (:port config)}))

(comment
  (:port config))
