package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

/**
 * Estado vigente de una persona en el evento, en el formato acordado con MS06 (camelCase)
 *
 * @param usuarioId usuario
 * @param nombre nombre completo que entrega MS01; null si MS01 no respondió (RN-MS04-25)
 * @param estado estado vigente
 * @param zonaId id del punto de encuentro de MS02; solo con SAFE, null en los demás estados
 * @param registradoEn fecha y hora del check-in vigente
 */
public record DetalleCheckinMs06(
        UUID usuarioId,
        String nombre,
        EstadoCheckin estado,
        UUID zonaId,
        Instant registradoEn) {
}
