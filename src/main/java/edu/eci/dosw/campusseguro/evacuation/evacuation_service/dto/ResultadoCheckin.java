package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

/**
 * Resultado de registrar o actualizar un check-in: la respuesta y si el usuario no tenía check-in en el evento
 *
 * @param checkin respuesta del check-in
 * @param creado true si es el primer check-in del usuario en el evento (201); false si es un cambio o el mismo
 *        estado (200)
 */
public record ResultadoCheckin(CheckinResponse checkin, boolean creado) {
}
