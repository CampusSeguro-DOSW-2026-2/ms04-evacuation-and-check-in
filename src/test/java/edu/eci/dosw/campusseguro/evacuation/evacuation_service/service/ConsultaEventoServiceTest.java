package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * RF-MS04-13 Query events, with the repository replaced by a mock: the list in any state with the optional state
 * filter and the pagination of the standards, and one event by its id even with the check-in disabled
 */
@ExtendWith(MockitoExtension.class)
class ConsultaEventoServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");

    @Mock
    private EventoRepository events;

    @InjectMocks
    private ConsultaEventoService service;

    /**
     * Stored event in a state
     *
     * @param state state of the event
     * @return the event
     */
    private static Evento event(EstadoEvento state) {
        Evento event = new Evento(EVENT);
        event.setNombre("Simulacro bloque B");
        event.setSimulacro(true);
        event.setEstado(state);
        event.setFechaInicio(Instant.parse("2026-11-20T15:00:00Z"));
        event.setRolesConvocados(List.of("ESTUDIANTE"));
        return event;
    }

    /**
     * Without filter every state is queried and the page keeps the pagination of the standards
     */
    @Test
    void withoutFilterEveryStateIsListed() {
        when(events.consultar(null, PageRequest.of(0, 20))).thenReturn(new PageImpl<>(
                List.of(event(EstadoEvento.FINALIZADO)), PageRequest.of(0, 20), 1));

        PageResponse<EventoResponse> page = service.listar(null, 0, 20);

        assertEquals(1, page.totalElementos());
        assertEquals(1, page.totalPaginas());
        assertEquals(EstadoEvento.FINALIZADO, page.contenido().get(0).estado());
    }

    /**
     * The state filter is sent to the query with its code, and a page without events is an empty list
     */
    @Test
    void theStateFilterIsSentWithItsCode() {
        when(events.consultar("PROGRAMADO", PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        PageResponse<EventoResponse> page = service.listar(EstadoEvento.PROGRAMADO, 2, 5);

        assertEquals(List.of(), page.contenido());
        assertEquals(2, page.page());
        assertEquals(5, page.size());
    }

    /**
     * An event is returned by its id in any state, also with the check-in enabled
     */
    @Test
    void anEventIsReturnedByItsId() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));

        EventoResponse event = service.obtener(EVENT);

        assertEquals(EVENT, event.eventoId());
        assertTrue(event.checkinHabilitado());
    }

    /**
     * An event that MS04 does not know is not found
     */
    @Test
    void anUnknownEventIsNotFound() {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.obtener(EVENT));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(ConsultaEventoService.EVENTO_NO_ENCONTRADO, error.getMessage());
    }
}
