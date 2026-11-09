package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;

/**
 * Envío a MS05 de la solicitud de asistencia que origina un check-in Requiero asistencia (RF-MS04-04)
 */
public interface SolicitudAsistenciaClient {

    /**
     * Pide a MS05 que cree la solicitud. Nunca lanza excepciones: la indisponibilidad de MS05 no puede impedir el
     * check-in (RN-MS04-12), así que cualquier falla vuelve como {@link ResultadoEnvioMs05.Tipo#NO_DISPONIBLE}
     *
     * @param solicitud solicitud de MS04; su id viaja como llave de idempotencia para que un reenvío no la duplique
     * @param rolesUsuario roles del usuario: con ellos se propagan X-User-Id y X-User-Role juntos; null en el reenvío
     *        periódico, que no propaga identidad (el Gateway exige ambas o ninguna, RF-GW-08)
     * @param correlationId correlation id de la petición, o uno nuevo en el reenvío
     * @return cómo terminó el envío
     */
    ResultadoEnvioMs05 enviar(SolicitudAsistencia solicitud, String rolesUsuario, String correlationId);
}
