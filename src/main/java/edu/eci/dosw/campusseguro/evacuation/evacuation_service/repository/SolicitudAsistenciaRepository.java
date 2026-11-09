package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;

/**
 * Acceso a las solicitudes de asistencia generadas desde el check-in (RF-MS04-04)
 */
public interface SolicitudAsistenciaRepository extends JpaRepository<SolicitudAsistencia, UUID> {

    /**
     * Solicitud de un usuario en un evento: hay como máximo una (RN-MS04-11)
     *
     * @param eventoId evento
     * @param usuarioId usuario
     * @return la solicitud, si existe
     */
    Optional<SolicitudAsistencia> findByEventoIdAndUsuarioId(UUID eventoId, UUID usuarioId);

    /**
     * Solicitudes en un estado de envío, p. ej. las PENDIENTE_ENVIO para reenviarlas (RN-MS04-12)
     *
     * @param estadoEnvio estado de envío
     * @return las solicitudes en ese estado
     */
    List<SolicitudAsistencia> findByEstadoEnvio(EstadoEnvio estadoEnvio);

    /**
     * Solicitudes de un evento
     *
     * @param eventoId evento
     * @return las solicitudes del evento
     */
    List<SolicitudAsistencia> findByEventoId(UUID eventoId);

    /**
     * Ids de las solicitudes en un estado de envío, de la más antigua a la más reciente: el reenvío periódico atiende
     * primero a quien lleva más tiempo esperando
     *
     * @param estadoEnvio estado de envío
     * @return ids en orden de reporte
     */
    @Query("SELECT s.id FROM SolicitudAsistencia s WHERE s.estadoEnvio = :estado ORDER BY s.reportadaEn, s.id")
    List<UUID> idsPorEstadoEnvio(@Param("estado") EstadoEnvio estadoEnvio);

    /**
     * Toma la solicitud para enviarla a MS05 si sigue PENDIENTE_ENVIO, con bloqueo de fila (SELECT ... FOR UPDATE
     * SKIP LOCKED): si otro envío la tiene tomada (el del check-in o el reenvío periódico), no la devuelve, y así
     * nunca se envía dos veces a la vez (RN-MS04-12)
     *
     * @param id id de la solicitud
     * @return la solicitud bloqueada, o vacío si ya no está pendiente o la tiene otro envío
     */
    @Query(value = "SELECT * FROM solicitudes_asistencia WHERE id = :id AND estado_envio = 'PENDIENTE_ENVIO' "
            + "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<SolicitudAsistencia> tomarPendiente(@Param("id") UUID id);
}
