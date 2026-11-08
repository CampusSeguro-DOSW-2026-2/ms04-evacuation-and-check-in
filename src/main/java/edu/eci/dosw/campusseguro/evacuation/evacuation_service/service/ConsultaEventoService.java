package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * RF-MS04-13 Consultar eventos (Jira KAIR-582, historia KAIR-604). Solo lectura: un evento únicamente lo cambia
 * MS06 (RN-MS04-26).
 *
 * Pendiente de implementar: las operaciones responden 501 hasta que se complete la lógica
 */
@Service
public class ConsultaEventoService {

    /**
     * Eventos en cualquier estado, con filtro opcional por estado, ordenados por fecha de inicio del más reciente
     * al más antiguo (los BORRADOR, sin fecha, al final) y luego por {@code creado_en} descendente
     *
     * @param estado filtro opcional por estado del evento
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @return la página de eventos, que puede estar vacía
     */
    public PageResponse<EventoResponse> listar(EstadoEvento estado, int page, int size) {
        throw ApiException.noImplementado("RF-MS04-13 listar eventos");
    }

    /**
     * Un evento en cualquier estado, aunque su check-in esté deshabilitado
     *
     * @param eventoId id del evento; 404 si MS04 no lo conoce
     * @return el evento
     */
    public EventoResponse obtener(UUID eventoId) {
        throw ApiException.noImplementado("RF-MS04-13 consultar un evento");
    }
}
