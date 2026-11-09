package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.util.UUID;

/**
 * Punto de encuentro (zona segura) de MS02, con lo que MS04 necesita para validar un check-in Estoy a salvo
 * (RN-MS04-04)
 *
 * @param id id del punto de encuentro en MS02 (UUID)
 * @param codigo código legible del punto, p. ej. MP-H-01
 * @param nombre nombre del punto
 * @param activo true si su estado en MS02 es ACTIVO
 */
public record PuntoEncuentro(UUID id, String codigo, String nombre, boolean activo) {
}
