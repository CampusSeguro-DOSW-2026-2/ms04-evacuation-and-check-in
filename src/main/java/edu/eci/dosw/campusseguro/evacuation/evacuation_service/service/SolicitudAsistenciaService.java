package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;

/**
 * RF-MS04-04 Generar solicitud de asistencia en MS05 (Jira KAIR-575, historias KAIR-593 y KAIR-594).
 *
 * No es un endpoint: {@link CheckinService} registra la solicitud dentro de la transacción del check-in
 * Requiero asistencia (3_modelo-datos.md, "Operaciones"), y el envío a MS05 se hace después de confirmarla
 */
@Service
public class SolicitudAsistenciaService {

    private final SolicitudAsistenciaRepository solicitudes;

    public SolicitudAsistenciaService(SolicitudAsistenciaRepository solicitudes) {
        this.solicitudes = solicitudes;
    }

    /**
     * Registra la solicitud de asistencia de un check-in Requiero asistencia recién agregado, en PENDIENTE_ENVIO.
     * Un usuario tiene como máximo una solicitud por evento (RN-MS04-11): si ya tiene una (enviada, pendiente o
     * rechazada), no se crea otra y se reutiliza (A3). Debe llamarse dentro de la transacción del check-in, que ya
     * tiene el bloqueo del usuario en el evento
     *
     * @param checkin check-in NEEDS_ASSISTANCE ya guardado, con su fecha/hora de registro
     * @return la solicitud y si se creó en esta llamada
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistroSolicitud registrar(Checkin checkin) {
        if (checkin.getEstado() != EstadoCheckin.NEEDS_ASSISTANCE) {
            throw new IllegalArgumentException("Solo un check-in NEEDS_ASSISTANCE origina una solicitud");
        }
        Optional<SolicitudAsistencia> existente = solicitudes.findByEventoIdAndUsuarioId(checkin.getEventoId(),
                checkin.getUsuarioId());
        if (existente.isPresent()) {
            return new RegistroSolicitud(existente.get(), false);
        }
        SolicitudAsistencia nueva = new SolicitudAsistencia(checkin.getId(), checkin.getEventoId(),
                checkin.getUsuarioId(), checkin.getUbicacionTipo(), checkin.getUbicacionId(),
                checkin.getRegistradoEn());
        return new RegistroSolicitud(solicitudes.saveAndFlush(nueva), true);
    }

    /**
     * Solicitud de asistencia de un usuario en un evento, si tiene una
     *
     * @param eventoId evento
     * @param usuarioId usuario
     * @return la solicitud, si existe
     */
    @Transactional(readOnly = true)
    public Optional<SolicitudAsistencia> buscar(UUID eventoId, UUID usuarioId) {
        return solicitudes.findByEventoIdAndUsuarioId(eventoId, usuarioId);
    }

    /**
     * Reenvía a MS05 las solicitudes PENDIENTE_ENVIO, sin duplicarlas: el id de la solicitud viaja como llave de
     * idempotencia (RN-MS04-12). La frecuencia de la tarea que lo llama se configura por variable de entorno
     *
     * @return cuántas solicitudes se enviaron
     */
    public int reenviarPendientes() {
        throw ApiException.noImplementado("RF-MS04-04 reenviar las solicitudes pendientes");
    }

    /**
     * Solicitud registrada con el check-in
     *
     * @param solicitud solicitud del usuario en el evento
     * @param nueva true si se creó con este check-in; false si ya existía y se reutilizó
     */
    public record RegistroSolicitud(SolicitudAsistencia solicitud, boolean nueva) {
    }
}
