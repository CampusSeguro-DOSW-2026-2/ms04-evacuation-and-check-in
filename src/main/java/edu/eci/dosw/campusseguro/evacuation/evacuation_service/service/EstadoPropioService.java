package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.SolicitudAsistenciaResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-05 Consultar el propio estado vigente (Jira KAIR-576, historia KAIR-595)
 *
 * Solo lee: no agrega, modifica ni elimina datos, y por eso no genera auditoría (RNF-MS04-04, RNF-MS04-17)
 */
@Service
public class EstadoPropioService {

    /** Mensaje cuando el evento no existe (A2 del RF-MS04-05) */
    public static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";

    /** Mensaje cuando el usuario todavía no reportó su estado en el evento (A1 del RF-MS04-05) */
    public static final String SIN_CHECKIN = "No tiene check-in en este evento.";

    private static final Logger log = LoggerFactory.getLogger(EstadoPropioService.class);

    private final EventoRepository eventos;
    private final CheckinRepository checkins;
    private final SolicitudAsistenciaRepository solicitudes;

    /**
     * Crea el service con los repositorios que necesita para leer
     *
     * @param eventos acceso a los eventos
     * @param checkins acceso a los check-ins y a la vista del estado vigente
     * @param solicitudes acceso a las solicitudes de asistencia
     */
    public EstadoPropioService(EventoRepository eventos, CheckinRepository checkins,
            SolicitudAsistenciaRepository solicitudes) {
        this.eventos = eventos;
        this.checkins = checkins;
        this.solicitudes = solicitudes;
    }

    /**
     * Estado vigente del usuario en el evento: su check-in más reciente, sin los anteriores (RN-MS04-06,
     * RN-MS04-07 y RN-MS04-08). La identidad sale únicamente de X-User-Id, así que nadie puede consultar el estado
     * de otra persona. Funciona con el evento en cualquier estado (RN-MS04-22)
     *
     * @param eventoId evento
     * @param requester identidad del solicitante; solo puede consultar su propio estado
     * @return el estado vigente con el estado de envío de su solicitud, si aplica
     * @throws ApiException 404 NO_ENCONTRADO si el evento no existe o si el usuario no tiene check-in en él
     */
    @Transactional(readOnly = true)
    public CheckinResponse consultar(UUID eventoId, RequesterIdentity requester) {
        if (!eventos.existsById(eventoId)) {
            throw ApiException.noEncontrado(EVENTO_NO_ENCONTRADO);
        }
        Optional<Checkin> encontrado = checkins.buscarVigente(eventoId, requester.userId());
        if (encontrado.isEmpty()) {
            throw ApiException.noEncontrado(SIN_CHECKIN);
        }
        Checkin vigente = encontrado.get();
        log.debug("Estado vigente consultado: evento={}, estado={}", eventoId, vigente.getEstado());
        return armarRespuesta(vigente);
    }

    /**
     * Arma la respuesta con los campos que corresponden al estado: el punto de encuentro solo viene con SAFE
     * (RN-MS04-04), y la ubicación con la solicitud de asistencia solo con NEEDS_ASSISTANCE (RN-MS04-09)
     *
     * @param vigente check-in más reciente del usuario en el evento
     * @return la respuesta del contrato
     */
    private CheckinResponse armarRespuesta(Checkin vigente) {
        Ubicacion ubicacion = null;
        SolicitudAsistenciaResumen solicitud = null;
        if (vigente.getEstado() == EstadoCheckin.NEEDS_ASSISTANCE) {
            ubicacion = new Ubicacion(vigente.getUbicacionTipo(), vigente.getUbicacionId());
            solicitud = resumirSolicitud(vigente);
        }
        return new CheckinResponse(vigente.getId(), vigente.getEventoId(), vigente.getEstado(),
                vigente.getPuntoEncuentroId(), ubicacion, solicitud, vigente.getRegistradoEn());
    }

    /**
     * Estado de envío de la solicitud de asistencia del usuario en el evento. Se busca por usuario y evento y no por
     * el check-in: hay una sola solicitud por usuario y evento (RN-MS04-11) y quedó ligada al primer check-in que
     * la pidió, aunque después el usuario haya cambiado de estado y vuelto a pedir asistencia
     *
     * @param vigente check-in NEEDS_ASSISTANCE vigente del usuario
     * @return el resumen de la solicitud, o null si no existe (no debería pasar: se crea junto con el check-in)
     */
    private SolicitudAsistenciaResumen resumirSolicitud(Checkin vigente) {
        Optional<SolicitudAsistencia> encontrada = solicitudes.findByEventoIdAndUsuarioId(vigente.getEventoId(),
                vigente.getUsuarioId());
        if (encontrada.isEmpty()) {
            log.warn("El check-in NEEDS_ASSISTANCE vigente del evento {} no tiene solicitud de asistencia",
                    vigente.getEventoId());
            return null;
        }
        SolicitudAsistencia solicitud = encontrada.get();
        return new SolicitudAsistenciaResumen(solicitud.getSolicitudMs05Id(), solicitud.getEstadoEnvio());
    }
}
