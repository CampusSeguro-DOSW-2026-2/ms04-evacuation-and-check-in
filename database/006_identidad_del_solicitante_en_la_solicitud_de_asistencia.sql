-- MS05 registra la solicitud de asistencia como un incidente a nombre del usuario del check-in y lo autoriza con sus
-- permisos (X-User-Id, X-User-Role y X-User-Permission). El reenvío periódico de las solicitudes pendientes no tiene
-- una petición de usuario, así que la identidad se guarda con la solicitud al crearla. Las columnas son nulas en las
-- solicitudes anteriores. En una base nueva corre después de 001; en una ya creada se aplica una vez a mano (los
-- scripts de database/ solo corren al crear el volumen).
ALTER TABLE solicitudes_asistencia
    ADD COLUMN IF NOT EXISTS roles_solicitante TEXT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS permisos_solicitante TEXT DEFAULT NULL;
