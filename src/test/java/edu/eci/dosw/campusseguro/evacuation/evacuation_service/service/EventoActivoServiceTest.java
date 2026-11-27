package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * RF-MS04-01 Query active events, with the repository replaced by a mock: the search by the roles of the user and
 * the answer of each event in the order of the query
 */
class EventoActivoServiceTest {

    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");

    private EventoRepository events;
    private EventoActivoService service;

    /**
     * Creates the service with a mocked repository
     */
    @BeforeEach
    void setUp() {
        events = mock(EventoRepository.class);
        service = new EventoActivoService(events);
    }

    /**
     * Event in execution that convenes students
     *
     * @param name name of the event
     * @return the event
     */
    private static Evento active(String name) {
        Evento event = new Evento(UUID.randomUUID());
        event.setNombre(name);
        event.setSimulacro(true);
        event.setEstado(EstadoEvento.EN_EJECUCION);
        event.setFechaInicio(Instant.parse("2026-11-20T15:00:00Z"));
        event.setRolesConvocados(List.of("ESTUDIANTE"));
        return event;
    }

    /**
     * The events are searched by the roles of the user in alphabetical order
     */
    @Test
    void searchesByTheRolesOfTheUserInAlphabeticalOrder() {
        RequesterIdentity user = TestIdentities.identity(USER, "ESTUDIANTE", "BRIGADISTA");

        service.activos(user);

        verify(events).buscarActivosPorRoles(List.of("BRIGADISTA", "ESTUDIANTE"));
        verifyNoMoreInteractions(events);
    }

    /**
     * Each event becomes its answer keeping the order of the query
     */
    @Test
    void eachEventBecomesItsAnswerKeepingTheOrder() {
        Evento first = active("Primero");
        Evento second = active("Segundo");
        when(events.buscarActivosPorRoles(List.of("ESTUDIANTE"))).thenReturn(List.of(first, second));

        List<EventoResponse> answer = service.activos(TestIdentities.identity(USER, "ESTUDIANTE"));

        assertEquals(List.of(first.getId(), second.getId()), answer.stream()
                .map(EventoResponse::eventoId)
                .toList());
        assertEquals(List.of("Primero", "Segundo"), answer.stream()
                .map(EventoResponse::nombre)
                .toList());
        assertTrue(answer.stream().allMatch(EventoResponse::checkinHabilitado));
    }

    /**
     * Without active events the answer is an empty list
     */
    @Test
    void withoutActiveEventsReturnsAnEmptyList() {
        when(events.buscarActivosPorRoles(List.of("ESTUDIANTE"))).thenReturn(List.of());

        assertTrue(service.activos(TestIdentities.identity(USER, "ESTUDIANTE")).isEmpty());
    }
}
