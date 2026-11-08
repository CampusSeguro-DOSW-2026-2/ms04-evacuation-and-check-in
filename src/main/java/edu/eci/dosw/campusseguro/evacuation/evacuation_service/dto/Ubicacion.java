package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import jakarta.validation.constraints.NotNull;

/**
 * Ubicación de MS02 donde se requiere la asistencia (RN-MS04-09). El id es un UUID, como los demás ids de MS02
 *
 * @param tipo EDIFICIO, ESPACIO o PUNTO_ENCUENTRO
 * @param id id del elemento en MS02
 */
public record Ubicacion(
        @NotNull(message = "Indique dónde necesita la asistencia.") TipoUbicacion tipo,
        @NotNull(message = "Seleccione el lugar donde necesita la asistencia.") UUID id) {
}
