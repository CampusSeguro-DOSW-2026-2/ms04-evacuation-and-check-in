package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

/**
 * Resultado de recibir el estado de un evento: el evento almacenado y si era nuevo
 *
 * @param evento evento almacenado
 * @param creado true si el evento era nuevo (201); false si ya existía, se actualizó o se repitió (200)
 */
public record ResultadoEstadoEvento(EventoInterno evento, boolean creado) {
}
