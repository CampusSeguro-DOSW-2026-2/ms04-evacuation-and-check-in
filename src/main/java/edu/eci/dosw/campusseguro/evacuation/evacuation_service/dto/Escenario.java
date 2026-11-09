package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edificios y zonas del evento tal como los envía MS06. Son informativos: MS04 los guarda y los devuelve sin
 * validarlos contra MS02 ni usarlos para limitar quién puede reportar (RN-MS04-24)
 *
 * @param edificios nombres de edificios, cada uno de 1 a 50 caracteres
 * @param zonas nombres de zonas, cada una de 1 a 50 caracteres
 */
public record Escenario(
        List<@NotBlank(message = "Edificio no válido (1 a 50 caracteres).")
             @Size(max = 50, message = "Edificio no válido (1 a 50 caracteres).") String> edificios,
        List<@NotBlank(message = "Zona no válida (1 a 50 caracteres).")
             @Size(max = 50, message = "Zona no válida (1 a 50 caracteres).") String> zonas) {

    /**
     * Las listas ausentes se toman como vacías (F-05)
     */
    public Escenario {
        edificios = edificios == null ? List.of() : List.copyOf(edificios);
        zonas = zonas == null ? List.of() : List.copyOf(zonas);
    }
}
