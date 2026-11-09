package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EstadoEventoRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoEstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoEventoValidator.EstadoValidado;

/**
 * RF-MS04-11 Recibir el estado del evento y habilitar o deshabilitar el check-in (Jira KAIR-571, historia
 * KAIR-588). Es la única operación que crea o modifica eventos; MS04 nunca inicia ni termina un evento por reloj
 * ni consulta a MS06 por su cuenta.
 *
 * Pasos:
 * 1. Valida el cuerpo con F-05 (400)
 * 2. Determina el estado efectivo: con {@code simulacro = false}, todo estado distinto de FINALIZADO es
 *    EN_EJECUCION (RN-MS04-20)
 * 3. En una transacción: toma el bloqueo del evento (así dos llamadas simultáneas, incluso sobre un evento
 *    nuevo, se atienden una tras otra, RNF-MS04-18), lo lee con SELECT ... FOR UPDATE, valida la transición de
 *    RN-MS04-21 (409) y crea o actualiza el evento
 * 4. Audita CAMBIO_ESTADO_EVENTO hacia MS08, con éxito o con falla, con el servicio de origen y los estados en el
 *    detalle (RNF-MS04-17)
 */
@Service
public class EstadoEventoService {

    public static final String TIPO_NO_PUEDE_CAMBIAR = "El tipo de evento no puede cambiar.";
    public static final String TRANSICION_NO_PERMITIDA = "La transición de estado no está permitida.";
    /** Estado anterior en el detalle de auditoría cuando el evento no existía */
    public static final String NUEVO = "NUEVO";

