package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinsMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * RF-MS04-12 Entregar a MS06 el detalle de check-ins del evento (Jira KAIR-581, historia KAIR-603).
 *
 * Pendiente de implementar: responde 501 hasta que se complete la lógica
 */
@Service
public class DetalleCheckinsService {

    /**
     * Estado vigente de cada persona en el evento (su check-in más reciente), del más antiguo al más reciente, con
     * filtro opcional por estado. Los nombres salen de MS01; si MS01 no responde, {@code nombre} es null
     * (RN-MS04-25). {@code zonaId} es el punto de encuentro, solo con SAFE. Funciona con el evento en cualquier
     * estado
     *
     * @param eventoId evento; 404 si no existe
     * @param estado filtro opcional por estado vigente
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @return la página en el formato acordado con MS06
     */
    public DetalleCheckinsMs06 detalle(UUID eventoId, EstadoCheckin estado, int page, int size) {
        throw ApiException.noImplementado("RF-MS04-12 detalle de check-ins para MS06");
    }
}
