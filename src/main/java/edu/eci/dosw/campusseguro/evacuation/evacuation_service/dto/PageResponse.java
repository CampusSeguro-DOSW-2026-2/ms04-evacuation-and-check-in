package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Paginación de los Estándares (sección 9): {@code page} desde 0 y {@code size} de 1 a 100, con los nombres
 * {@code contenido}, {@code total_elementos} y {@code total_paginas}
 *
 * @param contenido elementos de la página
 * @param page número de página, desde 0
 * @param size tamaño de página
 * @param totalElementos total de elementos
 * @param totalPaginas total de páginas
 * @param <T> tipo de los elementos
 */
public record PageResponse<T>(
        List<T> contenido,
        int page,
        int size,
        @JsonProperty("total_elementos") long totalElementos,
        @JsonProperty("total_paginas") int totalPaginas) {

    /**
     * Convierte una página de Spring Data
     *
     * @param page página de Spring Data
     * @param <T> tipo de los elementos
     * @return la respuesta paginada
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    /**
     * Arma la respuesta cuando la página se calcula a mano (p. ej. los pendientes)
     *
     * @param contenido elementos de la página
     * @param page número de página, desde 0
     * @param size tamaño de página
     * @param totalElementos total de elementos
     * @param <T> tipo de los elementos
     * @return la respuesta paginada con el total de páginas calculado
     */
    public static <T> PageResponse<T> of(List<T> contenido, int page, int size, long totalElementos) {
        int totalPaginas = (int) ((totalElementos + size - 1) / size);
        return new PageResponse<>(contenido, page, size, totalElementos, totalPaginas);
    }
}