    private final EventoRepository eventos;
    private final AuditRecorder auditoria;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    /**
     * @param eventos repositorio de eventos
     * @param auditoria registro de auditoría hacia MS08
     * @param clock reloj de la aplicación: da la fecha de recepción (inicio por defecto y fecha de cierre)
     * @param transactionManager administrador de transacciones
     */
    public EstadoEventoService(EventoRepository eventos, AuditRecorder auditoria, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.clock = clock;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /** Resultado de la transacción: el evento y su estado antes de la llamada (null si no existía) */
    private record Cambio(Evento evento, EstadoEvento anterior) {
    }

    /**
     * Crea o actualiza el evento con el estado que informa MS06
     *
     * @param eventoId id del evento que asigna MS06 (el de la ruta)
     * @param request estado del evento; su {@code eventoId} debe ser igual al de la ruta
     * @param servicio servicio que llama (X-Service-Name), para la auditoría
     * @param correlationId correlation id de la petición
     * @return el evento almacenado y si era nuevo (201) o ya existía (200)
     */
    public ResultadoEstadoEvento recibir(UUID eventoId, EstadoEventoRequest request, String servicio,
            String correlationId) {
        Cambio cambio;
        try {
            EstadoValidado datos = EstadoEventoValidator.validar(eventoId, request);
            cambio = transaccion.execute(status -> aplicar(eventoId, datos));
        } catch (RuntimeException e) {
            String codigo = e instanceof ApiException api ? api.getCode().getCode() : ErrorCode.INTERNAL_ERROR.getCode();
            auditoria.recordSystem(AuditAction.CAMBIO_ESTADO_EVENTO, AuditResult.FALLO,
                    AuditRecorder.eventResource(eventoId), detalle(servicio, codigo), correlationId);
            throw e;
        }
        String anterior = cambio.anterior() == null ? NUEVO : cambio.anterior().name();
        auditoria.recordSystem(AuditAction.CAMBIO_ESTADO_EVENTO, AuditResult.EXITO,
                AuditRecorder.eventResource(eventoId),
                detalle(servicio, anterior + "->" + cambio.evento().getEstado().name()), correlationId);
        return new ResultadoEstadoEvento(EventoMapper.aInterno(cambio.evento()), cambio.anterior() == null);
    }

    /**
     * Estado efectivo (RN-MS04-20): un evento que no es simulacro es inmediato y queda EN_EJECUCION, salvo que
     * llegue FINALIZADO
     *
     * @param simulacro tipo de evento
     * @param recibido estado que envía MS06
     * @return el estado que se guarda
     */
    static EstadoEvento estadoEfectivo(boolean simulacro, EstadoEvento recibido) {
        if (!simulacro && recibido != EstadoEvento.FINALIZADO) {
            return EstadoEvento.EN_EJECUCION;
        }
        return recibido;
    }

    private Cambio aplicar(UUID eventoId, EstadoValidado datos) {
        eventos.bloquearEvento(eventoId);
        EstadoEvento efectivo = estadoEfectivo(datos.simulacro(), datos.estado());
        Optional<Evento> guardado = eventos.buscarParaActualizar(eventoId);
        if (guardado.isEmpty()) {
            return new Cambio(crear(eventoId, datos, efectivo), null);
        }
        Evento evento = guardado.get();
        EstadoEvento anterior = evento.getEstado();
        if (evento.isSimulacro() != datos.simulacro()) {
            throw ApiException.conflicto(TIPO_NO_PUEDE_CAMBIAR);
        }
        actualizar(evento, datos, efectivo);
        eventos.flush();
        return new Cambio(evento, anterior);
    }

    /**
     * Un evento nuevo puede llegar en cualquier estado (RN-MS04-21). Sin fecha de inicio, salvo en BORRADOR, se
     * toma la fecha/hora de recepción (RN-MS04-24); con FINALIZADO se guarda la fecha de cierre
     */
    private Evento crear(UUID eventoId, EstadoValidado datos, EstadoEvento efectivo) {
        Instant ahora = ahora();
        Evento evento = new Evento(eventoId);
        evento.setSimulacro(datos.simulacro());
        evento.setEstado(efectivo);
        evento.setFechaInicio(efectivo == EstadoEvento.BORRADOR ? null : fechaODefecto(datos, ahora));
        evento.setFechaCierre(efectivo == EstadoEvento.FINALIZADO ? ahora : null);
        copiarInformacion(evento, datos);
        return eventos.saveAndFlush(evento);
    }

    /**
     * Transiciones aceptadas con el evento ya guardado (RN-MS04-21). Cualquier otra combinación responde 409 y no
     * modifica nada. Repetir EN_EJECUCION o FINALIZADO no escribe nada (idempotente)
     */
    private void actualizar(Evento evento, EstadoValidado datos, EstadoEvento efectivo) {
        EstadoEvento actual = evento.getEstado();
        switch (actual) {
            case BORRADOR -> {
                switch (efectivo) {
                    // Actualiza nombre, escenario y roles; sigue sin fecha
                    case BORRADOR -> copiarInformacion(evento, datos);
                    // Programación: guarda la fecha de inicio (obligatoria con PROGRAMADO)
                    case PROGRAMADO -> programar(evento, datos, datos.fechaInicio());
                    // Inicio sin programación previa: sin fecha, se toma la de recepción
                    case EN_EJECUCION -> iniciar(evento, datos, fechaODefecto(datos, ahora()));
                    default -> throw transicionNoPermitida();
                }
            }
            case PROGRAMADO -> {
                switch (efectivo) {
                    // Reprogramación: actualiza fecha, nombre, escenario y roles
                    case PROGRAMADO -> programar(evento, datos, datos.fechaInicio());
                    // Cancelación de la programación: se elimina la fecha
                    case BORRADOR -> {
                        evento.setEstado(EstadoEvento.BORRADOR);
                        evento.setFechaInicio(null);
                        copiarInformacion(evento, datos);
                    }
                    // Inicio: sin fecha nueva, se conserva la programada
                    case EN_EJECUCION -> iniciar(evento, datos,
                            datos.fechaInicio() != null ? datos.fechaInicio() : evento.getFechaInicio());
                    default -> throw transicionNoPermitida();
                }
            }
            case EN_EJECUCION -> {
                if (efectivo == EstadoEvento.FINALIZADO) {
                    // Cierre: deshabilita el check-in y guarda la fecha de recepción
                    evento.setEstado(EstadoEvento.FINALIZADO);
                    evento.setFechaCierre(ahora());
                } else if (efectivo != EstadoEvento.EN_EJECUCION) {
                    throw transicionNoPermitida();
                }
            }
            case FINALIZADO -> {
                // FINALIZADO es definitivo: solo se acepta repetirlo
                if (efectivo != EstadoEvento.FINALIZADO) {
                    throw transicionNoPermitida();
                }
            }
        }
    }

    private void programar(Evento evento, EstadoValidado datos, Instant fechaInicio) {
        evento.setEstado(EstadoEvento.PROGRAMADO);
        evento.setFechaInicio(fechaInicio);
        copiarInformacion(evento, datos);
    }

    private void iniciar(Evento evento, EstadoValidado datos, Instant fechaInicio) {
        evento.setEstado(EstadoEvento.EN_EJECUCION);
        evento.setFechaInicio(fechaInicio);
        copiarInformacion(evento, datos);
    }

    /** Nombre, escenario y roles convocados, que son informativos y se guardan tal cual (RN-MS04-24) */
    private static void copiarInformacion(Evento evento, EstadoValidado datos) {
        evento.setNombre(datos.nombre());
        evento.setEscenarioEdificios(datos.edificios());
        evento.setEscenarioZonas(datos.zonas());
        evento.setRolesConvocados(datos.roles());
    }

    /**
     * Fecha/hora de recepción, con la precisión de PostgreSQL (microsegundos): así la respuesta trae exactamente
     * lo que queda guardado
     */
    private Instant ahora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant fechaODefecto(EstadoValidado datos, Instant ahora) {
        return datos.fechaInicio() != null ? datos.fechaInicio() : ahora;
    }

    private static ApiException transicionNoPermitida() {
        return ApiException.conflicto(TRANSICION_NO_PERMITIDA);
    }

    /** Detalle de auditoría: el servicio de origen y los estados, o el código del error (RNF-MS04-17) */
    private static String detalle(String servicio, String cambio) {
        String origen = servicio == null ? "desconocido" : servicio.trim().toLowerCase();
        return "origen=" + origen + " " + cambio;
    }
}
