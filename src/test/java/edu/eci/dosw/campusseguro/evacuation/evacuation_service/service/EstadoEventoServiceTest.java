package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditContext;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EstadoEventoRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Escenario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoInterno;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoEstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * RF-MS04-11 Receive the state of the event, with the repository and the audit replaced by mocks: creation of a new
 * event in any state, the transitions of RN-MS04-21, the start and closing dates, the idempotent repetitions, the
 * lock taken before reading the event and the audit of every change sent by MS06
 */
@ExtendWith(MockitoExtension.class)
class EstadoEventoServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String SERVICE = "ms06";
    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00.123456789Z");
    private static final Instant NOW_IN_MICROS = Instant.parse("2026-11-20T15:00:00.123456Z");
    private static final String SCHEDULED_DATE = "2026-11-25T10:00:00-05:00";
    private static final Instant SCHEDULED_INSTANT = Instant.parse("2026-11-25T15:00:00Z");
    private static final Instant PREVIOUS_DATE = Instant.parse("2026-11-21T14:00:00Z");
    private static final Instant PREVIOUS_CLOSING = Instant.parse("2026-11-21T16:00:00Z");

    @Mock
    private EventoRepository events;

    @Mock
    private AuditRecorder audit;

    @Mock
    private PlatformTransactionManager transactionManager;

    private EstadoEventoService service;

    /**
     * Creates the service with a fixed clock that has more precision than PostgreSQL
     */
    @BeforeEach
    void setUp() {
        service = new EstadoEventoService(events, audit, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager);
    }

    /**
     * Body that MS06 sends for the event of the route
     *
     * @param state state of the event
     * @param drill true for a drill
     * @param startDate start date in ISO-8601 with offset, or null
     * @return the body
     */
    private static EstadoEventoRequest body(EstadoEvento state, boolean drill, String startDate) {
        return new EstadoEventoRequest(EVENT, "Simulacro bloque B", drill, state, startDate,
                new Escenario(List.of("Bloque B"), List.of("Zona norte")), List.of("ESTUDIANTE", "PROFESOR"));
    }

    /**
     * Event already stored by MS04
     *
     * @param state stored state
     * @param drill stored type
     * @return the event with the dates that its state requires
     */
    private static Evento stored(EstadoEvento state, boolean drill) {
        Evento event = new Evento(EVENT);
        event.setNombre("Nombre anterior");
        event.setSimulacro(drill);
        event.setEstado(state);
        if (state != EstadoEvento.BORRADOR) {
            event.setFechaInicio(PREVIOUS_DATE);
        }
        if (state == EstadoEvento.FINALIZADO) {
            event.setFechaCierre(PREVIOUS_CLOSING);
        }
        event.setRolesConvocados(List.of("ESTUDIANTE"));
        return event;
    }

    /**
     * Makes the repository find no event, as with a new one, and return what it saves
     */
    private void newEvent() {
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.empty());
        when(events.saveAndFlush(any(Evento.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * Captures the context of the only audit record of the change
     *
     * @param result expected result
     * @param detail expected detail
     * @return the captured context
     */
    private AuditContext auditedAs(AuditResult result, String detail) {
        ArgumentCaptor<AuditContext> context = ArgumentCaptor.forClass(AuditContext.class);
        verify(audit).recordSystem(eq(AuditAction.CAMBIO_ESTADO_EVENTO), eq(result), eq("evento:" + EVENT),
                eq(detail), eq(CORRELATION), context.capture());
        return context.getValue();
    }

    /**
     * A new scheduled event is stored with its date in UTC and without check-in, answers as created and is audited
     * with the origin service and the states
     */
    @Test
    void aNewScheduledEventIsStoredWithoutCheckin() {
        newEvent();

        ResultadoEstadoEvento result = service.recibir(EVENT, body(EstadoEvento.PROGRAMADO, true, SCHEDULED_DATE),
                SERVICE, CORRELATION);

        assertTrue(result.creado());
        EventoInterno event = result.evento();
        assertEquals(EVENT, event.eventoId());
        assertEquals("Simulacro bloque B", event.nombre());
        assertEquals(EstadoEvento.PROGRAMADO, event.estado());
        assertEquals(SCHEDULED_INSTANT, event.fechaInicio());
        assertNull(event.fechaCierre());
        assertFalse(event.checkinHabilitado());
        assertEquals(List.of("Bloque B"), event.escenario().edificios());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), event.rolesConvocados());
        AuditContext context = auditedAs(AuditResult.EXITO, "origen=ms06 NUEVO->PROGRAMADO");
        assertEquals("SIMULACRO", context.tipoEvento());
        assertEquals("NUEVO", context.atributos().get("estado_anterior"));
        assertEquals("PROGRAMADO", context.atributos().get("estado_nuevo"));
        assertEquals("ms06", context.atributos().get("servicio_origen"));
    }

    /**
     * A new event may arrive in any state: without date it takes the reception time with the precision of the
     * database, except in BORRADOR, and FINALIZADO also stores the closing date
     *
     * @param state received state
     * @param hasStart true if the event keeps a start date
     * @param hasClosing true if the event keeps a closing date
     * @param enabled true if the check-in is enabled
     */
    @ParameterizedTest
    @CsvSource({
            "BORRADOR, false, false, false",
            "EN_EJECUCION, true, false, true",
            "FINALIZADO, true, true, false" })
    void aNewEventMayArriveInAnyState(EstadoEvento state, boolean hasStart, boolean hasClosing, boolean enabled) {
        newEvent();

        EventoInterno event = service.recibir(EVENT, body(state, false, null), SERVICE, CORRELATION).evento();

        assertEquals(state, event.estado());
        if (hasStart) {
            assertEquals(NOW_IN_MICROS, event.fechaInicio());
        } else {
            assertNull(event.fechaInicio());
        }
        if (hasClosing) {
            assertEquals(NOW_IN_MICROS, event.fechaCierre());
        } else {
            assertNull(event.fechaCierre());
        }
        assertEquals(enabled, event.checkinHabilitado());
    }

    /**
     * The lock of the event is taken before reading it, so two simultaneous calls on the same event are served one
     * after the other, even when the event does not exist yet
     */
    @Test
    void theEventIsLockedBeforeItIsRead() {
        newEvent();

        service.recibir(EVENT, body(EstadoEvento.EN_EJECUCION, true, null), SERVICE, CORRELATION);

        InOrder order = inOrder(events);
        order.verify(events).bloquearEvento(EVENT);
        order.verify(events).buscarParaActualizar(EVENT);
        order.verify(events).saveAndFlush(any(Evento.class));
    }

    /**
     * Every transition of RN-MS04-21 with the event already stored: the accepted ones leave the expected state and
     * the others answer 409 without modifying the event
     *
     * @param current stored state
     * @param received received state
     * @param expected resulting state, or CONFLICTO when the transition is rejected
     */
    @ParameterizedTest
    @CsvSource({
            "BORRADOR, BORRADOR, BORRADOR",
            "BORRADOR, PROGRAMADO, PROGRAMADO",
            "BORRADOR, EN_EJECUCION, EN_EJECUCION",
            "BORRADOR, FINALIZADO, CONFLICTO",
            "PROGRAMADO, PROGRAMADO, PROGRAMADO",
            "PROGRAMADO, BORRADOR, BORRADOR",
            "PROGRAMADO, EN_EJECUCION, EN_EJECUCION",
            "PROGRAMADO, FINALIZADO, CONFLICTO",
            "EN_EJECUCION, EN_EJECUCION, EN_EJECUCION",
            "EN_EJECUCION, FINALIZADO, FINALIZADO",
            "EN_EJECUCION, BORRADOR, CONFLICTO",
            "EN_EJECUCION, PROGRAMADO, CONFLICTO",
            "FINALIZADO, FINALIZADO, FINALIZADO",
            "FINALIZADO, BORRADOR, CONFLICTO",
            "FINALIZADO, PROGRAMADO, CONFLICTO",
            "FINALIZADO, EN_EJECUCION, CONFLICTO" })
    void everyTransitionAnswersAsRnMs0421Defines(EstadoEvento current, EstadoEvento received, String expected) {
        Evento event = stored(current, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));
        String startDate = null;
        if (received == EstadoEvento.PROGRAMADO) {
            startDate = SCHEDULED_DATE;
        }
        EstadoEventoRequest request = body(received, true, startDate);

        if ("CONFLICTO".equals(expected)) {
            when(events.findById(EVENT)).thenReturn(Optional.of(event));
            ApiException error = assertThrows(ApiException.class,
                    () -> service.recibir(EVENT, request, SERVICE, CORRELATION));
            assertEquals(ErrorCode.CONFLICT, error.getCode());
            assertEquals(EstadoEventoService.TRANSICION_NO_PERMITIDA, error.getMessage());
            assertEquals(current, event.getEstado());
            verify(events, never()).flush();
            auditedAs(AuditResult.FALLO, "origen=ms06 CONFLICTO");
        } else {
            ResultadoEstadoEvento result = service.recibir(EVENT, request, SERVICE, CORRELATION);
            assertFalse(result.creado());
            assertEquals(EstadoEvento.valueOf(expected), result.evento().estado());
            verify(events).flush();
            auditedAs(AuditResult.EXITO, "origen=ms06 " + current + "->" + expected);
        }
    }

    /**
     * A draft over a scheduled event cancels the programming: the start date is removed and the information is
     * updated
     */
    @Test
    void aDraftOverAScheduledEventRemovesTheDate() {
        Evento event = stored(EstadoEvento.PROGRAMADO, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));

        EventoInterno result = service.recibir(EVENT, body(EstadoEvento.BORRADOR, true, null), SERVICE, CORRELATION)
                .evento();

        assertEquals(EstadoEvento.BORRADOR, result.estado());
        assertNull(result.fechaInicio());
        assertEquals("Simulacro bloque B", result.nombre());
    }

    /**
     * Repeating PROGRAMADO reschedules the event with the new date and information
     */
    @Test
    void repeatingScheduledReschedulesTheEvent() {
        Evento event = stored(EstadoEvento.PROGRAMADO, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));

        EventoInterno result = service.recibir(EVENT, body(EstadoEvento.PROGRAMADO, true, SCHEDULED_DATE), SERVICE,
                CORRELATION).evento();

        assertEquals(SCHEDULED_INSTANT, result.fechaInicio());
        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), result.rolesConvocados());
    }

    /**
     * Starting a scheduled event without a new date keeps the scheduled one, and with a date takes the new one
     */
    @Test
    void startingAScheduledEventKeepsOrReplacesTheDate() {
        Evento event = stored(EstadoEvento.PROGRAMADO, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));

        EventoInterno kept = service.recibir(EVENT, body(EstadoEvento.EN_EJECUCION, true, null), SERVICE,
                CORRELATION).evento();
        assertEquals(PREVIOUS_DATE, kept.fechaInicio());
        assertTrue(kept.checkinHabilitado());

        Evento other = stored(EstadoEvento.PROGRAMADO, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(other));
        EventoInterno replaced = service.recibir(EVENT, body(EstadoEvento.EN_EJECUCION, true, SCHEDULED_DATE),
                SERVICE, CORRELATION).evento();
        assertEquals(SCHEDULED_INSTANT, replaced.fechaInicio());
    }

    /**
     * Starting a draft without date takes the reception time
     */
    @Test
    void startingADraftWithoutDateTakesTheReceptionTime() {
        Evento event = stored(EstadoEvento.BORRADOR, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));

        EventoInterno result = service.recibir(EVENT, body(EstadoEvento.EN_EJECUCION, true, null), SERVICE,
                CORRELATION).evento();

        assertEquals(NOW_IN_MICROS, result.fechaInicio());
    }

    /**
     * Closing an event disables the check-in and stores the reception time; repeating FINALIZADO keeps the first
     * closing date
     */
    @Test
    void closingStoresTheDateAndRepeatingItKeepsIt() {
        Evento event = stored(EstadoEvento.EN_EJECUCION, false);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));

        EventoInterno closed = service.recibir(EVENT, body(EstadoEvento.FINALIZADO, false, null), SERVICE,
                CORRELATION).evento();
        assertEquals(NOW_IN_MICROS, closed.fechaCierre());
        assertFalse(closed.checkinHabilitado());

        Evento finished = stored(EstadoEvento.FINALIZADO, false);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(finished));
        EventoInterno repeated = service.recibir(EVENT, body(EstadoEvento.FINALIZADO, false, null), SERVICE,
                CORRELATION).evento();
        assertEquals(PREVIOUS_CLOSING, repeated.fechaCierre());
    }

    /**
     * Repeating EN_EJECUCION is idempotent: the information of the event does not change
     */
    @Test
    void repeatingInExecutionChangesNothing() {
        Evento event = stored(EstadoEvento.EN_EJECUCION, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));

        EventoInterno result = service.recibir(EVENT, body(EstadoEvento.EN_EJECUCION, true, SCHEDULED_DATE), SERVICE,
                CORRELATION).evento();

        assertEquals(PREVIOUS_DATE, result.fechaInicio());
        assertEquals("Nombre anterior", result.nombre());
    }

    /**
     * The type of an event cannot change: the call answers 409 and the event is not modified
     */
    @Test
    void changingTheTypeOfTheEventIsAConflict() {
        Evento event = stored(EstadoEvento.PROGRAMADO, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));
        when(events.findById(EVENT)).thenReturn(Optional.of(event));

        ApiException error = assertThrows(ApiException.class, () -> service.recibir(EVENT,
                body(EstadoEvento.EN_EJECUCION, false, null), SERVICE, CORRELATION));

        assertEquals(ErrorCode.CONFLICT, error.getCode());
        assertEquals(EstadoEventoService.TIPO_NO_PUEDE_CAMBIAR, error.getMessage());
        assertEquals(EstadoEvento.PROGRAMADO, event.getEstado());
        AuditContext context = auditedAs(AuditResult.FALLO, "origen=ms06 CONFLICTO");
        assertEquals("SIMULACRO", context.tipoEvento());
    }

    /**
     * An invalid body is rejected before opening the transaction and is audited with the type that the body brings
     */
    @Test
    void anInvalidBodyIsAuditedWithTheTypeOfTheBody() {
        when(events.findById(EVENT)).thenReturn(Optional.empty());
        EstadoEventoRequest withoutRoles = new EstadoEventoRequest(EVENT, "Simulacro", false, EstadoEvento.BORRADOR,
                null, null, List.of());

        ApiException error = assertThrows(ApiException.class,
                () -> service.recibir(EVENT, withoutRoles, SERVICE, CORRELATION));

        assertEquals(ErrorCode.VALIDATION, error.getCode());
        verify(events, never()).bloquearEvento(any());
        AuditContext context = auditedAs(AuditResult.FALLO, "origen=ms06 VALIDACION");
        assertEquals(EVENT, context.eventoId());
        assertEquals("REAL", context.tipoEvento());
    }

    /**
     * Without body and without stored event the failed change is audited without event
     */
    @Test
    void withoutBodyTheFailureIsAuditedWithoutEvent() {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.recibir(EVENT, null, SERVICE, CORRELATION));

        AuditContext context = auditedAs(AuditResult.FALLO, "origen=ms06 VALIDACION");
        assertNull(context.eventoId());
        assertNull(context.tipoEvento());
        assertEquals("ms06", context.atributos().get("servicio_origen"));
    }

    /**
     * If the stored event cannot be read for the audit, the type of the body is used and the original error
     * reaches the caller
     */
    @Test
    void ifTheEventCannotBeReadForTheAuditTheBodyTypeIsUsed() {
        IllegalStateException failure = new IllegalStateException("database");
        when(events.buscarParaActualizar(EVENT)).thenThrow(failure);
        when(events.findById(EVENT)).thenThrow(new IllegalStateException("database"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.recibir(EVENT,
                body(EstadoEvento.EN_EJECUCION, true, null), SERVICE, CORRELATION));

        assertSame(failure, error);
        AuditContext context = auditedAs(AuditResult.FALLO, "origen=ms06 ERROR_INTERNO");
        assertEquals("SIMULACRO", context.tipoEvento());
    }

    /**
     * The origin service is written in lower case and without spaces; without service it is unknown
     */
    @Test
    void theOriginServiceIsNormalized() {
        newEvent();

        service.recibir(EVENT, body(EstadoEvento.BORRADOR, true, null), " MS06 ", CORRELATION);
        auditedAs(AuditResult.EXITO, "origen=ms06 NUEVO->BORRADOR");

        Evento event = stored(EstadoEvento.BORRADOR, true);
        when(events.buscarParaActualizar(EVENT)).thenReturn(Optional.of(event));
        service.recibir(EVENT, body(EstadoEvento.BORRADOR, true, null), null, CORRELATION);
        verify(audit).recordSystem(eq(AuditAction.CAMBIO_ESTADO_EVENTO), eq(AuditResult.EXITO), eq("evento:" + EVENT),
                eq("origen=desconocido BORRADOR->BORRADOR"), eq(CORRELATION), any(AuditContext.class));
    }
}
