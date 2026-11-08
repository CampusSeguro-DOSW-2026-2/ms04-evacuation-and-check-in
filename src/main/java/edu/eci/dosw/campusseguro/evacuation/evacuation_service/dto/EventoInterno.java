package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;

/**
 * Evento almacenado, tal como lo devuelve la ruta interna de MS06 (camelCase). El estado es el efectivo y
 * {@code checkinHabilitado} indica si el check-in quedó habilitado (RF-MS04-11)
 *
 * @param eventoId id que asigna MS06
 * @param nombre nombre informativo
 * @param simulacro false es un evento inmediato
 * @param estado estado efectivo
 * @param fechaInicio fecha de inicio; null solo con BORRADOR
 * @param fechaCierre fecha en que MS04 recibió FINALIZADO; null en los demás estados
 * @param escenario edificios y zonas informativos
 * @param rolesConvocados roles a los que les aplica el evento
 * @param checkinHabilitado true solo con el evento EN_EJECUCION (RN-MS04-19)
 */
public record EventoInterno(
        UUID eventoId,
        String nombre,
        boolean simulacro,
        EstadoEvento estado,
        Instant fechaInicio,
        Instant fechaCierre,
        Escenario escenario,
        List<String> rolesConvocados,
        boolean checkinHabilitado) {
}
