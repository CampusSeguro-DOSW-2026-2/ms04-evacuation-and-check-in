package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentro;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentroClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.SolicitudAsistenciaResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.SolicitudAsistenciaService.RegistroSolicitud;

/**
 * RF-MS04-02 Registrar check-in y RF-MS04-03 Mantener y actualizar el estado vigente (Jira KAIR-573 y KAIR-574,
 * historias KAIR-590, KAIR-591 y KAIR-592).
 *
 * Los check-ins solo se agregan: cada reporte es un check-in nuevo y el estado vigente del usuario es el más
 * reciente (RN-MS04-07). El orden sigue 3_modelo-datos.md ("Operaciones"): la llamada a MS02 se hace antes de abrir
 * la transacción, y en la transacción se relee el evento con bloqueo compartido para que un FINALIZADO no se cruce
 */
@Service
public class CheckinService {

    public static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";
    public static final String CHECKIN_NO_HABILITADO = "El check-in de este evento no está habilitado.";
    public static final String EVENTO_NO_APLICA = "Este evento no le aplica.";
    public static final String PUNTO_NO_ENCONTRADO = "Punto de encuentro no encontrado.";
    public static final String PUNTO_INACTIVO = "El punto de encuentro no está activo.";
    /** Detalle de auditoría de un reporte que repite el estado vigente */
    public static final String SIN_CAMBIO = "SIN_CAMBIO";

    private static final Logger log = LoggerFactory.getLogger(CheckinService.class);

    private final EventoRepository eventos;
    private final CheckinRepository checkins;
    private final PuntoEncuentroClient puntosEncuentro;
    private final AuditRecorder auditoria;
    private final SolicitudAsistenciaService solicitudes;
    private final TransactionTemplate transaccion;

