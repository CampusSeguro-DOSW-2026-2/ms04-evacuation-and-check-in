package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.List;

/**
 * Respuesta de {@code GET /chi/eventos/{eventoId}/checkins} para MS06. Usa {@code page}, {@code size}, {@code total}
 * e {@code items}, el formato acordado con MS06, y no la paginación de los listados protegidos (RNF-MS04-16)
 *
 * @param page número de página, desde 0
 * @param size tamaño de página
 * @param total personas que cumplen el filtro, no los elementos de la página
 * @param items un elemento por persona con su estado vigente
 */
public record DetalleCheckinsMs06(int page, int size, long total, List<DetalleCheckinMs06> items) {
}
