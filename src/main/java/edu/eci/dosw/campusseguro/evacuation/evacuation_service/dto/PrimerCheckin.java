package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fecha y hora del primer check-in de un usuario, para que MS06 y MS08 calculen sus indicadores (RF-MS04-10)
 *
 * @param usuarioId usuario
 * @param primerCheckinEn fecha y hora de su primer check-in en el evento
 */
public record PrimerCheckin(
        @JsonProperty("usuario_id") UUID usuarioId,
        @JsonProperty("primer_checkin_en") Instant primerCheckinEn) {
}
