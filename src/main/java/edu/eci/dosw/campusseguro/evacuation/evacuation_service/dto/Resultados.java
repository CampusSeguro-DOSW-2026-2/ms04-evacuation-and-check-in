package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;

/**
 * Datos de check-in del evento para el organizador, el auditor, MS06 y MS08 (RF-MS04-10). MS04 no calcula
 * indicadores (RN-MS04-18)
 *
 * @param eventoId evento
 * @param simulacro false es una emergencia o evento inmediato
 * @param estadoEvento estado del evento
 * @param fechaInicio fecha de inicio
 * @param fechaCierre fecha de cierre
 * @param finales true si el evento está FINALIZADO; false si los resultados son parciales
 * @param poblacionObjetivo población objetivo; null si MS01 no la entregó
 * @param totalReportados personas con estado vigente
 * @param totalPendientes personas que no reportaron; null si MS01 no entregó la población objetivo
 * @param totalesPorEstado personas por estado vigente
 * @param porPuntoEncuentro personas a salvo por punto de encuentro
 * @param solicitudesAsistencia personas que reportaron Requiero asistencia al menos una vez
 * @param primerosCheckins fecha y hora del primer check-in de cada usuario
 */
public record Resultados(
        @JsonProperty("evento_id") UUID eventoId,
        boolean simulacro,
        @JsonProperty("estado_evento") EstadoEvento estadoEvento,
        @JsonProperty("fecha_inicio") Instant fechaInicio,
        @JsonProperty("fecha_cierre") Instant fechaCierre,
        boolean finales,
        @JsonProperty("poblacion_objetivo") Long poblacionObjetivo,
        @JsonProperty("total_reportados") long totalReportados,
        @JsonProperty("total_pendientes") Long totalPendientes,
        @JsonProperty("totales_por_estado") TotalesPorEstado totalesPorEstado,
        @JsonProperty("por_punto_encuentro") List<TotalPorPunto> porPuntoEncuentro,
        @JsonProperty("solicitudes_asistencia") List<SolicitudResultado> solicitudesAsistencia,
        @JsonProperty("primeros_checkins") List<PrimerCheckin> primerosCheckins) {
}
