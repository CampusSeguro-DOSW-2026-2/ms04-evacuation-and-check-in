package edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity;

/**
 * Estados que reporta una persona en el check-in (RN-MS04-03)
 */
public enum EstadoCheckin {
    /** Estoy a salvo: exige punto de encuentro */
    SAFE,
    /** Me estoy evacuando */
    EVACUATING,
    /** Requiero asistencia: exige ubicación y genera una solicitud en MS05 */
    NEEDS_ASSISTANCE,
    /** No estoy en el campus */
    OFF_CAMPUS
}
