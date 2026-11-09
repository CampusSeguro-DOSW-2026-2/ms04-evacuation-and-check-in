package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;

/**
 * Cuerpo que envía MS06 cada vez que programa, reprograma, cancela la programación, inicia o finaliza un evento
 * (F-05, RF-MS04-11). Los nombres de los campos son los acordados con MS06 (camelCase)
 *
 * Todas las reglas de F-05 las valida {@code EstadoEventoValidator} en el service y no Bean Validation en el
 * controller, para que cada intento fallido quede auditado (RF-MS04-11, A1). Por eso un estado fuera del
 * catálogo llega como null y la fecha de inicio llega como texto: así su error también se audita y lleva el
 * mensaje de F-05
 *
 * @param eventoId id que asigna MS06, igual al de la ruta
 * @param nombre nombre informativo, de 1 a 150 caracteres
 * @param simulacro false es un evento inmediato y en ejecución
 * @param estado BORRADOR, PROGRAMADO, EN_EJECUCION o FINALIZADO; null si no es del catálogo
 * @param fechaInicio fecha de inicio ISO-8601 con desfase horario, como texto; se guarda en UTC
 * @param escenario edificios y zonas informativos
 * @param rolesConvocados roles a los que les aplica el evento
 */
public record EstadoEventoRequest(
        UUID eventoId,
        String nombre,
        Boolean simulacro,
        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) EstadoEvento estado,
        String fechaInicio,
        Escenario escenario,
        List<String> rolesConvocados) {
}
