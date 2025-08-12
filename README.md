# lst

A Professional Leiningen Template for Lucero Systems Web Applications

---

## Overview

lst is a modern [Leiningen](https://leiningen.org/) project template for rapidly building robust,
scalable, and maintainable Clojure web applications. It scaffolds CRUD grids, dashboards,
reports, and subgrids in seconds, following Lucero Systems conventions. With a clear
handler/view/model structure, Hiccup-based HTML, and seamless DB integration, lst helps you
launch production-ready Clojure web apps fast.

---

## Features

- Rapid scaffolding: grids, dashboards, reports, subgrids
- Automatic DB integration and schema-driven fields
- Multiple database support (MySQL, PostgreSQL, SQLite)
- Clear handler/view/model separation
- Hiccup-based HTML UI
- Bootstrap 5 UI, DataTables integrations, modal forms
- VS Code + Calva friendly
- Highly extensible; open source (MIT/EPL)

---

## Requirements

- Clojure: 1.10+
- Java: 17+
- Leiningen: 2.9.0+

---

## Install the Template Locally

1) Clone and install:
```sh
git clone <your-repo-url>
cd lst
lein clean && lein deps && lein install
```

You can now use lst as a template for new projects.

---

## Quick Start

1) Create a new project
```sh
lein new lst myapp
cd myapp
```

2) Configure the project
- Edit project.clj and replace “Change me/xxxxx” placeholders.
- Configure DB connections in resources/private/config.clj.

Example multi-DB config (resources/private/config.clj):
```clojure
;; MySQL (default)
{:db {:classname   "com.mysql.cj.jdbc.Driver"
      :subprotocol "mysql"
      :subname     "//localhost:3306/mydb"
      :user        "myuser"
      :password    "mypassword"}
 ;; PostgreSQL
 :pg {:classname   "org.postgresql.Driver"
      :subprotocol "postgresql"
      :subname     "//localhost:5432/mydb"
      :user        "myuser"
      :password    "mypassword"}
 ;; SQLite (local development)
 :localdb {:classname   "org.sqlite.JDBC"
           :subprotocol "sqlite"
           :subname     "db/mydb.sqlite"}}
```

3) Create your database (example: MySQL)
```sql
CREATE DATABASE mydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

4) Start the dev server
```sh
lein with-profile dev run
```

5) Run migrations and seed users (multi-DB)
```sh
# MySQL (default)
lein migrate
lein database

# PostgreSQL
lein migrate pg
lein database pg

# SQLite
lein migrate localdb
lein database localdb
```

Default users:
- user@example.com / user
- admin@example.com / admin
- system@example.com / system

6) Open the app
http://localhost:3000

---

## Project Structure

| Feature         | Location                                              | Description                    |
|-----------------|-------------------------------------------------------|--------------------------------|
| Grids           | [src/myapp/handlers/admin/](src/myapp/handlers/admin/)          | CRUD grids                     |
| Subgrids        | [src/myapp/handlers/admin/](src/myapp/handlers/admin/)          | Child tables linked to parent  |
| Dashboards      | [src/myapp/handlers/](src/myapp/handlers/)                     | Dashboards                     |
| Reports         | [src/myapp/handlers/reports/](src/myapp/handlers/reports/)     | Reports                        |
| Private routes  | [src/myapp/routes/proutes.clj](src/myapp/routes/proutes.clj)    | Authenticated routes           |
| Public routes   | [src/myapp/routes/routes.clj](src/myapp/routes/routes.clj)      | Public routes                  |
| Menu (Navbar)   | [src/myapp/menu.clj](src/myapp/menu.clj)                        | Bootstrap 5 navbar             |

Each grid, subgrid, dashboard, and report contains:
- controller.clj
- model.clj
- view.clj

---

## Generators: Grids, Subgrids, Dashboards, Reports

Generators are implemented in:
- Template builder: [resources/leiningen/new/lst/builder.clj](resources/leiningen/new/lst/builder.clj)
  - Entrypoints: [`leiningen.new.lst.builder/build-grid`](resources/leiningen/new/lst/builder.clj), [`leiningen.new.lst.builder/build-dashboard`](resources/leiningen/new/lst/builder.clj), [`leiningen.new.lst.builder/build-report`](resources/leiningen/new/lst/builder.clj), [`leiningen.new.lst.builder/build-subgrid`](resources/leiningen/new/lst/builder.clj)
  - Route wiring helpers: [resources/leiningen/new/lst/models-routes.clj](resources/leiningen/new/lst/models-routes.clj)

UI helpers:
- Grid/Dashboard builders and subgrid UI: [resources/leiningen/new/lst/grid.clj](resources/leiningen/new/lst/grid.clj)
  - e.g., [`{{name}}.models.grid/build-grid`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/build-dashboard`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/build-grid-with-subgrids`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/create-subgrid-config`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/build-subgrid-trigger`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/build-subgrid-modal`](resources/leiningen/new/lst/grid.clj)
