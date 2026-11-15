-- Roles y permisos flexibles (Estándares de Integración, secciones 3 y 6): los roles son datos de MS01 y ya no son un
-- conjunto fijo, así que un rol convocado es cualquier nombre con el formato de rol (mayúsculas, dígitos y guion bajo,
-- empieza con una letra, de 2 a 30 caracteres), sin elementos nulos y con al menos uno. La columna pasa a 30 caracteres,
-- el largo máximo de un nombre de rol. En una base nueva corre después de 001; en una ya creada se aplica una vez a mano
-- (los scripts de database/ solo corren al crear el volumen).
ALTER TABLE eventos DROP CONSTRAINT IF EXISTS chk_eventos_roles_convocados;

ALTER TABLE eventos ALTER COLUMN roles_convocados TYPE VARCHAR(30)[];

ALTER TABLE eventos ADD CONSTRAINT chk_eventos_roles_convocados
    CHECK (cardinality(roles_convocados) >= 1
           AND array_position(roles_convocados, NULL) IS NULL
           AND array_to_string(roles_convocados, ',') ~ '^[A-Z][A-Z0-9_]{1,29}(,[A-Z][A-Z0-9_]{1,29})*$');
