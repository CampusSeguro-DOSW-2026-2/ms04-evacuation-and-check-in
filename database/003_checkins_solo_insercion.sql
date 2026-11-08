-- MS04 - Los check-ins solo se agregan: nunca se actualizan ni se eliminan (RN-MS04-07, RN-MS04-08, RNF-MS04-04)
--
-- El documento del modelo pide reforzarlo con permisos de solo INSERT y SELECT para el usuario de la aplicación.
-- En el contenedor de PostgreSQL ese usuario es el dueño de las tablas y los permisos no lo restringirían,
-- así que la regla se garantiza con un trigger, que aplica a cualquier usuario.

CREATE FUNCTION fn_checkins_solo_insercion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los check-ins solo se agregan: no se permite % sobre checkins', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_checkins_solo_insercion
    BEFORE UPDATE OR DELETE ON checkins
    FOR EACH ROW EXECUTE FUNCTION fn_checkins_solo_insercion();

CREATE TRIGGER trg_checkins_sin_truncate
    BEFORE TRUNCATE ON checkins
    FOR EACH STATEMENT EXECUTE FUNCTION fn_checkins_solo_insercion();
