package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

/**
 * Un check-in del histórico del evento: cada reporte de cada usuario, en orden cronológico (RF-MS04-09)
 *
 * @param checkinId id del check-in
 * @param usuarioId usuario
 * @param estado estado reportado
 * @param puntoEncuentroId punto de encuentro, solo con SAFE
 * @param ubicacion ubicación, solo con NEEDS_ASSISTANCE
 * @param registradoEn fecha y hora del reporte
 */
public record RegistroHistorial(
        @JsonProperty("checkin_id") UUID checkinId,
        @JsonProperty("usuario_id") UUID usuarioId,
        EstadoCheckin estado,
        @JsonProperty("punto_encuentro_id") UUID puntoEncuentroId,
        Ubicacion ubicacion,
        @JsonProperty("registrado_en") Instant registradoEn) {
}
