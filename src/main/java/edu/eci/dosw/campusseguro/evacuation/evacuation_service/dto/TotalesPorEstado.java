package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Personas por cada uno de los cuatro estados vigentes. Siempre incluye los cuatro, con 0 si no hay personas
 * (RN-MS04-13)
 *
 * @param safe personas en SAFE
 * @param evacuating personas en EVACUATING
 * @param needsAssistance personas en NEEDS_ASSISTANCE
 * @param offCampus personas en OFF_CAMPUS
 */
public record TotalesPorEstado(
        @JsonProperty("SAFE") long safe,
        @JsonProperty("EVACUATING") long evacuating,
        @JsonProperty("NEEDS_ASSISTANCE") long needsAssistance,
        @JsonProperty("OFF_CAMPUS") long offCampus) {

    /**
     * Arma los totales a partir de los conteos que devuelve la base de datos, completando con 0 los estados sin
     * personas
     *
     * @param conteos total de personas por código de estado; puede omitir estados
     * @return los cuatro totales
     */
    public static TotalesPorEstado de(Map<String, Long> conteos) {
        return new TotalesPorEstado(
                conteos.getOrDefault("SAFE", 0L),
                conteos.getOrDefault("EVACUATING", 0L),
                conteos.getOrDefault("NEEDS_ASSISTANCE", 0L),
                conteos.getOrDefault("OFF_CAMPUS", 0L));
    }

    /**
     * Suma de los cuatro estados: el total de personas con estado vigente
     *
     * @return el total de reportados
     */
    public long total() {
        return safe + evacuating + needsAssistance + offCampus;
    }
}
