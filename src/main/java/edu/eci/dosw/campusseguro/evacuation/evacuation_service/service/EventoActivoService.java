package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * RF-MS04-01 Consultar eventos activos (Jira KAIR-572, historia KAIR-589).
 *
 * Pendiente de implementar: las operaciones responden 501 hasta que se complete la lógica
 */
@Service
public class EventoActivoService {

    /**
     * Eventos EN_EJECUCION que le aplican al usuario: los que convocan alguno de sus roles, ordenados por fecha
     * de inicio. Lista vacía si no hay ninguno (RN-MS04-01). Usa
     * {@code EventoRepository#buscarActivosPorRoles(requester.nombresDeRoles())}
     *
     * @param requester identidad del solicitante (X-User-Id y X-User-Role)
     * @return los eventos activos que le aplican
     */
    public List<EventoResponse> activos(RequesterIdentity requester) {
        throw ApiException.noImplementado("RF-MS04-01 consultar eventos activos");
    }
}
