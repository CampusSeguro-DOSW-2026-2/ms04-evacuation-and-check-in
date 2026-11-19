package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

/**
 * Nombre completo y correo de una persona, tal como los entrega MS01. MS04 no los guarda (RN-MS04-02)
 *
 * @param nombre nombres y apellidos separados por un espacio
 * @param correo correo institucional; null si MS01 no lo envió
 */
public record DatosUsuario(String nombre, String correo) {
}
