package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
