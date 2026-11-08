package edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity;

/**
 * Estados de un evento que informa MS06 (RN-MS04-19, RN-MS04-21). El check-in está habilitado si y solo si el
 * evento está EN_EJECUCION
 */
public enum EstadoEvento {
    BORRADOR,
    PROGRAMADO,
    EN_EJECUCION,
    FINALIZADO
}
