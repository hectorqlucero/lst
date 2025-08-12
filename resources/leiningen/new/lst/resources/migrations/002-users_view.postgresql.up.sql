CREATE OR REPLACE VIEW users_view AS
SELECT 
    id,
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
    to_char(dob, 'DD/MM/YYYY') AS dob_formatted,
    CASE
        WHEN level = 'U' THEN 'Usuario'
        WHEN level = 'A' THEN 'Administrador'
        ELSE 'Sistema'
    END AS level_formatted,
    CASE
        WHEN active = 'T' THEN 'Activo'
        ELSE 'Inactivo'
    END AS active_formatted
FROM users
ORDER BY lastname, firstname;
