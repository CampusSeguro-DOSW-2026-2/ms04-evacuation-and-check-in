package edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Behavior of the entities that does not need a database: the check-in enabled only in execution, the cut of the
 * target population, the copies of the lists of an event and the dates set when a row is created or updated
 */
class EntitiesTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final Instant START = Instant.parse("2026-11-20T15:00:00Z");

    /**
     * Only an event in execution has the check-in enabled
     *
     * @param state state of the event
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void onlyAnEventInExecutionEnablesTheCheckin(EstadoEvento state) {
        Evento event = new Evento(EVENT);
        event.setEstado(state);

        assertEquals(state == EstadoEvento.EN_EJECUCION, event.isCheckinHabilitado());
    }

    /**
     * The population is cut at the start of the event, or at the given moment if the event has not started or has
     * no start date
     */
    @Test
    void thePopulationIsCutAtTheStartOfTheEvent() {
        Evento event = new Evento(EVENT);
        Instant later = START.plusSeconds(60);
        Instant earlier = START.minusSeconds(60);

        assertEquals(later, event.corteDePoblacion(later));
        event.setFechaInicio(START);
        assertEquals(START, event.corteDePoblacion(later));
        assertEquals(earlier, event.corteDePoblacion(earlier));
    }

    /**
     * The lists of an event are copies: a null list is stored as empty and a later change of the original list does
     * not change the event
     */
    @Test
    void theListsOfAnEventAreCopies() {
        Evento event = new Evento(EVENT);
        List<String> roles = new ArrayList<>(List.of("ESTUDIANTE"));

        event.setRolesConvocados(roles);
        event.setEscenarioEdificios(null);
        event.setEscenarioZonas(List.of("Zona norte"));
        roles.add("PROFESOR");

        assertEquals(List.of("ESTUDIANTE"), event.getRolesConvocados());
        assertEquals(List.of(), event.getEscenarioEdificios());
        assertEquals(List.of("Zona norte"), event.getEscenarioZonas());
        event.setRolesConvocados(null);
        event.setEscenarioZonas(null);
        assertEquals(List.of(), event.getRolesConvocados());
        assertEquals(List.of(), event.getEscenarioZonas());
    }

    /**
     * An event gets its creation and update dates when it is stored, and only the update date changes later
     */
    @Test
    void anEventGetsItsDatesWhenItIsStored() {
        Evento event = new Evento(EVENT);

        event.alCrear();
        Instant created = event.getCreadoEn();
        assertNotNull(created);
        assertEquals(created, event.getActualizadoEn());
        event.alActualizar();

        assertEquals(created, event.getCreadoEn());
        assertFalse(event.getActualizadoEn().isBefore(created));
    }

    /**
     * A request keeps the date of the check-in that originated it, takes the current one only without date, and
     * stores the identity of the user
     */
    @Test
    void aRequestKeepsTheDateOfItsCheckin() {
        SolicitudAsistencia withDate = new SolicitudAsistencia(UUID.randomUUID(), EVENT, USER, TipoUbicacion.ESPACIO,
                UUID.randomUUID(), START);
        SolicitudAsistencia withoutDate = new SolicitudAsistencia(UUID.randomUUID(), EVENT, USER,
                TipoUbicacion.ESPACIO, UUID.randomUUID(), null);

        withDate.alCrear();
        withoutDate.alCrear();
        withDate.setIdentidadSolicitante("ESTUDIANTE", "ms04:checkin:write");
        withDate.alActualizar();

        assertEquals(START, withDate.getReportadaEn());
        assertNotNull(withoutDate.getReportadaEn());
        assertNotNull(withDate.getActualizadoEn());
        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, withDate.getEstadoEnvio());
        assertEquals("ESTUDIANTE", withDate.getRolesSolicitante());
        assertEquals("ms04:checkin:write", withDate.getPermisosSolicitante());
    }

    /**
     * A new check-in keeps what the user reported and leaves to the database its id, number and date
     */
    @Test
    void aNewCheckinKeepsWhatTheUserReported() {
        UUID point = UUID.randomUUID();
        Checkin checkin = new Checkin(EVENT, USER, EstadoCheckin.SAFE, point, null, null);

        assertEquals(EVENT, checkin.getEventoId());
        assertEquals(USER, checkin.getUsuarioId());
        assertEquals(EstadoCheckin.SAFE, checkin.getEstado());
        assertEquals(point, checkin.getPuntoEncuentroId());
        assertNull(checkin.getId());
        assertNull(checkin.getNumero());
        assertNull(checkin.getRegistradoEn());
        assertNull(checkin.getUbicacionTipo());
        assertNull(checkin.getUbicacionId());
    }
}
