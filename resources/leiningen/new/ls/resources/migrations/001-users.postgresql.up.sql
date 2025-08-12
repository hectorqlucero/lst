CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  lastname VARCHAR(255),
  firstname VARCHAR(255),
  username VARCHAR(255) UNIQUE,
  password VARCHAR(255),
  dob DATE,
  cell VARCHAR(50),
  phone VARCHAR(50),
  fax VARCHAR(50),
  email VARCHAR(255),
  level CHAR(1),
  active CHAR(1),
  imagen VARCHAR(255),
  last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
