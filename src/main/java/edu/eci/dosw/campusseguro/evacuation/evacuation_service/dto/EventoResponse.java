package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;

/**
 * Evento tal como lo guarda MS04, en el contrato protegido (snake_case): lo devuelven los eventos activos
 * (RF-MS04-01) y la consulta de eventos (RF-MS04-13)
 *
 * @param eventoId id que asigna MS06
 * @param nombre nombre informativo
 * @param simulacro false es una emergencia o evento inmediato
 * @param estado estado del evento
 * @param fechaInicio fecha de inicio; null solo con BORRADOR
 * @param fechaCierre fecha de cierre; solo con FINALIZADO
 * @param escenario edificios y zonas informativos
 * @param rolesConvocados roles a los que les aplica el evento
 * @param checkinHabilitado true solo con el evento EN_EJECUCION (RN-MS04-19)
 */
public record EventoResponse(
        @JsonProperty("evento_id") UUID eventoId,
        String nombre,
        boolean simulacro,
        EstadoEvento estado,
        @JsonProperty("fecha_inicio") Instant fechaInicio,
        @JsonProperty("fecha_cierre") Instant fechaCierre,
        Escenario escenario,
        @JsonProperty("roles_convocados") List<String> rolesConvocados,
        @JsonProperty("checkin_habilitado") boolean checkinHabilitado) {
}
