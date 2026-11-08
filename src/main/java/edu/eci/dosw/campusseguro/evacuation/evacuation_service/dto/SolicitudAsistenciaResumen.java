package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;

/**
 * Estado de la solicitud de asistencia de un check-in Requiero asistencia
 *
 * @param id id de la solicitud en MS05; null mientras no haya llegado
 * @param estadoEnvio ENVIADA, PENDIENTE_ENVIO o RECHAZADA
 */
public record SolicitudAsistenciaResumen(
        String id,
        @JsonProperty("estado_envio") EstadoEnvio estadoEnvio) {
}
