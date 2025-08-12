CREATE VIEW IF NOT EXISTS users_view AS
SELECT id,
    lastname,
    firstname,
    username,
    dob,
    cell,
    phone,
    fax,
    email,
    level,
    active,
    imagen,
    last_login,
    strftime('%d/%m/%Y', dob) as dob_formatted,
    CASE
        WHEN level = 'U' THEN 'Usuario'
        WHEN level = 'A' THEN 'Administrador'
        ELSE 'Sistema'
    END as level_formatted,
    CASE
        WHEN active = 'T' THEN 'Activo'
        ELSE 'Inactivo'
    END as active_formatted
FROM users
ORDER BY lastname,
    firstname;
