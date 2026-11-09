package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de auditoría que MS04 envía a MS08 (Estándares de Integración, sección 12). No lleva datos personales
 * distintos de los ids
 *
 * @param accion acción realizada
 * @param resultado EXITO o FALLO
 * @param usuarioId usuario que ejecuta la acción; null si no hay usuario (p. ej. el cambio lo informa MS06)
 * @param usuarioRoles roles del usuario con el formato de X-User-Role, para propagar su identidad; null si no hay
 * @param detalle código genérico del motivo o dato adicional; null si no aplica
 * @param correlationId correlation id de la petición
 * @param fecha fecha y hora de la acción
 * @param recursoAfectado {@code <tipo>:<id>}, p. ej. {@code checkin:9a1b...} o {@code evento:7c1d...}; null solo si
 *        no hay un recurso identificable
 */
public record AuditEvent(
        AuditAction accion,
        AuditResult resultado,
        UUID usuarioId,
        String usuarioRoles,
        String detalle,
        String correlationId,
        Instant fecha,
        String recursoAfectado) {

    /**
     * Exige los campos obligatorios
     */
    public AuditEvent {
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(fecha, "fecha");
    }
}
