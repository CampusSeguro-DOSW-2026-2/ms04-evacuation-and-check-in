package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Personas a salvo en un punto de encuentro
 *
 * @param puntoEncuentroId punto de encuentro de MS02
 * @param total personas en SAFE en ese punto
 */
public record TotalPorPunto(
        @JsonProperty("punto_encuentro_id") UUID puntoEncuentroId,
        long total) {
}
