package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Punto único para registrar la auditoría desde los services: arma el {@link AuditEvent} y lo entrega al
 * publicador. Nunca lanza excepciones, así que una falla de auditoría no cambia la respuesta al usuario
 */
@Component
public class AuditRecorder {

    public static final String CHECKIN_RESOURCE_TYPE = "checkin";
    public static final String EVENT_RESOURCE_TYPE = "evento";

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditEventPublisher publisher;
    private final Clock clock;

    public AuditRecorder(AuditEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Audita una acción hecha por un usuario identificado; su identidad viaja a MS08
     *
     * @param action acción realizada
     * @param result EXITO o FALLO
     * @param requester usuario que ejecuta la acción
     * @param resource {@code checkin:<id>} o {@code evento:<id>}
     * @param detail código genérico del motivo o dato adicional; puede ser null
     * @param correlationId correlation id de la petición
     */
    public void recordAs(AuditAction action, AuditResult result, RequesterIdentity requester, String resource,
            String detail, String correlationId) {
        publish(new AuditEvent(action, result, requester.userId(), requester.rolesComoCabecera(), detail,
                correlationId, clock.instant(), resource));
    }

    /**
     * Audita una acción sin usuario, p. ej. el cambio de estado de un evento que informa MS06
     *
     * @param action acción realizada
     * @param result EXITO o FALLO
     * @param resource {@code checkin:<id>} o {@code evento:<id>}
     * @param detail código genérico del motivo o dato adicional; puede ser null
     * @param correlationId correlation id de la petición
     */
    public void recordSystem(AuditAction action, AuditResult result, String resource, String detail,
            String correlationId) {
        publish(new AuditEvent(action, result, null, null, detail, correlationId, clock.instant(), resource));
    }

    /**
     * Recurso afectado por un check-in
     *
     * @param checkinId id del check-in; null si el check-in no llegó a crearse
     * @return {@code checkin:<id>} o null
     */
    public static String checkinResource(UUID checkinId) {
        return checkinId == null ? null : CHECKIN_RESOURCE_TYPE + ":" + checkinId;
    }

    /**
     * Recurso afectado por un evento (también el de un check-in que no llegó a crearse)
     *
     * @param eventoId id del evento
     * @return {@code evento:<id>} o null
     */
    public static String eventResource(UUID eventoId) {
        return eventoId == null ? null : EVENT_RESOURCE_TYPE + ":" + eventoId;
    }

    private void publish(AuditEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException e) {
            log.error("Falló la emisión del evento de auditoría {}/{}: {}", event.accion(), event.resultado(),
                    e.toString());
        }
    }
}
