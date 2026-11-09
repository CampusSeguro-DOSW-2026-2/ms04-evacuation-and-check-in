package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Persona cuyo estado vigente es Requiero asistencia, con su ubicación y la solicitud en MS05 (RF-MS04-08)
 *
 * @param usuarioId usuario
 * @param nombre nombre completo que entrega MS01; null si MS01 no respondió (RN-MS04-25)
 * @param ubicacion ubicación reportada
 * @param fechaReporte fecha y hora de su check-in vigente
 * @param solicitudAsistencia id de la solicitud en MS05 y estado de su envío
 */
public record PersonaAsistencia(
        @JsonProperty("usuario_id") UUID usuarioId,
        String nombre,
        Ubicacion ubicacion,
        @JsonProperty("fecha_reporte") Instant fechaReporte,
        @JsonProperty("solicitud_asistencia") SolicitudAsistenciaResumen solicitudAsistencia) {
}
