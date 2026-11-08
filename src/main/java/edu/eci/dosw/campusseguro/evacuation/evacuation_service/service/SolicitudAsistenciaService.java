package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-04 Generar solicitud de asistencia en MS05 (Jira KAIR-575, historias KAIR-593 y KAIR-594).
 *
 * No es un endpoint: lo llama {@link CheckinService} cuando el estado es Requiero asistencia, y una tarea
 * periódica reenvía las pendientes. Pendiente de implementar: responde 501 hasta que se complete la lógica
 */
@Service
public class SolicitudAsistenciaService {

    /**
     * Crea la solicitud del check-in NEEDS_ASSISTANCE y la envía a MS05 por la ruta interna del Gateway, después
     * de confirmar la transacción del check-in (RN-MS04-12). Una solicitud por usuario y evento (RN-MS04-11): si
     * ya existe, se reutiliza. Si MS05 no responde, queda PENDIENTE_ENVIO; si la rechaza, RECHAZADA
     *
     * @param checkin check-in Requiero asistencia que originó la solicitud
     * @param requester usuario que reportó; su identidad se propaga a MS05
     * @param correlationId correlation id de la petición
     */
    public void solicitar(Checkin checkin, RequesterIdentity requester, String correlationId) {
        throw ApiException.noImplementado("RF-MS04-04 enviar la solicitud de asistencia a MS05");
    }

    /**
     * Reenvía a MS05 las solicitudes PENDIENTE_ENVIO, sin duplicarlas: el id de la solicitud viaja como llave de
     * idempotencia (RN-MS04-12). La frecuencia de la tarea que lo llama se configura por variable de entorno
     *
     * @return cuántas solicitudes se enviaron
     */
    public int reenviarPendientes() {
        throw ApiException.noImplementado("RF-MS04-04 reenviar las solicitudes pendientes");
    }
}
