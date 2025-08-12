(ns {{name}}.handlers.admin.users.model
  (:require
   [{{name}}.models.crud :refer [Query crud-fix-id]]))

(def get-users-sql
  (str
   "\n    SELECT id, lastname, firstname, username,\n"
   "    dob_formatted,\n"
   "    cell,\n"
   "    level_formatted,\n"
   "    active_formatted\n"
   "    FROM users_view\n"
   "    ORDER BY lastname, firstname\n"))

(defn get-users
  []
  ;; Use PostgreSQL connection
  (Query get-users-sql :conn :mysql))

;; Start get-user
(def get-user-sql
  (str
   "\n"
   "    SELECT *\n"
   "    FROM users_view\n"
   "    WHERE id = ?\n"
   "    ORDER BY lastname,firstname\n"
   "    "))

(defn get-user
  [id]
  ;; Use PostgreSQL connection
  (first (Query [get-user-sql (crud-fix-id id)] :conn :mysql)))
;; End get-user

(comment
  ;; Mysql
  (Query "select * from users_view" :conn :mysql)
  ;; SQLite (local)
  (Query "select * from users_view" :conn :localdb)
  (get-user 1)
  (get-users)
  ;; PostgreSQL
  (Query "select * from users_view" :conn :pg)
  (first (Query [get-user-sql 1] :conn :pg)))
