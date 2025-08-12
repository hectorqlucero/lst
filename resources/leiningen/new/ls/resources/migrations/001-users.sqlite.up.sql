CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  lastname TEXT,
  firstname TEXT,
  username TEXT UNIQUE,
  password TEXT,
  dob TEXT,
  cell TEXT,
  phone TEXT,
  fax TEXT,
  email TEXT,
  level TEXT,
  active TEXT,
  imagen TEXT,
  last_login TEXT DEFAULT (datetime('now'))
);
