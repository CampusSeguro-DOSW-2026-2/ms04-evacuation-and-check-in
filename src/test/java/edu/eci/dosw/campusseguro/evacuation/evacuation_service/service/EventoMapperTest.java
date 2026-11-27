package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoInterno;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;

/**
 * Conversion of a stored event into the answer of the protected contract and of the internal route of MS06
 */
class EventoMapperTest {

    private static final UUID ID = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final Instant START = Instant.parse("2026-11-20T15:00:00Z");

    /**
     * Event with its scenario and roles in a state
     *
     * @param state state of the event
     * @return the event, without start date when it is a draft
     */
    private static Evento event(EstadoEvento state) {
        Evento event = new Evento(ID);
        event.setNombre("Simulacro de evacuación bloque A");
        event.setSimulacro(true);
        event.setEstado(state);
        if (state != EstadoEvento.BORRADOR) {
            event.setFechaInicio(START);
        }
        event.setRolesConvocados(List.of("ESTUDIANTE", "PROFESOR"));
        event.setEscenarioEdificios(List.of("Bloque A"));
        event.setEscenarioZonas(List.of("Zona norte"));
        return event;
    }

    /**
     * Every field of the event is copied
     */
    @Test
    void copiesEveryFieldOfTheEvent() {
        EventoResponse answer = EventoMapper.aRespuesta(event(EstadoEvento.EN_EJECUCION));

        assertEquals(ID, answer.eventoId());
        assertEquals("Simulacro de evacuación bloque A", answer.nombre());
        assertTrue(answer.simulacro());
        assertEquals(EstadoEvento.EN_EJECUCION, answer.estado());
        assertEquals(START, answer.fechaInicio());
        assertNull(answer.fechaCierre());
        assertEquals(List.of("Bloque A"), answer.escenario().edificios());
        assertEquals(List.of("Zona norte"), answer.escenario().zonas());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), answer.rolesConvocados());
    }

    /**
     * The check-in is enabled only with the event in execution
     */
    @Test
    void theCheckinIsEnabledOnlyInExecution() {
        assertTrue(EventoMapper.aRespuesta(event(EstadoEvento.EN_EJECUCION)).checkinHabilitado());
        for (EstadoEvento state : List.of(EstadoEvento.BORRADOR, EstadoEvento.PROGRAMADO,
                EstadoEvento.FINALIZADO)) {
            assertFalse(EventoMapper.aRespuesta(event(state)).checkinHabilitado(), state.name());
        }
    }

    /**
     * A draft has no start date
     */
    @Test
    void aDraftHasNoStartDate() {
        assertNull(EventoMapper.aRespuesta(event(EstadoEvento.BORRADOR)).fechaInicio());
    }

    /**
     * A finished event keeps its closing date
     */
    @Test
    void aFinishedEventKeepsItsClosingDate() {
        Evento finished = event(EstadoEvento.FINALIZADO);
        finished.setFechaCierre(Instant.parse("2026-11-20T15:45:00Z"));

        assertEquals(Instant.parse("2026-11-20T15:45:00Z"), EventoMapper.aRespuesta(finished).fechaCierre());
    }

    /**
     * A scenario without data is answered with empty lists
     */
    @Test
    void aScenarioWithoutDataHasEmptyLists() {
        Evento event = event(EstadoEvento.EN_EJECUCION);
        event.setEscenarioEdificios(null);
        event.setEscenarioZonas(null);

        EventoResponse answer = EventoMapper.aRespuesta(event);

        assertTrue(answer.escenario().edificios().isEmpty());
        assertTrue(answer.escenario().zonas().isEmpty());
    }

    /**
     * The answer does not share its lists with the entity
     */
    @Test
    void theAnswerDoesNotShareTheListsWithTheEntity() {
        Evento event = event(EstadoEvento.EN_EJECUCION);

        EventoResponse answer = EventoMapper.aRespuesta(event);
        event.setRolesConvocados(List.of("COORDINADOR"));

        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), answer.rolesConvocados());
        assertThrows(UnsupportedOperationException.class, () -> answer.rolesConvocados().add("X"));
    }

    /**
     * The answer of the internal route of MS06 carries the same data of the event
     */
    @Test
    void theInternalAnswerCarriesTheSameData() {
        EventoInterno answer = EventoMapper.aInterno(event(EstadoEvento.EN_EJECUCION));

        assertEquals(ID, answer.eventoId());
        assertEquals(START, answer.fechaInicio());
        assertEquals(List.of("Bloque A"), answer.escenario().edificios());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), answer.rolesConvocados());
        assertTrue(answer.checkinHabilitado());
    }
}
