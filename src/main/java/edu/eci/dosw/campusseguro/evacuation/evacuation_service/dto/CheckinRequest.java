package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo del reporte de estado (F-01, RF-MS04-02 y RF-MS04-03). Un campo que no está aquí se rechaza con 400
 * (RG-04): en especial un id de usuario, que solo llega por X-User-Id (RN-MS04-06)
 *
 * El punto de encuentro es obligatorio con SAFE y prohibido con los demás estados; la ubicación es obligatoria
 * con NEEDS_ASSISTANCE y prohibida con los demás (RG-05). Esas reglas dependen del estado y las valida el service
 *
 * @param estado SAFE, EVACUATING, NEEDS_ASSISTANCE u OFF_CAMPUS
 * @param puntoEncuentroId punto de encuentro de MS02 (UUID), solo con SAFE
 * @param ubicacion ubicación de MS02, solo con NEEDS_ASSISTANCE
 */
public record CheckinRequest(
        @NotNull(message = "Seleccione un estado válido.") EstadoCheckin estado,
        @JsonProperty("punto_encuentro_id") UUID puntoEncuentroId,
        @Valid Ubicacion ubicacion) {
}
