# lst

A professional Leiningen template for Lucero Systems–style Clojure web applications.

---

## What is lst?

lst is a Leiningen template that scaffolds full-stack, database-backed Clojure web apps fast. It generates CRUD grids, dashboards, reports, and subgrids with a consistent handler/model/view structure, Bootstrap 5 + DataTables UI, and ready‑to‑use routes and auth checks.

Use it to:
- Create CRUD interfaces in seconds
- Wire dashboards and reports
- Add master–detail subgrids (modal-based)
- Target MySQL, PostgreSQL, or SQLite

---

## Requirements

- Clojure 1.10+
- Java 17+
- Leiningen 2.9.0+

---

## Install the template

```sh
git clone <your-repo-url>
cd lst
lein clean && lein deps && lein install
```

Then generate a new app:

```sh
lein new lst myapp
cd myapp
```

---

## Configure your app

1) Edit project.clj and replace placeholders (name, org, description).
2) Add database connections in resources/private/config.clj:

```clojure
{:db      {:classname "com.mysql.cj.jdbc.Driver" :subprotocol "mysql"      :subname "//localhost:3306/mydb" :user "myuser" :password "mypassword"}
 :pg      {:classname "org.postgresql.Driver"    :subprotocol "postgresql" :subname "//localhost:5432/mydb" :user "myuser" :password "mypassword"}
 :localdb {:classname "org.sqlite.JDBC"          :subprotocol "sqlite"     :subname "db/mydb.sqlite"}}
```

Create a database (example: MySQL):
```sql
CREATE DATABASE mydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Run the app:
```sh
lein with-profile dev run
```

Run migrations and seed users:
```sh
# default connection (:db)
lein migrate
lein database

# PostgreSQL
lein migrate pg
lein database pg

# SQLite
lein migrate localdb
lein database localdb
```

Open http://localhost:3000

Default users:
- user@example.com / user
- admin@example.com / admin
- system@example.com / system

---

## Core concepts (plain English)

- Grid: CRUD table with “New/Edit/Delete” actions.
- Dashboard: Read‑only table (no CRUD buttons).
- Report: Read‑only page under /reports; you supply the query/logic.
- Subgrid: Child grid tied to a parent record, shown in a modal.
- Connection key: Which DB config to use (db, pg, localdb).
- Rights: Who can access (U = user, A = admin, S = system). You can restrict any generated handler.

Namespace layout in a generated app (example: myapp):
- Handlers (controllers + HTTP endpoints): src/myapp/handlers/...
- Models (DB access): src/myapp/handlers/.../model.clj
- Views (Hiccup/HTML): src/myapp/handlers/.../view.clj
- Private routes: src/myapp/routes/proutes.clj
- Public routes: src/myapp/routes/routes.clj
- Navbar: src/myapp/menu.clj
- Layout: src/myapp/layout.clj

---

## One‑minute recipes

Create a CRUD grid for users:
```sh
lein grid users "Name:firstname" "Email:email"
```
- Visit: /admin/users
- Files: src/myapp/handlers/admin/users/{controller,model,view}.clj

Create a read‑only dashboard:
```sh
lein dashboard users "Name:firstname" "Email:email"
```
- Visit: /users

Create a report:
```sh
lein report monthlySummary
```
- Visit: /reports/monthlySummary

Create a subgrid (user_contacts under users via user_id):
```sh
lein subgrid user_contacts users user_id "Contact Name:contact_name" "Email:email"
```
- Parent visit: /admin/users
- Child visit: /admin/usercontactsusers (opened via modal button in parent)

Target PostgreSQL once and remember it:
```sh
lein grid pg users "Name:firstname" "Email:email" :set-default
```

Restrict a page to admins and system only:
```sh
lein dashboard kpis :rights [A S]
```

---

## Generator reference (concise)

General notes
- Label:field pairs control column headers and which DB fields show. Example: "Email:email".
- Omit fields to auto‑detect (common for subgrids).
- You can pass the connection key up front (pg/localdb) or via :conn <key>.
- :set-default persists the chosen connection for future runs.
- Rights are optional; default is open to all logged-in users.

Grid (CRUD)
```sh
lein grid [conn] <table> <Label:field> ... [:rights U A S] [:set-default]
# or
lein grid <table> <Label:field> ... :conn <conn> [:rights ...] [:set-default]
```
Creates:
- src/myapp/handlers/admin/<table>/{controller,model,view}.clj
- Private routes in src/myapp/routes/proutes.clj
- UI uses myapp.models.grid/build-grid

Dashboard (read‑only)
```sh
lein dashboard [conn] <table> <Label:field> ... [:rights U A S] [:set-default]
```
Creates:
- src/myapp/handlers/<table>/{controller,model,view}.clj
- Private routes; UI uses myapp.models.grid/build-dashboard

Report (read‑only under /reports)
```sh
lein report [conn] <name> [:rights U A S] [:set-default]
```
Creates:
- src/myapp/handlers/reports/<name>/{controller,model,view}.clj
- Private routes; UI uses myapp.models.grid/build-dashboard
- You implement the query in model.clj

Subgrid (child of a parent table)
```sh
lein subgrid [conn] <child-table> <parent-table> <foreign-key> [Label:field ...] [:rights U A S] [:set-default]
```
Creates:
- src/myapp/handlers/admin/<child><parent>/{controller,model,view}.clj
- Routes to support modal endpoints
- Parent view can include the subgrid via helper config

DB requirements for subgrids
- Primary keys on parent and child (e.g., id)
- Child has FK to parent (e.g., user_id)

---

## Wiring a subgrid into a parent view

Example (parent users view):

```clojure
(ns myapp.handlers.admin.users.view
  (:require [myapp.models.grid :refer [build-grid-with-subgrids create-subgrid-config]]))

