package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

/**
 * Acciones de MS04 que se auditan en MS08 (RNF-MS04-17). El nombre va en MAYUSCULAS_CON_GUION_BAJO, como exigen
 * los Estándares
 */
public enum AuditAction {
    /** Primer check-in de un usuario en un evento */
    REGISTRO_CHECKIN,
    /** Cambio del estado vigente de un usuario; el detalle lleva el estado anterior y el nuevo */
    CAMBIO_ESTADO_CHECKIN,
    /** Envío de la solicitud de asistencia a MS05 */
    SOLICITUD_ASISTENCIA,
    /** Cambio de estado de un evento que informa MS06; el detalle lleva el servicio de origen y los estados */
    CAMBIO_ESTADO_EVENTO
}
