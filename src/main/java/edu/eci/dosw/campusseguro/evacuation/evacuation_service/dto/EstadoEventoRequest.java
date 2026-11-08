package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo que envía MS06 cada vez que programa, reprograma, cancela la programación, inicia o finaliza un evento
 * (F-05, RF-MS04-11). Los nombres de los campos son los acordados con MS06 (camelCase)
 *
 * Las reglas que dependen del estado las valida el service: {@code fechaInicio} es obligatoria con PROGRAMADO,
 * no se envía con BORRADOR y es opcional con EN_EJECUCION y FINALIZADO; los roles convocados son solo los de la
 * comunidad, sin repetidos; {@code eventoId} debe ser igual al de la ruta
 *
 * @param eventoId id que asigna MS06, igual al de la ruta
 * @param nombre nombre informativo, de 1 a 150 caracteres
 * @param simulacro false es un evento inmediato y en ejecución
 * @param estado BORRADOR, PROGRAMADO, EN_EJECUCION o FINALIZADO
 * @param fechaInicio fecha de inicio ISO-8601 con desfase horario; se guarda en UTC
 * @param escenario edificios y zonas informativos
 * @param rolesConvocados roles a los que les aplica el evento
 */
public record EstadoEventoRequest(
        @NotNull(message = "Evento no válido.") UUID eventoId,
        @NotBlank(message = "El nombre del evento es obligatorio (1 a 150 caracteres).")
        @Size(max = 150, message = "El nombre del evento es obligatorio (1 a 150 caracteres).") String nombre,
        @NotNull(message = "Indique si el evento es un simulacro.") Boolean simulacro,
        @NotNull(message = "Estado de evento no válido.") EstadoEvento estado,
        OffsetDateTime fechaInicio,
        @Valid Escenario escenario,
        @NotEmpty(message = "Indique al menos un rol convocado.") List<String> rolesConvocados) {
}