- Form builders used by views: see generated view samples and [`{{name}}.handlers.admin.users.view`](resources/leiningen/new/lst/admin-users-view.clj)

Frontend assets:
- JS (DataTables, modals, subgrid logic): [resources/leiningen/new/lst/resources/public/vendor/app.js](resources/leiningen/new/lst/resources/public/vendor/app.js)
- CSS (DataTables and UI): [resources/leiningen/new/lst/resources/public/vendor/app.css](resources/leiningen/new/lst/resources/public/vendor/app.css)

Routes are auto-updated by:
- [`leiningen.new.lst.models-routes/process-grid`](resources/leiningen/new/lst/models-routes.clj)
- [`leiningen.new.lst.models-routes/process-dashboard`](resources/leiningen/new/lst/models-routes.clj)
- [`leiningen.new.lst.models-routes/process-report`](resources/leiningen/new/lst/models-routes.clj)
- [`leiningen.new.lst.models-routes/process-subgrid`](resources/leiningen/new/lst/models-routes.clj)

---

### Common Generator Flags

- Target DB:
  - Leading arg form: `lein grid pg users Name:name`
  - Alternative flag form: `lein grid users Name:name :conn pg`
- Set the chosen DB as default for future runs: add `:set-default`
- Restrict access: `:rights [U A S]` (bare tokens or quoted strings)

Examples (from builder usage):
```sh
lein grid users Name:name Email:email
lein grid pg users Name:name Email:email :set-default
lein grid users Name:name :conn pg
lein dashboard users "Name:name" "Email:email"
lein report users
lein subgrid user_contacts users user_id "Contact Name:contact_name" Email:email
```

---

### Grid Generator

Scaffolds a full CRUD grid for a table.

Syntax
```sh
lein grid [conn] <table> <Label1:field1> <Label2:field2> ... [:rights U A S] [:set-default]
```
or with explicit connection flag:
```sh
lein grid <table> <Label1:field1> ... :conn <conn> [:rights ...] [:set-default]
```

What it generates
- Handlers in src/myapp/handlers/admin/<table>/{controller,model,view}.clj
- Adds routes to src/myapp/routes/proutes.clj
- A Bootstrap 5/DataTables grid using [`{{name}}.models.grid/build-grid`](resources/leiningen/new/lst/grid.clj)

Customize the view
- Use [`{{name}}.models.grid/build-grid`](resources/leiningen/new/lst/grid.clj) with fields = (apply array-map (interleave db-fields labels))
- See a full example in [`{{name}}.handlers.admin.users.view`](resources/leiningen/new/lst/admin-users-view.clj)

---

### Dashboard Generator

A read-only grid (“dashboard”) without CRUD action buttons.

Syntax
```sh
lein dashboard [conn] <table> <Label1:field1> <Label2:field2> ... [:rights U A S] [:set-default]
```

What it generates
- Handlers in src/myapp/handlers/<table>/{controller,model,view}.clj
- Routes added to src/myapp/routes/proutes.clj
- UI rendered with [`{{name}}.models.grid/build-dashboard`](resources/leiningen/new/lst/grid.clj)

---

### Report Generator

A report is rendered similarly to a dashboard under /reports/<name>.

Syntax
```sh
lein report [conn] <report> [:rights U A S] [:set-default]
```

What it generates
- Handlers in src/myapp/handlers/reports/<report>/{controller,model,view}.clj
- Routes added to src/myapp/routes/proutes.clj
- UI via [`{{name}}.models.grid/build-dashboard`](resources/leiningen/new/lst/grid.clj)

See templates:
- Controller/View/Model templates for reports in [resources/leiningen/new/lst/builder.clj](resources/leiningen/new/lst/builder.clj)

---

### Subgrid Generator

Creates a child grid linked to a parent (master-detail), rendered in a modal over the parent grid.

Syntax
```sh
lein subgrid [conn] <child-table> <parent-table> <foreign-key> [Label:field ...] [:rights U A S] [:set-default]
```

Parameters
- child-table: subgrid table
- parent-table: parent table
- foreign-key: FK in child referencing the parent (e.g., user_id)
- fields: optional `"Label:field"` pairs. If omitted, fields are auto-detected (excluding id and the FK)

What it generates
- Handlers in src/myapp/handlers/admin/<child><parent>/{controller,model,view}.clj
  - Generated name concatenates child+parent (e.g., usercontacts + users => usercontactsusers)
- Parent-aware routes in src/myapp/routes/proutes.clj
- Subgrid endpoints used by parent buttons (see `:href` below)
- View leverages:
  - [`{{name}}.models.grid/build-grid-with-custom-new`](resources/leiningen/new/lst/grid.clj) to ensure “New” passes parent_id
  - [`{{name}}.models.grid/build-subgrid-modal`](resources/leiningen/new/lst/grid.clj) and related JS