(defn users-view [title rows]
  (let [labels ["Name" "Email"]
        db-fields [:firstname :email]
        fields (apply array-map (interleave db-fields labels))
        table-id "users_table"
        href "/admin/users"
        args {:new true :edit true :delete true
              :subgrids [(create-subgrid-config
                           {:title "Contacts"
                            :table-name "usercontacts"
                            :foreign-key "user_id"
                            :href "/admin/usercontactsusers"
                            :icon "bi bi-people"
                            :label "Contacts"})]}]
    (build-grid-with-subgrids title rows table-id fields href args)))
```

Key idea: the subgrid href is /admin/<child><parent>, and the foreign key is passed automatically to the modal.

---

## Migrations

Location (template):
- resources/leiningen/new/lst/resources/migrations/

Commands (run inside your generated app):
```sh
lein migrate [conn]
lein rollback [conn]
lein database [conn]
```

Example helpers:
- 002-users_view.mysql.up.sql
- 002-users_view.postgresql.up.sql
- 002-users_view.sqlite.up.sql

---

## UI behavior

- Bootstrap 5 + DataTables for grids
- Modals for New/Edit with client‑side validation
- Subgrids open in a modal; saving refreshes content without full reload

Assets (template):
- JS: resources/leiningen/new/lst/resources/public/vendor/app.js
- CSS: resources/leiningen/new/lst/resources/public/vendor/app.css
- Layout: resources/leiningen/new/lst/layout.clj

---

## Access control (rights)

You can restrict any generated page:

```sh
lein grid customers :rights [A S]
lein dashboard kpis :rights [S]
lein report monthlySummary :rights [U]
lein subgrid orderitems orders order_id :rights [A S]
```

Generated controllers check the current user’s level against the allowed list.

---

## Troubleshooting

- Routes didn’t update after generation?
  - Touch src/myapp/core.clj, then restart the dev server.
- Wrong DB?
  - Pass :conn <key> or use :set-default to change the default.
- Subgrid not opening?
  - Confirm the :href (e.g., /admin/usercontactsusers) and that the FK exists in the child table.
- Namespace errors after renaming?
  - Prefer child/parent handler dirs without underscores (e.g., usercontacts). SQL table names can keep underscores.

---

## Commands quick reference

- Grid
  - lein grid <table> <Label:field>... [:rights ...]
  - lein grid pg <table> ... [:set-default]
  - lein grid <table> ... :conn pg
- Dashboard
  - lein dashboard <table> <Label:field>... [:rights ...]
- Report
  - lein report <name> [:rights ...]
- Subgrid
  - lein subgrid <child> <parent> <fk> [Label:field]... [:rights ...]

---

## Learn more

- Leiningen: https://leiningen.org/
- Template sources: resources/leiningen/new/lst/
- Example view helpers in template: resources/leiningen/new/lst/admin-users-view.clj
