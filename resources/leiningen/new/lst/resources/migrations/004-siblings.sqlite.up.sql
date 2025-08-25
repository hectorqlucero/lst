create table if not exists siblings (
  id integer primary key autoincrement,
  name text,
  age integer,
  imagen text,
  contacto_id integer,
  foreign key (contacto_id) references contactos(id) on delete cascade
  );
