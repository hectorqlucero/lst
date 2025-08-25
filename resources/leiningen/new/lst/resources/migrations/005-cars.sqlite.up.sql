create table if not exists cars (
  id integer primary key autoincrement,
  company text,
  model text,
  year integer,
  imagen text,
  contacto_id integer,
  foreign key (contacto_id) references contactos(id) on delete cascade
  );
