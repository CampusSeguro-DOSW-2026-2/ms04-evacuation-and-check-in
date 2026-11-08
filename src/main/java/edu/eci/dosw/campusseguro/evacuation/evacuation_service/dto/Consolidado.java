package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;

/**
 * Consolidado del evento por estado y punto de encuentro (RF-MS04-06), calculado con los estados vigentes
 *
 * @param eventoId evento
 * @param estadoEvento estado del evento
 * @param totalReportados suma de los cuatro estados
 * @param totalesPorEstado personas por estado, siempre los cuatro
 * @param porPuntoEncuentro personas a salvo por punto de encuentro (solo SAFE)
 * @param calculadoEn fecha y hora en que se calculó
 */
public record Consolidado(
        @JsonProperty("evento_id") UUID eventoId,
        @JsonProperty("estado_evento") EstadoEvento estadoEvento,
        @JsonProperty("total_reportados") long totalReportados,
        @JsonProperty("totales_por_estado") TotalesPorEstado totalesPorEstado,
        @JsonProperty("por_punto_encuentro") List<TotalPorPunto> porPuntoEncuentro,
        @JsonProperty("calculado_en") Instant calculadoEn) {
}
