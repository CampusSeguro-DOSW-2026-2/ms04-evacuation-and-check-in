package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * RF-MS04-13 Consultar eventos (Jira KAIR-582, historia KAIR-604). Solo lectura: un evento únicamente lo cambia
 * MS06 (RN-MS04-26), y esta clase no escribe nada. Los eventos se consultan en cualquier estado, tengan o no el
 * check-in habilitado (RN-MS04-22). Los eventos no son datos de personas ni tienen ubicación: el BRIGADISTA los ve
 * todos, sin filtro de zona.
 *
 * El permiso ({@code ms04:dashboard:read}) y la paginación los valida el controller antes de llegar aquí
 */
@Service
public class ConsultaEventoService {

    static final String EVENTO_NO_ENCONTRADO = "Evento no encontrado.";

    private final EventoRepository eventos;

    /**
     * @param eventos repositorio de eventos
     */
    public ConsultaEventoService(EventoRepository eventos) {
        this.eventos = eventos;
    }

    /**
     * Eventos en cualquier estado, con filtro opcional por estado, ordenados por fecha de inicio del más reciente
     * al más antiguo (los BORRADOR, sin fecha, al final) y luego por {@code creado_en} descendente
     *
     * @param estado filtro opcional por estado del evento
     * @param page número de página, desde 0
     * @param size tamaño de página, de 1 a 100
     * @return la página de eventos, que puede estar vacía (A1)
     */
    public PageResponse<EventoResponse> listar(EstadoEvento estado, int page, int size) {
        return PageResponse.from(eventos.consultar(estado == null ? null : estado.name(), PageRequest.of(page, size))
                .map(EventoMapper::aRespuesta));
    }

    /**
     * Un evento en cualquier estado, aunque su check-in esté deshabilitado
     *
     * @param eventoId id del evento; 404 si MS04 no lo conoce (A4)
     * @return el evento
     */
    public EventoResponse obtener(UUID eventoId) {
        return eventos.findById(eventoId)
                .map(EventoMapper::aRespuesta)
                .orElseThrow(() -> ApiException.noEncontrado(EVENTO_NO_ENCONTRADO));
    }
}
