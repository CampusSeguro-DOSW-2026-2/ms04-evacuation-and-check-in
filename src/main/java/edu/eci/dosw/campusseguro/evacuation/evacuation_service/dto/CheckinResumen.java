package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

/**
 * Estado vigente de una persona en el listado de personas por estado (RF-MS04-06)
 *
 * @param usuarioId usuario
 * @param nombre nombre completo que entrega MS01; null si MS01 no respondió (RN-MS04-25)
 * @param estado estado vigente
 * @param puntoEncuentroId punto de encuentro, solo con SAFE
 * @param fechaHora fecha y hora del check-in vigente
 */
public record CheckinResumen(
        @JsonProperty("usuario_id") UUID usuarioId,
        String nombre,
        EstadoCheckin estado,
        @JsonProperty("punto_encuentro_id") UUID puntoEncuentroId,
        @JsonProperty("fecha_hora") Instant fechaHora) {
}
