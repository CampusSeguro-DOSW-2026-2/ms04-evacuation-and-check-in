package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ResultadoEnvioMs05;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.SolicitudAsistenciaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;

/**
 * RF-MS04-04 Generar solicitud de asistencia en MS05 (Jira KAIR-575, historias KAIR-593 y KAIR-594).
 *
 * No es un endpoint: {@link CheckinService} registra la solicitud dentro de la transacción del check-in
 * Requiero asistencia (3_modelo-datos.md, "Operaciones"), y el envío a MS05 se hace después de confirmarla, para
 * que la indisponibilidad de MS05 no impida el check-in (RN-MS04-12)
 */
@Service
public class SolicitudAsistenciaService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudAsistenciaService.class);

    private final SolicitudAsistenciaRepository solicitudes;
    private final SolicitudAsistenciaClient ms05;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public SolicitudAsistenciaService(SolicitudAsistenciaRepository solicitudes, SolicitudAsistenciaClient ms05,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.solicitudes = solicitudes;
        this.ms05 = ms05;
        this.clock = clock;
        this.transaccion = new TransactionTemplate(transactionManager);
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
     * Envía a MS05 una solicitud PENDIENTE_ENVIO (RF-MS04-04, pasos 3 a 5), después de confirmada la transacción del
     * check-in. Toma la fila con bloqueo para que el check-in y el reenvío periódico no la envíen a la vez, cuenta el
     * intento y guarda el resultado: con el id de MS05 queda ENVIADA; si MS05 la rechaza por datos inválidos,
     * RECHAZADA (A4); si MS05 no responde, sigue PENDIENTE_ENVIO para el reenvío (A2)
     *
     * @param solicitudId solicitud a enviar
     * @param rolesUsuario roles del usuario para X-User-Role, o null en el reenvío periódico
     * @param correlationId correlation id con el que se llama a MS05
     * @return la solicitud como quedó y el resultado del envío; el resultado es null si no se intentó porque ya no
     *         estaba pendiente o la tenía otro envío
     */
    public EnvioSolicitud enviar(UUID solicitudId, String rolesUsuario, String correlationId) {
        return transaccion.execute(status -> {
            Optional<SolicitudAsistencia> pendiente = solicitudes.tomarPendiente(solicitudId);
            if (pendiente.isEmpty()) {
                return new EnvioSolicitud(solicitudes.findById(solicitudId).orElse(null), null);
            }
            SolicitudAsistencia solicitud = pendiente.get();
            ResultadoEnvioMs05 resultado = ms05.enviar(solicitud, rolesUsuario, correlationId);
            solicitud.setIntentos(solicitud.getIntentos() + 1);
            solicitud.setUltimoIntentoEn(clock.instant());
            switch (resultado.tipo()) {
                case CREADA -> {
                    solicitud.setSolicitudMs05Id(resultado.solicitudMs05Id());
                    solicitud.setEstadoEnvio(EstadoEnvio.ENVIADA);
                }
                case RECHAZADA -> {
                    solicitud.setEstadoEnvio(EstadoEnvio.RECHAZADA);
                    log.error("MS05 rechazó la solicitud de asistencia {} ({}); queda RECHAZADA y no se reenvía",
                            solicitud.getId(), resultado.detalle());
                }
                case NO_DISPONIBLE -> log.warn("No se pudo enviar la solicitud de asistencia {} a MS05 ({}), "
                        + "intento {}; queda PENDIENTE_ENVIO", solicitud.getId(), resultado.detalle(),
                        solicitud.getIntentos());
            }
            return new EnvioSolicitud(solicitudes.saveAndFlush(solicitud), resultado);
        });
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

    /**
     * Resultado de un envío a MS05
     *
     * @param solicitud la solicitud como quedó
     * @param resultado cómo terminó el envío; null si no se intentó
     */
    public record EnvioSolicitud(SolicitudAsistencia solicitud, ResultadoEnvioMs05 resultado) {
    }
}
