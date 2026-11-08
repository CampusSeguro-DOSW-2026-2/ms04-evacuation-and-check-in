-- MS04 - Índices y vista del estado vigente (3_modelo-datos.md)

-- Eventos en ejecución (RF-MS04-01) y consulta por estado (RF-MS04-13)
CREATE INDEX idx_eventos_estado ON eventos (estado);

-- Check-in más reciente de cada usuario en un evento (estado vigente)
CREATE INDEX idx_checkins_vigente ON checkins (evento_id, usuario_id, registrado_en DESC, numero DESC);

-- Histórico y lista de asistencia en orden cronológico
CREATE INDEX idx_checkins_evento_fecha ON checkins (evento_id, registrado_en);

-- Reenvío de las solicitudes pendientes (RN-MS04-12)
CREATE INDEX idx_solicitudes_pendientes ON solicitudes_asistencia (estado_envio);

-- Solicitudes de un evento
CREATE INDEX idx_solicitudes_evento ON solicitudes_asistencia (evento_id, reportada_en);

-- Estado vigente: el check-in más reciente de cada usuario en cada evento (RN-MS04-07).
-- La usan el consolidado, las personas por estado, los pendientes, la lista de asistencia, el propio estado
-- y el detalle para MS06.
CREATE VIEW v_checkins_vigentes AS
SELECT DISTINCT ON (evento_id, usuario_id) *
FROM checkins
ORDER BY evento_id, usuario_id, registrado_en DESC, numero DESC;
