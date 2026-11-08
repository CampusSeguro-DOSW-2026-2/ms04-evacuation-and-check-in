package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-05 Consultar el propio estado vigente (Jira KAIR-576, historia KAIR-595).
 *
 * Pendiente de implementar: responde 501 hasta que se complete la lógica
 */
@Service
public class EstadoPropioService {

    /**
     * Estado vigente del usuario en el evento: su check-in más reciente, sin los anteriores (RN-MS04-06 y
     * RN-MS04-08). Funciona con el evento en cualquier estado (RN-MS04-22). 404 si el evento no existe o el
     * usuario no tiene check-in en él
     *
     * @param eventoId evento
     * @param requester identidad del solicitante; solo puede consultar su propio estado
     * @return el estado vigente con el estado de envío de su solicitud, si aplica
     */
    public CheckinResponse consultar(UUID eventoId, RequesterIdentity requester) {
        throw ApiException.noImplementado("RF-MS04-05 consultar el propio estado vigente");
    }
}