    public CheckinService(EventoRepository eventos, CheckinRepository checkins, PuntoEncuentroClient puntosEncuentro,
            AuditRecorder auditoria, SolicitudAsistenciaService solicitudes,
            PlatformTransactionManager transactionManager) {
        this.eventos = eventos;
        this.checkins = checkins;
        this.puntosEncuentro = puntosEncuentro;
        this.auditoria = auditoria;
        this.solicitudes = solicitudes;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /**
     * Registra el check-in del usuario en el evento (RF-MS04-02).
     *
     * Pasos: valida el cuerpo según el estado (RN-MS04-03, RG-05); verifica que el evento exista (404), que su
     * check-in esté habilitado (409) y que convoque alguno de los roles del usuario (422); si es SAFE, valida con
     * MS02 que el punto de encuentro exista (404) y esté ACTIVO (422), o 503 si MS02 no responde; y en una
     * transacción, relee el evento con bloqueo compartido, vuelve a verificarlo y agrega el check-in
     *
     * @param eventoId evento del check-in
     * @param request estado reportado y, según el estado, punto de encuentro o ubicación
     * @param requester identidad del solicitante; el usuario sale de aquí y nunca del cuerpo (RN-MS04-06)
     * @param correlationId correlation id de la petición, para las llamadas salientes y la auditoría
     * @return el check-in y si es el primero del usuario en el evento (201) o no (200)
     */
    public ResultadoCheckin registrar(UUID eventoId, CheckinRequest request, RequesterIdentity requester,
            String correlationId) {
        Registro registro;
        try {
            CheckinValidator.validar(request);
            // El evento se revisa antes de llamar a MS02, para no consultarlo si el check-in igual se va a rechazar
            verificarEvento(eventos.findById(eventoId), requester);
            if (request.estado() == EstadoCheckin.SAFE) {
                validarPuntoEncuentro(request.puntoEncuentroId(), requester, correlationId);
            }
            registro = transaccion.execute(status -> guardar(eventoId, request, requester));
        } catch (RuntimeException e) {
            auditarFallo(eventoId, requester, e, correlationId);
            throw e;
        }
        auditarExito(registro, requester, correlationId);
        return new ResultadoCheckin(aRespuesta(registro.checkin(), registro.solicitud()), registro.creado());
    }

    /**
     * Auditoría de un check-in aceptado: REGISTRO_CHECKIN si es el primero del usuario en el evento;
     * CAMBIO_ESTADO_CHECKIN con el estado anterior y el nuevo si lo cambia, o con SIN_CAMBIO si repite el vigente
     */
    private void auditarExito(Registro registro, RequesterIdentity requester, String correlationId) {
        String recurso = AuditRecorder.checkinResource(registro.checkin().getId());
        if (registro.anterior() == null) {
            auditoria.recordAs(AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, requester, recurso,
                    registro.checkin().getEstado().name(), correlationId);
            return;
        }
        String detalle = registro.anterior() == registro.checkin()
                ? SIN_CAMBIO
                : registro.anterior().getEstado().name() + "->" + registro.checkin().getEstado().name();
        auditoria.recordAs(AuditAction.CAMBIO_ESTADO_CHECKIN, AuditResult.EXITO, requester, recurso, detalle,
                correlationId);
    }

    /**
     * Auditoría de un check-in rechazado: el recurso es el evento, porque no se creó check-in, y el detalle es el
     * código del error. La acción es CAMBIO_ESTADO_CHECKIN si el usuario ya tenía estado vigente en el evento y
     * REGISTRO_CHECKIN si no. Nunca lanza, para no tapar el error original
     */
    private void auditarFallo(UUID eventoId, RequesterIdentity requester, RuntimeException error,
            String correlationId) {
        String detalle = error instanceof ApiException api ? api.getCode().getCode() : ErrorCode.INTERNAL_ERROR.getCode();
        AuditAction accion = AuditAction.REGISTRO_CHECKIN;
        try {
            if (eventoId != null && checkins.buscarVigente(eventoId, requester.userId()).isPresent()) {
                accion = AuditAction.CAMBIO_ESTADO_CHECKIN;
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo leer el estado vigente para auditar el check-in fallido: {}", e.toString());
        }
        auditoria.recordAs(accion, AuditResult.FALLO, requester, AuditRecorder.eventResource(eventoId), detalle,
                correlationId);
    }

    /**
     * Transacción del check-in (RF-MS04-03): toma el bloqueo asesor del usuario en el evento para que sus reportes
     * simultáneos se atiendan uno tras otro (RNF-MS04-04), relee el evento con bloqueo compartido
     * (SELECT ... FOR SHARE) y lo vuelve a verificar por si cambió mientras se consultaba MS02, y compara con el
     * estado vigente: si el reporte repite el vigente no agrega nada y devuelve el vigente; si no, agrega el check-in
     */
    private Registro guardar(UUID eventoId, CheckinRequest request, RequesterIdentity requester) {
        checkins.bloquearUsuarioEnEvento(eventoId, requester.userId());
        verificarEvento(eventos.buscarParaCheckin(eventoId), requester);
        Optional<Checkin> vigente = checkins.buscarVigente(eventoId, requester.userId());
        if (vigente.isPresent() && repiteElVigente(vigente.get(), request)) {
            SolicitudAsistencia solicitud = vigente.get().getEstado() == EstadoCheckin.NEEDS_ASSISTANCE
                    ? solicitudes.buscar(eventoId, requester.userId()).orElse(null)
                    : null;
            return new Registro(vigente.get(), false, vigente.get(), solicitud, false);
        }
        Checkin nuevo = checkins.save(nuevoCheckin(eventoId, request, requester));
        checkins.flush();
        if (nuevo.getEstado() == EstadoCheckin.NEEDS_ASSISTANCE) {
            // RF-MS04-04: la solicitud se registra en la misma transacción que el check-in, en PENDIENTE_ENVIO
            RegistroSolicitud solicitud = solicitudes.registrar(nuevo);
            return new Registro(nuevo, vigente.isEmpty(), vigente.orElse(null), solicitud.solicitud(),
                    solicitud.nueva());
        }
        return new Registro(nuevo, vigente.isEmpty(), vigente.orElse(null), null, false);
    }

    /**
     * Un reporte repite el vigente si trae el mismo estado; con Estoy a salvo, además, el mismo punto de encuentro.
     * Requiero asistencia con otra ubicación sigue siendo el mismo estado (1_requirements.md, RF-MS04-03, A3)
     */
    static boolean repiteElVigente(Checkin vigente, CheckinRequest request) {
        if (vigente.getEstado() != request.estado()) {
            return false;
        }
        return request.estado() != EstadoCheckin.SAFE
                || Objects.equals(vigente.getPuntoEncuentroId(), request.puntoEncuentroId());
    }

    /**
     * El evento debe existir (404), tener el check-in habilitado (409, RN-MS04-05) y convocar alguno de los roles
     * del usuario (422, RN-MS04-01)
     */
    private static Evento verificarEvento(Optional<Evento> encontrado, RequesterIdentity requester) {
        Evento evento = encontrado.orElseThrow(() -> ApiException.noEncontrado(EVENTO_NO_ENCONTRADO));
        if (!evento.isCheckinHabilitado()) {
            throw ApiException.conflicto(CHECKIN_NO_HABILITADO);
        }
        List<String> roles = requester.nombresDeRoles();
        if (evento.getRolesConvocados().stream().noneMatch(roles::contains)) {
            throw ApiException.reglaNegocio(EVENTO_NO_APLICA);
        }
        return evento;
    }

    /**
     * Estoy a salvo exige un punto de encuentro existente y ACTIVO en MS02 (RN-MS04-04)
     */
    private void validarPuntoEncuentro(UUID puntoEncuentroId, RequesterIdentity requester, String correlationId) {
        PuntoEncuentro punto = puntosEncuentro.buscar(puntoEncuentroId, requester, correlationId)
                .orElseThrow(() -> ApiException.noEncontrado(PUNTO_NO_ENCONTRADO));
        if (!punto.activo()) {
            throw ApiException.reglaNegocio(PUNTO_INACTIVO);
        }
    }

    private static Checkin nuevoCheckin(UUID eventoId, CheckinRequest request, RequesterIdentity requester) {
        Ubicacion ubicacion = request.ubicacion();
        boolean conUbicacion = request.estado() == EstadoCheckin.NEEDS_ASSISTANCE && ubicacion != null;
        return new Checkin(eventoId, requester.userId(), request.estado(),
                request.estado() == EstadoCheckin.SAFE ? request.puntoEncuentroId() : null,
                conUbicacion ? ubicacion.tipo() : null,
                conUbicacion ? ubicacion.id() : null);
    }

    /**
     * Respuesta del check-in. Con Requiero asistencia incluye el id de la solicitud en MS05 (null mientras no llegue)
     * y su estado de envío (RF-MS04-04, paso 7)
     */
    private static CheckinResponse aRespuesta(Checkin checkin, SolicitudAsistencia solicitud) {
        Ubicacion ubicacion = checkin.getUbicacionTipo() == null
                ? null
                : new Ubicacion(checkin.getUbicacionTipo(), checkin.getUbicacionId());
        return new CheckinResponse(checkin.getId(), checkin.getEventoId(), checkin.getEstado(),
                checkin.getPuntoEncuentroId(), ubicacion, resumir(solicitud), checkin.getRegistradoEn());
    }

    private static SolicitudAsistenciaResumen resumir(SolicitudAsistencia solicitud) {
        return solicitud == null
                ? null
                : new SolicitudAsistenciaResumen(solicitud.getSolicitudMs05Id(), solicitud.getEstadoEnvio());
    }

    /**
     * Resultado de la transacción del check-in
     *
     * @param checkin check-in nuevo, o el vigente si el reporte lo repetía
     * @param creado true si es el primer check-in del usuario en el evento
     * @param anterior estado vigente antes del reporte; null en el primer check-in
     * @param solicitud solicitud de asistencia del usuario en el evento si el check-in es NEEDS_ASSISTANCE
     * @param solicitudNueva true si la solicitud se creó con este check-in y hay que enviarla a MS05
     */
    private record Registro(Checkin checkin, boolean creado, Checkin anterior, SolicitudAsistencia solicitud,
            boolean solicitudNueva) {
    }
}
