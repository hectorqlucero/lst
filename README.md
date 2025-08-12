# LST - Lucero Systems Template

> A professional Leiningen template for rapid Clojure web application development

[![Clojure](https://img.shields.io/badge/Clojure-1.10+-blue.svg)](https://clojure.org/)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Leiningen](https://img.shields.io/badge/Leiningen-2.9.0+-green.svg)](https://leiningen.org/)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Core Concepts](#core-concepts)
- [Generator Commands](#generator-commands)
- [Database Support](#database-support)
- [Access Control](#access-control)
- [Troubleshooting](#troubleshooting)
- [API Reference](#api-reference)

---

## 🎯 Overview

**LST** is a powerful Leiningen template that scaffolds full-stack, database-backed Clojure web applications with enterprise-grade features. Generate complete CRUD interfaces, dashboards, and reports in seconds with a consistent MVC architecture.

### What You Get

- **CRUD Grids**: Full Create, Read, Update, Delete interfaces
- **Dashboards**: Read-only data tables with advanced filtering
- **Reports**: Custom report pages with flexible querying
- **Subgrids**: Master-detail relationships with modal interfaces
- **Multi-Database**: MySQL, PostgreSQL, and SQLite support
- **Modern UI**: Bootstrap 5 + DataTables integration
- **Security**: Built-in authentication and authorization

---

## ✨ Features

| Feature | Description | Benefit |
|---------|-------------|---------|
| **Rapid Scaffolding** | Generate complete CRUD in seconds | 🚀 10x faster development |
| **Database Agnostic** | MySQL, PostgreSQL, SQLite support | 🔄 Easy deployment flexibility |
| **Modern UI** | Bootstrap 5 + DataTables | 💎 Professional appearance |
| **Security Built-in** | Role-based access control | 🔒 Enterprise-ready security |
| **Modal Subgrids** | Master-detail relationships | 📊 Rich data relationships |
| **Migration System** | Database versioning | 🔧 Easy schema management |

---

## 🚀 Quick Start

### Prerequisites

| Requirement | Version | Download |
|-------------|---------|----------|
| **Java** | 17+ | [OpenJDK](https://openjdk.org/) |
| **Clojure** | 1.10+ | [Clojure.org](https://clojure.org/) |
| **Leiningen** | 2.9.0+ | [Leiningen.org](https://leiningen.org/) |

### Installation

```bash
# 1. Clone and install the template
git clone <your-repo-url>
cd lst
lein clean && lein deps && lein install
```

```bash
# 2. Create your new application
lein new lst myapp
cd myapp
```

### First Run

```bash
# 3. Configure database (see Configuration section)
# 4. Run migrations and seed data
lein migrate
lein database

# 5. Start the development server
lein with-profile dev run
```

🎉 **Success!** Open http://localhost:3000

---

## ⚙️ Configuration

### Database Configuration

Edit `resources/private/config.clj` with your database credentials:

```clojure
{:db      {:classname "com.mysql.cj.jdbc.Driver" 
           :subprotocol "mysql"      
           :subname "//localhost:3306/mydb" 
           :user "myuser" 
           :password "mypassword"}
           
 :pg      {:classname "org.postgresql.Driver"    
           :subprotocol "postgresql" 
           :subname "//localhost:5432/mydb" 
           :user "myuser" 
           :password "mypassword"}
           
 :localdb {:classname "org.sqlite.JDBC"          
           :subprotocol "sqlite"     
           :subname "db/mydb.sqlite"}}
```

### Database Setup Commands

| Database | Setup Command | Migration Command |
|----------|---------------|-------------------|
| **MySQL** | `CREATE DATABASE mydb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` | `lein migrate` |
| **PostgreSQL** | `CREATE DATABASE mydb;` | `lein migrate pg` |
| **SQLite** | *Auto-created* | `lein migrate localdb` |

### Default Users

After running `lein database`, these test users are available:

| Email | Password | Role | Access Level |
|-------|----------|------|--------------|
| user@example.com | user | User | Basic access |
| admin@example.com | admin | Admin | Administrative access |
| system@example.com | system | System | Full system access |

---

## 🧠 Core Concepts

### Architecture Overview

```
myapp/
├── src/myapp/
│   ├── handlers/           # Controllers (MVC)
│   │   ├── admin/         # Admin CRUD interfaces
│   │   ├── reports/       # Report handlers
│   │   └── <table>/       # Dashboard handlers
│   ├── routes/            # URL routing
│   ├── models/            # Database models
│   ├── layout.clj         # Page layout
│   └── menu.clj           # Navigation
└── resources/
    ├── private/config.clj # Database config
    └── migrations/        # Database migrations
```

### Component Types

| Component | Purpose | URL Pattern | Use Case |
|-----------|---------|-------------|----------|
| **Grid** | Full CRUD operations | `/admin/<table>` | Data management |
| **Dashboard** | Read-only table | `/<table>` | Data viewing |
| **Report** | Custom queries | `/reports/<name>` | Business reports |
| **Subgrid** | Child records | Modal in parent | Master-detail |

### Access Rights

| Code | Role | Description |
|------|------|-------------|
| **U** | User | Basic authenticated users |
| **A** | Admin | Administrative users |
| **S** | System | System administrators |

---

## 🔧 Generator Commands

### Grid Generator (CRUD)

**Purpose**: Creates full Create, Read, Update, Delete interfaces

```bash
# Basic syntax
lein grid <table> "Label:field" "Label:field" ...

# Examples
lein grid users "Name:firstname" "Email:email"
lein grid products "Product Name:name" "Price:price" "Category:category_id"

# With database connection
lein grid pg customers "Company:company_name" "Contact:contact_email"

# With access restrictions
lein grid orders "Order #:order_number" "Total:total_amount" :rights [A S]
```

**Generated Files**:
- `src/myapp/handlers/admin/<table>/controller.clj`
- `src/myapp/handlers/admin/<table>/model.clj`
- `src/myapp/handlers/admin/<table>/view.clj`

### Dashboard Generator (Read-Only)

**Purpose**: Creates read-only data tables

```bash
# Basic syntax
lein dashboard <table> "Label:field" "Label:field" ...

# Examples
lein dashboard products "Product:name" "Price:price" "Stock:quantity"
lein dashboard users "Name:firstname" "Email:email" "Joined:created_at"
```

**Generated Files**:
- `src/myapp/handlers/<table>/controller.clj`
- `src/myapp/handlers/<table>/model.clj`
- `src/myapp/handlers/<table>/view.clj`

### Report Generator

**Purpose**: Creates custom report pages

```bash
# Basic syntax
lein report <name>

# Examples
lein report monthlySales
lein report userActivity :rights [A S]
lein report pg inventoryReport :set-default
```

**Generated Files**:
- `src/myapp/handlers/reports/<name>/controller.clj`
- `src/myapp/handlers/reports/<name>/model.clj`
- `src/myapp/handlers/reports/<name>/view.clj`

### Subgrid Generator (Master-Detail)

**Purpose**: Creates child grids linked to parent records

```bash
# Basic syntax
lein subgrid <child-table> <parent-table> <foreign-key> ["Label:field" ...]

# Examples
lein subgrid order_items orders order_id "Product:product_name" "Qty:quantity"
lein subgrid user_addresses users user_id "Type:address_type" "Address:street"
```

**Requirements**:
- Parent table must have primary key `id`
- Child table must have foreign key to parent
- Child table must have primary key `id`

### Generator Options

| Option | Description | Example |
|--------|-------------|---------|
| `conn` | Specify database connection | `:conn pg` |
| `:rights` | Restrict access by role | `:rights [A S]` |
| `:set-default` | Save connection as default | `:set-default` |

---

## 🗄️ Database Support

### Supported Databases

| Database | Driver | Connection Key | Production Ready |
|----------|--------|----------------|------------------|
| **MySQL** | `com.mysql.cj.jdbc.Driver` | `db` | ✅ Yes |
| **PostgreSQL** | `org.postgresql.Driver` | `pg` | ✅ Yes |
| **SQLite** | `org.sqlite.JDBC` | `localdb` | ⚠️ Development |

### Migration Commands

| Command | Purpose | Example |
|---------|---------|---------|
| `lein migrate [conn]` | Run pending migrations | `lein migrate pg` |
| `lein rollback [conn]` | Rollback last migration | `lein rollback` |
| `lein database [conn]` | Seed test data | `lein database localdb` |

### Migration Files

Migrations are database-specific and located in `resources/migrations/`:

```
resources/migrations/
├── 001-initial.mysql.up.sql
├── 001-initial.postgresql.up.sql
├── 001-initial.sqlite.up.sql
├── 002-users.mysql.up.sql
└── ...
```

---

## 🔐 Access Control

### Setting Access Rights

```bash
# Admin and System only
lein grid sensitive_data "Data:value" :rights [A S]

# System only
lein dashboard system_logs "Time:timestamp" "Message:message" :rights [S]

# All authenticated users (default)
lein dashboard public_info "Title:title" "Content:content"
```

### Implementation

Generated controllers automatically check user permissions:

```clojure
(defn handler [request]
  (if (auth/has-rights? (:session request) [:A :S])
    (render-page request)
    (response/redirect "/unauthorized")))
```

---

## 🔗 Integrating Subgrids

### Manual Integration Example

After generating a subgrid, integrate it into the parent view:

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
                           {:title "User Contacts"
                            :table-name "user_contacts"
                            :foreign-key "user_id"
                            :href "/admin/usercontactsusers"
                            :icon "bi bi-people"
                            :label "Contacts"})]}]
    (build-grid-with-subgrids title rows table-id fields href args)))
```

---

## 🐛 Troubleshooting

### Common Issues

| Problem | Cause | Solution |
|---------|-------|----------|
| **Routes not updating** | Server cache | Restart dev server with `lein with-profile dev run` |
| **Database connection error** | Wrong credentials | Check `resources/private/config.clj` |
| **Subgrid not opening** | Missing foreign key | Verify FK exists in child table |
| **Permission denied** | Wrong user role | Check `:rights` setting in generator |
| **Migration failed** | SQL syntax error | Check database-specific SQL files |

### Debug Commands

```bash
# Check database connection
lein repl
=> (require '[myapp.db :as db])
=> (db/test-connection :db)

# Verify routes
=> (require '[myapp.routes.proutes :as routes])
=> (routes/app-routes)

# Test authentication
=> (require '[myapp.auth :as auth])
=> (auth/has-rights? {:user {:role "A"}} [:A :S])
```

---

## 📚 API Reference

### Quick Command Reference

#### Grid Commands
```bash
lein grid <table> "Label:field" ...                    # Basic CRUD
lein grid pg <table> "Label:field" ... :set-default    # PostgreSQL + save default
lein grid <table> "Label:field" ... :rights [A S]      # Restricted access
```

#### Dashboard Commands
```bash
lein dashboard <table> "Label:field" ...               # Read-only table
lein dashboard <table> "Label:field" ... :rights [S]   # System access only
```

#### Report Commands
```bash
lein report <name>                                      # Basic report
lein report <name> :rights [A S]                       # Admin/System only
```

#### Subgrid Commands
```bash
lein subgrid <child> <parent> <fk> "Label:field" ...   # Master-detail
lein subgrid <child> <parent> <fk> :rights [A]         # Admin only
```

### Database Operations

| Command | Description |
|---------|-------------|
| `lein migrate [conn]` | Apply pending migrations |
| `lein rollback [conn]` | Rollback last migration |
| `lein database [conn]` | Seed test data |

### Connection Keys

| Key | Database | Usage |
|-----|----------|-------|
| `db` | MySQL | Default production |
| `pg` | PostgreSQL | Alternative production |
| `localdb` | SQLite | Development/testing |

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## 🔗 Resources

- [Leiningen Documentation](https://leiningen.org/)
- [Clojure Documentation](https://clojure.org/)
- [Bootstrap 5 Documentation](https://getbootstrap.com/docs/5.0/)
- [DataTables Documentation](https://datatables.net/)

---

*Generated with ❤️ by LST - Lucero Systems Template*
