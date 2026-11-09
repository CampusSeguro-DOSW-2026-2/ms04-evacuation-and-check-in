package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;

/**
 * Cuerpo del reporte de estado (F-01, RF-MS04-02 y RF-MS04-03). Un campo que no está aquí se rechaza con 400
 * (RG-04): en especial un id de usuario, que solo llega por X-User-Id (RN-MS04-06)
 *
 * Las reglas que dependen del estado las valida {@code CheckinValidator} en el service, para auditar cada intento
 * fallido. Un estado fuera del catálogo se lee como null y produce el mensaje del formulario
 *
 * @param estado SAFE, EVACUATING, NEEDS_ASSISTANCE u OFF_CAMPUS
 * @param puntoEncuentroId punto de encuentro de MS02 (UUID), solo con SAFE
 * @param ubicacion ubicación de MS02, solo con NEEDS_ASSISTANCE
 */
public record CheckinRequest(
        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) EstadoCheckin estado,
        @JsonProperty("punto_encuentro_id") UUID puntoEncuentroId,
        Ubicacion ubicacion) {
}
