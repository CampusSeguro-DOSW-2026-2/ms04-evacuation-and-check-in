package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;

/**
 * Ubicación de MS02 donde se requiere la asistencia (RN-MS04-09). El id es un UUID, como los demás ids de MS02. Que
 * ambos campos vengan con Requiero asistencia lo valida {@code CheckinValidator}; un tipo fuera del catálogo se lee
 * como null
 *
 * @param tipo EDIFICIO, ESPACIO o PUNTO_ENCUENTRO
 * @param id id del elemento en MS02
 */
public record Ubicacion(
        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) TipoUbicacion tipo,
        UUID id) {
}
