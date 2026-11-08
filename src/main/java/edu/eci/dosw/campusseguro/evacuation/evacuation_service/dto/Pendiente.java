package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Persona de la población objetivo que todavía no reportó ninguno de los cuatro estados (RF-MS04-07)
 *
 * @param usuarioId usuario
 * @param nombre nombre completo que entrega MS01; null si MS01 no entregó el dato (RN-MS04-25)
 */
public record Pendiente(
        @JsonProperty("usuario_id") UUID usuarioId,
        String nombre) {
}
