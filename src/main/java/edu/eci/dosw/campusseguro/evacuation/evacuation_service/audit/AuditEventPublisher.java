package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

/**
 * Envía eventos de auditoría a MS08. Nunca lanza excepciones: si MS08 falla, la respuesta al usuario no cambia y
 * el error solo queda en el log (Estándares, sección 12)
 */
public interface AuditEventPublisher {

    /**
     * Publica el evento de forma asíncrona, sin esperar el resultado y sin reintentos
     *
     * @param event evento de auditoría
     */
    void publish(AuditEvent event);
}
