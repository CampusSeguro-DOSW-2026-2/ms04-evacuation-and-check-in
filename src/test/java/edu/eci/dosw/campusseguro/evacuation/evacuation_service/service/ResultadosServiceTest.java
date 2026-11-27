package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PrimerCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Resultados;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.PrimerCheckinPorUsuario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.SolicitudPorUsuario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * RF-MS04-10 Deliver the results of the event, with the repositories and the MS01 client replaced by mocks: the
 * totals by state and by meeting point, the people who asked for assistance, the first check-in of each user, the
 * target population with its pending people, partial results while the event is not finished and the consistent
 * read-only transaction
 */
@ExtendWith(MockitoExtension.class)
class ResultadosServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID SOFIA = UUID.fromString("8e4b6d2f-3a5c-4e7b-9d1f-4c6e8a0b2d35");
    private static final UUID OUTSIDER = UUID.fromString("1a3c5e7b-9d2f-4b6d-8e0a-5f7b9d1c3e57");
    private static final UUID POINT = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final Instant START = Instant.parse("2026-11-20T15:00:00Z");
    private static final Instant CLOSING = Instant.parse("2026-11-20T16:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-20T17:00:00Z");

    @Mock
    private EventoRepository events;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private PoblacionObjetivoClient population;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ResultadosService service;

    private RequesterIdentity coordinator;

    /**
     * Creates the service with a fixed clock and a coordinator who queries the results
     */
    @BeforeEach
    void setUp() {
        service = new ResultadosService(events, checkins, population, Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
        coordinator = TestIdentities.identity(UUID.randomUUID(), "COORDINADOR");
    }

    /**
     * Drill that convenes students and teachers
     *
     * @param state state of the event
     * @param start start date, or null
     * @return the event
     */
    private static Evento event(EstadoEvento state, Instant start) {
        Evento event = new Evento(EVENT);
        event.setSimulacro(true);
        event.setEstado(state);
        event.setFechaInicio(start);
        if (state == EstadoEvento.FINALIZADO) {
            event.setFechaCierre(CLOSING);
        }
        event.setRolesConvocados(List.of("ESTUDIANTE", "PROFESOR"));
        return event;
    }

    /**
     * Row of the count by state
     *
     * @param state state of the check-in
     * @param total number of people
     * @return the row
     */
    private static ConteoPorEstado byState(String state, long total) {
        return new ConteoPorEstado() {

            /**
             * @return the state of the row
             */
            @Override
            public String getEstado() {
                return state;
            }

            /**
             * @return the total of the row
             */
            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    /**
     * Row of the count of safe people by meeting point
     *
     * @param point meeting point
     * @param total number of people
     * @return the row
     */
    private static ConteoPorPunto byPoint(UUID point, long total) {
        return new ConteoPorPunto() {

            /**
             * @return the meeting point of the row
             */
            @Override
            public UUID getPuntoEncuentroId() {
                return point;
            }

            /**
             * @return the total of the row
             */
            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    /**
     * Row of a person who asked for assistance
     *
     * @param user user of the row
     * @param requestId id of the request in MS05, or null
     * @param sendingState sending state, or null without request
     * @return the row
     */
    private static SolicitudPorUsuario requester(UUID user, String requestId, String sendingState) {
        return new SolicitudPorUsuario() {

            /**
             * @return the user of the row
             */
            @Override
            public UUID getUsuarioId() {
                return user;
            }

            /**
             * @return the id of the request in MS05
             */
            @Override
            public String getSolicitudId() {
                return requestId;
            }

            /**
             * @return the sending state of the request
             */
            @Override
            public String getEstadoEnvio() {
                return sendingState;
            }
        };
    }

    /**
     * Row of the first check-in of a user
     *
     * @param user user of the row
     * @param at date of the first check-in
     * @return the row
     */
    private static PrimerCheckinPorUsuario first(UUID user, String at) {
        return new PrimerCheckinPorUsuario() {

            /**
             * @return the user of the row
             */
            @Override
            public UUID getUsuarioId() {
                return user;
            }

            /**
             * @return the date of the first check-in
             */
            @Override
            public Instant getPrimerCheckinEn() {
                return Instant.parse(at);
            }
        };
    }

    /**
     * Prepares the event and the rows that the queries return
     *
     * @param event stored event
     */
    private void stored(Evento event) {
        when(events.findById(EVENT)).thenReturn(Optional.of(event));
        when(checkins.contarVigentesPorEstado(EVENT)).thenReturn(List.of(byState("SAFE", 1),
                byState("NEEDS_ASSISTANCE", 1)));
        when(checkins.contarSafePorPuntoEncuentro(EVENT)).thenReturn(List.of(byPoint(POINT, 1)));
        when(checkins.buscarSolicitantesDeAsistencia(EVENT)).thenReturn(List.of(
                requester(LUIS, "INC-204", "ENVIADA"),
                requester(ANA, null, null)));
        when(checkins.buscarPrimerosCheckins(EVENT)).thenReturn(List.of(
                first(ANA, "2026-11-20T15:02:05Z"),
                first(LUIS, "2026-11-20T15:04:12Z")));
    }

    /**
     * The results of a finished event bring every datum and are final
     */
    @Test
    void theResultsOfAFinishedEventAreFinal() {
        stored(event(EstadoEvento.FINALIZADO, START));
        when(population.usuariosActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"), START, coordinator))
                .thenReturn(Optional.of(Set.of(ANA, LUIS, SOFIA)));

        Resultados results = service.resultados(EVENT, coordinator);

        assertEquals(EVENT, results.eventoId());
        assertTrue(results.simulacro());
        assertEquals(EstadoEvento.FINALIZADO, results.estadoEvento());
        assertEquals(START, results.fechaInicio());
        assertEquals(CLOSING, results.fechaCierre());
        assertTrue(results.finales());
        assertEquals(3L, results.poblacionObjetivo());
        assertEquals(2, results.totalReportados());
        assertEquals(1L, results.totalPendientes());
        assertEquals(new TotalesPorEstado(1, 0, 1, 0), results.totalesPorEstado());
        assertEquals(List.of(new TotalPorPunto(POINT, 1)), results.porPuntoEncuentro());
        assertEquals("INC-204", results.solicitudesAsistencia().get(0).solicitudAsistencia().id());
        assertEquals(EstadoEnvio.ENVIADA, results.solicitudesAsistencia().get(0).solicitudAsistencia().estadoEnvio());
        assertNull(results.solicitudesAsistencia().get(1).solicitudAsistencia());
        assertEquals(new PrimerCheckin(ANA, Instant.parse("2026-11-20T15:02:05Z")), results.primerosCheckins().get(0));
    }

    /**
     * An event that is not finished delivers partial results, and the population is cut at the start of the event
     */
    @Test
    void anEventThatIsNotFinishedDeliversPartialResults() {
        stored(event(EstadoEvento.EN_EJECUCION, START));
        when(population.usuariosActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"), START, coordinator))
                .thenReturn(Optional.of(Set.of(ANA, LUIS)));

        Resultados results = service.resultados(EVENT, coordinator);

        assertFalse(results.finales());
        assertNull(results.fechaCierre());
        assertEquals(0L, results.totalPendientes());
    }

    /**
     * When MS01 does not deliver the population, the population and the pending people are null and the rest is
     * delivered
     */
    @Test
    void withoutPopulationTheRestIsDelivered() {
        stored(event(EstadoEvento.FINALIZADO, START));
        when(population.usuariosActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"), START, coordinator))
                .thenReturn(Optional.empty());

        Resultados results = service.resultados(EVENT, coordinator);

        assertNull(results.poblacionObjetivo());
        assertNull(results.totalPendientes());
        assertEquals(2, results.totalReportados());
    }

    /**
     * An event without check-ins brings the four states in zero and the whole population pending
     */
    @Test
    void anEventWithoutCheckinsHasTheWholePopulationPending() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION, START)));
        when(population.usuariosActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"), START, coordinator))
                .thenReturn(Optional.of(Set.of(ANA, LUIS)));

        Resultados results = service.resultados(EVENT, coordinator);

        assertEquals(new TotalesPorEstado(0, 0, 0, 0), results.totalesPorEstado());
        assertEquals(0, results.totalReportados());
        assertEquals(2L, results.totalPendientes());
        assertEquals(List.of(), results.porPuntoEncuentro());
    }

    /**
     * Who reported without being in the population does not reduce the pending people
     */
    @Test
    void whoReportedOutsideThePopulationDoesNotReduceThePending() {
        List<PrimerCheckin> firsts = List.of(new PrimerCheckin(OUTSIDER, START), new PrimerCheckin(ANA, START));

        assertEquals(1, ResultadosService.contarPendientes(Set.of(ANA, LUIS), firsts));
    }

    /**
     * The population is cut at the moment of the query when the event has not started or has no start date
     */
    @Test
    void theCutIsTheQueryMomentIfTheEventHasNotStarted() {
        assertEquals(NOW, service.corteDePoblacion(event(EstadoEvento.PROGRAMADO, NOW.plusSeconds(3600))));
        assertEquals(NOW, service.corteDePoblacion(event(EstadoEvento.BORRADOR, null)));
        assertEquals(START, service.corteDePoblacion(event(EstadoEvento.EN_EJECUCION, START)));
    }

    /**
     * Every query is read inside a single read-only REPEATABLE READ transaction, so the totals match each other
     */
    @Test
    void theQueriesShareAConsistentReadOnlyTransaction() {
        stored(event(EstadoEvento.FINALIZADO, START));
        when(population.usuariosActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"), START, coordinator))
                .thenReturn(Optional.of(Set.of(ANA)));

        service.resultados(EVENT, coordinator);

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertTrue(definition.getValue().isReadOnly());
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, definition.getValue().getIsolationLevel());
    }

    /**
     * An event that MS04 does not know is not found and MS01 is not called
     */
    @Test
    void anUnknownEventIsNotFound() {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.resultados(EVENT, coordinator));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        verifyNoInteractions(population, checkins);
    }
}