Wiring the subgrid into the parent grid
- In your parent view, pass a :subgrids vector to [`{{name}}.models.grid/build-grid-with-subgrids`](resources/leiningen/new/lst/grid.clj)
- Use [`{{name}}.models.grid/create-subgrid-config`](resources/leiningen/new/lst/grid.clj) for convenience
- The subgrid :href follows: `/admin/<child><parent>`; e.g., `/admin/usercontactsusers`

Example parent view wiring
```clojure
(ns myapp.handlers.admin.users.view
  (:require
    [myapp.models.grid :refer [build-grid-with-subgrids create-subgrid-config]]))

(defn users-view
  [title rows]
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

Database requirements
1) Primary keys on both parent and child (usually id)
2) Foreign key on child referencing parent (e.g., user_id)
3) Recommended naming: <parent>_id

---

## Access Control with :rights

All generators accept an optional :rights vector. The generated controller enforces it via allowed-rights and [`{{name}}.models.util/user-level`](resources/leiningen/new/lst/builder.clj) in the template.

Examples
```sh
lein grid customers :rights [A S]
lein dashboard kpis :rights [S]
lein report monthlySummary :rights [U]
lein subgrid orderitems orders order_id :rights [A S]
```

Generated controllers follow:
```clojure
(def allowed-rights ["A" "S"]) ;; from your :rights
;; Request allowed if (user-level request) ∈ allowed-rights
```

---

## Commands Reference

- Grid
  - `lein grid <table> <Label:field>... [:rights ...]`
  - `lein grid pg <table> ... [:set-default]`
  - `lein grid <table> ... :conn pg`
- Dashboard
  - `lein dashboard <table> <Label:field>... [:rights ...]`
- Report
  - `lein report <name> [:rights ...]`
- Subgrid
  - `lein subgrid <child> <parent> <fk> [Label:field]... [:rights ...]`

See builder usage strings in [resources/leiningen/new/lst/builder.clj](resources/leiningen/new/lst/builder.clj).

---

## UI and Frontend Behavior

- Grids use Bootstrap 5 + DataTables
- Modal forms for “New” and “Edit” with validation
- Subgrids open as a modal above the parent grid
- After saving in a subgrid, content refreshes without page reload; “New” can navigate to the last page automatically

Key files:
- JS: [resources/leiningen/new/lst/resources/public/vendor/app.js](resources/leiningen/new/lst/resources/public/vendor/app.js)
  - Handles DataTables init, modal open/close, AJAX form submit/delete, subgrid modal lifecycle
- CSS: [resources/leiningen/new/lst/resources/public/vendor/app.css](resources/leiningen/new/lst/resources/public/vendor/app.css)
  - Responsive tables, button styling, navbar highlights
- Layout/Navbar: [resources/leiningen/new/lst/layout.clj](resources/leiningen/new/lst/layout.clj)

---

## Migrations

Starter migrations and examples live in:
- [resources/leiningen/new/lst/resources/migrations/](resources/leiningen/new/lst/resources/migrations/)

Helpers:
- MySQL users view: [002-users_view.mysql.up.sql](resources/leiningen/new/lst/resources/migrations/002-users_view.mysql.up.sql)
- PostgreSQL users view: [002-users_view.postgresql.up.sql](resources/leiningen/new/lst/resources/migrations/002-users_view.postgresql.up.sql)
- SQLite users view: [002-users_view.sqlite.up.sql](resources/leiningen/new/lst/resources/migrations/002-users_view.sqlite.up.sql)

Run:
```sh
lein migrate [conn]
lein rollback [conn]
lein database [conn]
```

---

## API Highlights (Generated App)

- Grid/Dashboard builders: [`{{name}}.models.grid/build-grid`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/build-dashboard`](resources/leiningen/new/lst/grid.clj)
- Subgrid helpers: [`{{name}}.models.grid/build-grid-with-subgrids`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/create-subgrid-config`](resources/leiningen/new/lst/grid.clj), [`{{name}}.models.grid/build-subgrid-trigger`](resources/leiningen/new/lst/grid.clj)
- Modal utilities: [`{{name}}.models.grid/build-subgrid-modal`](resources/leiningen/new/lst/grid.clj)
- Form helpers (examples in users view): [resources/leiningen/new/lst/admin-users-view.clj](resources/leiningen/new/lst/admin-users-view.clj)
- Route wiring (auto): [`leiningen.new.lst.models-routes`](resources/leiningen/new/lst/models-routes.clj)

---

## Tips & Best Practices

- Multi-DB: define multiple connections and pass the key (pg, localdb) to any generator or migration
- Use :set-default to switch the default connection used by generators
- Subgrid naming: prefer names without underscores for handler/view/model directories (e.g., usercontacts), to avoid namespace issues; SQL can keep underscores
- Troubleshooting: if routes don’t update, touch src/myapp/core.clj and reload
- Menu customization: edit src/myapp/menu.clj; layout tweaks in src/myapp/layout.clj

---

## Further Reading

- Inline comments in generated files
- Leiningen docs: https://leiningen.org/
- Template code (builder, views, grids): [resources/leiningen/new/lst/](resources/leiningen/new/lst/)
