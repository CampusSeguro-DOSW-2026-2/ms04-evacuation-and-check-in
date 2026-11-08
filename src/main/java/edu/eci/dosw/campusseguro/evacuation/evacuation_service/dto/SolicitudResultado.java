package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Persona que reportó Requiero asistencia al menos una vez, con su solicitud (RF-MS04-10)
 *
 * @param usuarioId usuario
 * @param solicitudAsistencia id de la solicitud en MS05 y estado de su envío
 */
public record SolicitudResultado(
        @JsonProperty("usuario_id") UUID usuarioId,
        @JsonProperty("solicitud_asistencia") SolicitudAsistenciaResumen solicitudAsistencia) {
}
