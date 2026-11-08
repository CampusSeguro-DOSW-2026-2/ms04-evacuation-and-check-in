package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

/**
 * Check-in aceptado, o estado vigente del usuario en el evento (RF-MS04-02, 03 y 05)
 *
 * @param id id del check-in (el más reciente del usuario en el evento)
 * @param eventoId evento
 * @param estado estado reportado
 * @param puntoEncuentroId punto de encuentro de MS02, solo con SAFE
 * @param ubicacion ubicación, solo con NEEDS_ASSISTANCE
 * @param solicitudAsistencia estado de la solicitud, solo con NEEDS_ASSISTANCE
 * @param fechaHora fecha y hora de registro del check-in
 */
public record CheckinResponse(
        UUID id,
        @JsonProperty("evento_id") UUID eventoId,
        EstadoCheckin estado,
        @JsonProperty("punto_encuentro_id") UUID puntoEncuentroId,
        Ubicacion ubicacion,
        @JsonProperty("solicitud_asistencia") SolicitudAsistenciaResumen solicitudAsistencia,
        @JsonProperty("fecha_hora") Instant fechaHora) {
}
