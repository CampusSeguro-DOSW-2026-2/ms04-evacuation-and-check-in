package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.DatosUsuario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Pendiente;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PersonaAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.SolicitudAsistenciaResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstadoYPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * RF-MS04-06 (consolidate the state of the event and list people by state and meeting point), RF-MS04-07 (pending
 * people) and RF-MS04-08 (people who need assistance), with the repositories and the MS01 and MS05 clients replaced
 * by mocks
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID BRIGADIER = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID POINT_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID POINT_B = UUID.fromString("d3f5a7c9-1b2e-4d6f-8a0c-4e6b8d0f2a41");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID SPACE = UUID.fromString("4e9c7a15-3b2d-4f60-a8e1-7c5d9b3f1a28");
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-20T15:04:12Z");
    private static final Instant CALCULATED_AT = Instant.parse("2026-11-20T15:10:00Z");
    private static final Clock CLOCK = Clock.fixed(CALCULATED_AT, ZoneOffset.UTC);
    private static final Set<UUID> NONE = Set.of();
    private static final Instant FIRST_REPORT = Instant.parse("2026-11-20T15:02:00Z");
    private static final Instant SECOND_REPORT = Instant.parse("2026-11-20T15:04:12Z");
    private static final Instant START = Instant.parse("2026-11-20T14:00:00Z");
    private static final List<String> ROLES = List.of("ESTUDIANTE", "PROFESOR");

    /**
     * Count row as the database returns it
     *
     * @param state state of the check-in
     * @param point meeting point, only with SAFE
     * @param total number of people
     */
    private record Row(String state, UUID point, long total) implements ConteoPorEstadoYPunto {

        /**
         * @return the state of the row
         */
        @Override
        public String getEstado() {
            return state;
        }

        /**
         * @return the meeting point of the row
         */
        @Override
        public UUID getPunto() {
            return point;
        }

        /**
         * @return the total of the row
         */
        @Override
        public long getTotal() {
            return total;
        }
    }

    @Mock
    private EventoRepository events;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private SolicitudAsistenciaRepository requests;

    @Mock
    private UsuarioNombresClient names;

    @Mock
    private ZonasBrigadistaClient brigadierZones;

    @Mock
    private PoblacionObjetivoClient targetPopulation;

    private DashboardService service;

    /**
     * Test email of a person, derived from the name
     *
     * @param name full name
     * @return the institutional test email
     */
    private static String email(String name) {
        return name.toLowerCase().replace(' ', '.') + "@escuelaing.edu.co";
    }

    /**
     * Data that MS01 delivers for a test person
     *
     * @param name full name
     * @return the name and its test email
     */
    private static DatosUsuario data(String name) {
        return new DatosUsuario(name, email(name));
    }

    /**
     * Creates the service with the fixed clock
     */
    @BeforeEach
    void setUp() {
        service = new DashboardService(events, checkins, requests, names, brigadierZones, targetPopulation, CLOCK);
    }

    /**
     * Creates the event in the given state
     *
     * @param state state of the event
     * @return the event
     */
    private Evento event(EstadoEvento state) {
        Evento event = new Evento(EVENT);
        event.setEstado(state);
        return event;
    }

    /**
     * Creates a check-in as the database returns it
     *
     * @param user user that reports
     * @param state reported state
     * @param point meeting point, only with SAFE
     * @return the check-in with its registration date
     */
    private Checkin checkin(UUID user, EstadoCheckin state, UUID point) {
        Checkin checkin = new Checkin(EVENT, user, state, point, null, null);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTERED_AT);
        return checkin;
    }

    /**
     * Identity with the given roles
     *
     * @param roles roles of the requester
     * @return the identity
     */
    private RequesterIdentity with(String... roles) {
        return TestIdentities.identity(BRIGADIER, roles);
    }

    /**
     * Builds the totals of the four states, the safe people by point, the reported total and the date of the
     * calculation
     */
    @Test
    void consolidationBuildsTheTotalsThePointsAndTheDate() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of(
                new Row("SAFE", POINT_A, 7), new Row("SAFE", POINT_B, 3), new Row("EVACUATING", null, 4),
                new Row("NEEDS_ASSISTANCE", null, 2), new Row("OFF_CAMPUS", null, 5)));

        Consolidado consolidation = service.consolidado(EVENT, with("COORDINADOR"));

        assertEquals(EVENT, consolidation.eventoId());
        assertEquals(EstadoEvento.EN_EJECUCION, consolidation.estadoEvento());
        assertEquals(10, consolidation.totalesPorEstado().safe());
        assertEquals(4, consolidation.totalesPorEstado().evacuating());
        assertEquals(2, consolidation.totalesPorEstado().needsAssistance());
        assertEquals(5, consolidation.totalesPorEstado().offCampus());
        assertEquals(21, consolidation.totalReportados());
        assertEquals(List.of(new TotalPorPunto(POINT_A, 7), new TotalPorPunto(POINT_B, 3)),
                consolidation.porPuntoEncuentro());
        assertEquals(CALCULATED_AT, consolidation.calculadoEn());
        verifyNoInteractions(brigadierZones, names);
    }

    /**
     * The date of the calculation is delivered in milliseconds
     */
    @Test
    void consolidationDeliversTheDateInMilliseconds() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-20T15:10:00.123456789Z"), ZoneOffset.UTC);
        service = new DashboardService(events, checkins, requests, names, brigadierZones, targetPopulation, clock);
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        Consolidado consolidation = service.consolidado(EVENT, with("COORDINADOR"));

        assertEquals(Instant.parse("2026-11-20T15:10:00.123Z"), consolidation.calculadoEn());
    }

    /**
     * Without check-ins it returns the four states in 0, without points and with total 0
     */
    @Test
    void consolidationWithoutCheckinsReturnsTheFourStatesInZero() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.PROGRAMADO)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        Consolidado consolidation = service.consolidado(EVENT, with("ORGANIZADOR"));

        assertEquals(0, consolidation.totalesPorEstado().safe());
        assertEquals(0, consolidation.totalesPorEstado().evacuating());
        assertEquals(0, consolidation.totalesPorEstado().needsAssistance());
        assertEquals(0, consolidation.totalesPorEstado().offCampus());
        assertEquals(0, consolidation.totalReportados());
        assertTrue(consolidation.porPuntoEncuentro().isEmpty());
    }

    /**
     * It works with the event in any state and reports it in the answer
     *
     * @param state state of the event
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void consolidationWorksWithTheEventInAnyState(EstadoEvento state) {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(state)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        Consolidado consolidation = service.consolidado(EVENT, with("AUDITOR"));

        assertEquals(state, consolidation.estadoEvento());
    }

    /**
     * COORDINADOR, ORGANIZADOR and AUDITOR see the whole event without querying MS05
     *
     * @param role role with access to the whole event
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR" })
    void consolidationDoesNotRestrictRolesWithAccessToTheWholeEvent(String role) {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        service.consolidado(EVENT, with(role));

        verifyNoInteractions(brigadierZones);
    }

    /**
     * An unknown event answers 404 without querying counts nor zones
     */
    @Test
    void consolidationOfAnUnknownEventAnswers404() {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> service.consolidado(EVENT, with("BRIGADISTA")));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(DashboardService.EVENTO_NO_ENCONTRADO, error.getMessage());
        verifyNoInteractions(checkins, brigadierZones);
    }

    /**
     * The brigadier asks MS05 for the zones and counts only what is in them
     */
    @Test
    void consolidationOfTheBrigadierCountsOnlyTheirZones() {
        ZonasAsignadas zones = new ZonasAsignadas(Set.of(BUILDING), Set.of(SPACE), Set.of(POINT_A));
        RequesterIdentity brigadier = with("BRIGADISTA");
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(brigadierZones.zonasDe(brigadier, EVENT)).thenReturn(zones);
        when(checkins.contarVigentes(EVENT, true, Set.of(POINT_A), Set.of(BUILDING), Set.of(SPACE)))
                .thenReturn(List.of(new Row("SAFE", POINT_A, 6), new Row("NEEDS_ASSISTANCE", null, 1)));

        Consolidado consolidation = service.consolidado(EVENT, brigadier);

        assertEquals(6, consolidation.totalesPorEstado().safe());
        assertEquals(0, consolidation.totalesPorEstado().evacuating());
        assertEquals(1, consolidation.totalesPorEstado().needsAssistance());
        assertEquals(0, consolidation.totalesPorEstado().offCampus());
        assertEquals(7, consolidation.totalReportados());
        assertEquals(List.of(new TotalPorPunto(POINT_A, 6)), consolidation.porPuntoEncuentro());
    }

    /**
     * Who has BRIGADISTA and also a role with access to the whole event is not restricted
     */
    @Test
    void aBrigadierWithAnotherRoleWithAccessIsNotRestricted() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        service.consolidado(EVENT, with("BRIGADISTA", "COORDINADOR"));

        verifyNoInteractions(brigadierZones);
    }

    /**
     * The restriction is decided by permissions and not by the name of the role (MR-07): a role created by the
     * administrator with ms04:dashboard:read and without ms04:history:read is limited to its zones, like the
     * brigadier
     */
    @Test
    void aNewRoleWithDashboardWithoutHistoryIsRestrictedToItsZones() {
        RequesterIdentity floorLeader = new RequesterIdentity(BRIGADIER, Set.of("JEFE_DE_PISO"),
                Set.of("ms04:dashboard:read"));
        ZonasAsignadas zones = new ZonasAsignadas(Set.of(), Set.of(), Set.of(POINT_A));
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(brigadierZones.zonasDe(floorLeader, EVENT)).thenReturn(zones);
        when(checkins.contarVigentes(EVENT, true, Set.of(POINT_A), Set.of(), Set.of())).thenReturn(List.of());

        service.consolidado(EVENT, floorLeader);

        verify(brigadierZones).zonasDe(floorLeader, EVENT);
    }

    /**
     * If the administrator gives ms04:history:read to the brigadier role, that user sees the whole event: the name of
     * the role does not decide the restriction
     */
    @Test
    void aBrigadierWithHistorySeesTheWholeEvent() {
        RequesterIdentity brigadier = new RequesterIdentity(BRIGADIER, Set.of("BRIGADISTA"),
                Set.of("ms04:dashboard:read", "ms04:history:read"));
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        service.consolidado(EVENT, brigadier);

        verifyNoInteractions(brigadierZones);
    }

    /**
     * Who has BRIGADISTA and a role without access to the dashboard stays restricted to their zones
     */
    @Test
    void aBrigadierWithACommunityRoleStaysRestricted() {
        RequesterIdentity brigadier = with("BRIGADISTA", "ESTUDIANTE");
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(brigadierZones.zonasDe(brigadier, EVENT)).thenReturn(new ZonasAsignadas(NONE, NONE, NONE));
        when(checkins.contarVigentes(EVENT, true, NONE, NONE, NONE)).thenReturn(List.of());

        Consolidado consolidation = service.consolidado(EVENT, brigadier);

        assertEquals(0, consolidation.totalReportados());
    }

    /**
     * If MS05 does not deliver the zones, the brigadier gets 503 and nothing else is queried
     */
    @Test
    void consolidationOfTheBrigadierWithoutZonesOfMs05Answers503() {
        RequesterIdentity brigadier = with("BRIGADISTA");
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(brigadierZones.zonasDe(brigadier, EVENT)).thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class, () -> service.consolidado(EVENT, brigadier));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        verifyNoInteractions(checkins);
    }

    /**
     * Lists the people with their name from MS01, the current state, the point and the date, and the totals of the
     * page
     */
    @Test
    void peopleListsNameStatePointAndDate() {
        when(events.existsById(EVENT)).thenReturn(true);
        Page<Checkin> page = new PageImpl<>(
                List.of(checkin(ANA, EstadoCheckin.SAFE, POINT_A), checkin(LUIS, EstadoCheckin.EVACUATING, null)),
                PageRequest.of(1, 2), 5);
        when(checkins.buscarVigentes(EVENT, null, null, false, NONE, NONE, NONE, PageRequest.of(1, 2)))
                .thenReturn(page);
        when(names.datosDe(List.of(ANA, LUIS)))
                .thenReturn(Map.of(ANA, data("Ana Pérez"), LUIS, data("Luis Rojas")));

        PageResponse<CheckinResumen> answer = service.personas(EVENT, null, null, 1, 2, with("COORDINADOR"));

        assertEquals(List.of(
                new CheckinResumen(ANA, "Ana Pérez", email("Ana Pérez"), EstadoCheckin.SAFE, POINT_A, REGISTERED_AT),
                new CheckinResumen(LUIS, "Luis Rojas", email("Luis Rojas"), EstadoCheckin.EVACUATING, null,
                        REGISTERED_AT)),
                answer.contenido());
        assertEquals(1, answer.page());
        assertEquals(2, answer.size());
        assertEquals(5, answer.totalElementos());
        assertEquals(3, answer.totalPaginas());
        verifyNoInteractions(brigadierZones);
    }

    /**
     * If MS01 does not deliver the names, the list has a null name
     */
    @Test
    void peopleWithoutNamesOfMs01HasNullName() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentes(EVENT, null, null, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(ANA, EstadoCheckin.OFF_CAMPUS, null)),
                        PageRequest.of(0, 20), 1));
        when(names.datosDe(List.of(ANA))).thenReturn(Map.of());

        PageResponse<CheckinResumen> answer = service.personas(EVENT, null, null, 0, 20, with("AUDITOR"));

        assertEquals(1, answer.contenido().size());
        assertNull(answer.contenido().get(0).nombre());
        assertEquals(ANA, answer.contenido().get(0).usuarioId());
    }

    /**
     * The state and the meeting point of the filter reach the query
     */
    @Test
    void peopleAppliesTheFilterOfStateAndPoint() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentes(EVENT, EstadoCheckin.SAFE, POINT_A, false, NONE, NONE, NONE,
                PageRequest.of(0, 20))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.personas(EVENT, EstadoCheckin.SAFE, POINT_A, 0, 20, with("ORGANIZADOR"));

        verify(checkins).buscarVigentes(EVENT, EstadoCheckin.SAFE, POINT_A, false, NONE, NONE, NONE,
                PageRequest.of(0, 20));
    }

    /**
     * The meeting point without state is valid: only the safe people have a point
     */
    @Test
    void peopleAcceptsTheMeetingPointWithoutState() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentes(EVENT, null, POINT_B, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        PageResponse<CheckinResumen> answer = service.personas(EVENT, null, POINT_B, 0, 20, with("COORDINADOR"));

        assertTrue(answer.contenido().isEmpty());
        assertEquals(0, answer.totalElementos());
    }

    /**
     * The meeting point with a state other than SAFE answers 400 before querying anything
     *
     * @param state state other than SAFE
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "NEEDS_ASSISTANCE", "OFF_CAMPUS" })
    void peopleWithPointAndAStateOtherThanSafeAnswers400(EstadoCheckin state) {
        ApiException error = assertThrows(ApiException.class,
                () -> service.personas(EVENT, state, POINT_A, 0, 20, with("COORDINADOR")));

        assertEquals(ErrorCode.VALIDATION, error.getCode());
        assertEquals("punto_encuentro_id", error.getErrors().get(0).campo());
        assertEquals("El punto de encuentro solo aplica a Estoy a salvo.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(events, checkins, names, brigadierZones);
    }

    /**
     * An unknown event answers 404 without querying check-ins, names nor zones, not even for a brigadier
     */
    @Test
    void peopleOfAnUnknownEventAnswers404() {
        when(events.existsById(EVENT)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.personas(EVENT, null, null, 0, 20, with("BRIGADISTA")));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(DashboardService.EVENTO_NO_ENCONTRADO, error.getMessage());
        verifyNoInteractions(checkins, names, brigadierZones);
    }

    /**
     * The brigadier asks MS05 for the zones and lists only what is in them
     */
    @Test
    void peopleOfTheBrigadierListsOnlyTheirZones() {
        RequesterIdentity brigadier = with("BRIGADISTA");
        when(events.existsById(EVENT)).thenReturn(true);
        when(brigadierZones.zonasDe(brigadier, EVENT))
                .thenReturn(new ZonasAsignadas(Set.of(BUILDING), Set.of(SPACE), Set.of(POINT_A)));
        when(checkins.buscarVigentes(EVENT, null, null, true, Set.of(POINT_A), Set.of(BUILDING), Set.of(SPACE),
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(ANA, EstadoCheckin.SAFE, POINT_A)),
                        PageRequest.of(0, 20), 1));
        when(names.datosDe(List.of(ANA))).thenReturn(Map.of(ANA, data("Ana Pérez")));

        PageResponse<CheckinResumen> answer = service.personas(EVENT, null, null, 0, 20, brigadier);

        assertEquals(1, answer.totalElementos());
        assertEquals("Ana Pérez", answer.contenido().get(0).nombre());
    }

    /**
     * If MS05 does not deliver the zones, the brigadier gets 503 and nothing else is queried
     */
    @Test
    void peopleOfTheBrigadierWithoutZonesOfMs05Answers503() {
        RequesterIdentity brigadier = with("BRIGADISTA");
        when(events.existsById(EVENT)).thenReturn(true);
        when(brigadierZones.zonasDe(brigadier, EVENT)).thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class,
                () -> service.personas(EVENT, null, null, 0, 20, brigadier));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        verifyNoInteractions(checkins, names);
    }

    /**
     * A page without people does not fail: it returns the empty content with the totals in 0
     *
     * @param page requested page number
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 7 })
    void peopleWithoutResultsReturnsAnEmptyPage(int page) {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentes(EVENT, null, null, false, NONE, NONE, NONE, PageRequest.of(page, 20)))
                .thenReturn(Page.empty(PageRequest.of(page, 20)));

        PageResponse<CheckinResumen> answer = service.personas(EVENT, null, null, page, 20, with("COORDINADOR"));

        assertTrue(answer.contenido().isEmpty());
        assertEquals(page, answer.page());
        assertEquals(0, answer.totalElementos());
        assertEquals(0, answer.totalPaginas());
        verify(names).datosDe(List.of());
        verify(brigadierZones, never()).zonasDe(any(), any());
    }

    /**
     * Creates the current NEEDS_ASSISTANCE check-in of a user, as the database returns it
     *
     * @param user user that reports
     * @param type type of the reported location
     * @param location MS02 id of the location
     * @param registeredAt date of the check-in
     * @return the check-in with its registration date
     */
    private Checkin assistanceCheckin(UUID user, TipoUbicacion type, UUID location, Instant registeredAt) {
        Checkin checkin = new Checkin(EVENT, user, EstadoCheckin.NEEDS_ASSISTANCE, null, type, location);
        ReflectionTestUtils.setField(checkin, "registradoEn", registeredAt);
        return checkin;
    }

    /**
     * Creates the assistance request of a user with its sending state
     *
     * @param user user of the request
     * @param ms05Id id that MS05 returned, null if it did not arrive
     * @param sendingState sending state
     * @return the request
     */
    private SolicitudAsistencia request(UUID user, String ms05Id, EstadoEnvio sendingState) {
        SolicitudAsistencia request = new SolicitudAsistencia(UUID.randomUUID(), EVENT, user,
                TipoUbicacion.EDIFICIO, BUILDING, FIRST_REPORT);
        request.setSolicitudMs05Id(ms05Id);
        request.setEstadoEnvio(sendingState);
        return request;
    }

    /**
     * Each person has the name, the location and the date of the current check-in and the request in MS05, in the
     * order of the query (from the oldest report to the most recent)
     */
    @Test
    void assistanceListsNameLocationDateAndRequest() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(
                        assistanceCheckin(ANA, TipoUbicacion.EDIFICIO, BUILDING, FIRST_REPORT),
                        assistanceCheckin(LUIS, TipoUbicacion.ESPACIO, SPACE, SECOND_REPORT)),
                        PageRequest.of(0, 20), 2));
        when(names.datosDe(List.of(ANA, LUIS)))
                .thenReturn(Map.of(ANA, data("Ana Pérez"), LUIS, data("Luis Gómez")));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA, LUIS))).thenReturn(List.of(
                request(LUIS, null, EstadoEnvio.PENDIENTE_ENVIO),
                request(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA)));

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, 0, 20, with("COORDINADOR"));

        assertEquals(2, answer.totalElementos());
        assertEquals(List.of(new PersonaAsistencia(ANA, "Ana Pérez", email("Ana Pérez"),
                new Ubicacion(TipoUbicacion.EDIFICIO, BUILDING),
                FIRST_REPORT, new SolicitudAsistenciaResumen("SOL-2026-0042", EstadoEnvio.ENVIADA)),
                new PersonaAsistencia(LUIS, "Luis Gómez", email("Luis Gómez"),
                        new Ubicacion(TipoUbicacion.ESPACIO, SPACE),
                        SECOND_REPORT, new SolicitudAsistenciaResumen(null, EstadoEnvio.PENDIENTE_ENVIO))),
                answer.contenido());
        verifyNoInteractions(brigadierZones);
    }

    /**
     * A request that did not reach MS05 is shown as it is, so the coordinator can register it by hand (A3)
     *
     * @param sendingState sending state of the request
     */
    @ParameterizedTest
    @EnumSource(EstadoEnvio.class)
    void assistanceShowsTheSendingStateOfTheRequest(EstadoEnvio sendingState) {
        String ms05Id = null;
        if (sendingState == EstadoEnvio.ENVIADA) {
            ms05Id = "SOL-2026-0042";
        }
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(assistanceCheckin(ANA, TipoUbicacion.PUNTO_ENCUENTRO, POINT_A, FIRST_REPORT)),
                        PageRequest.of(0, 20), 1));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA)))
                .thenReturn(List.of(request(ANA, ms05Id, sendingState)));

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, 0, 20, with("COORDINADOR"));

        assertEquals(new SolicitudAsistenciaResumen(ms05Id, sendingState),
                answer.contenido().get(0).solicitudAsistencia());
        assertEquals(new Ubicacion(TipoUbicacion.PUNTO_ENCUENTRO, POINT_A), answer.contenido().get(0).ubicacion());
    }

    /**
     * If MS01 does not answer, the list has a null name and the rest of the data
     */
    @Test
    void assistanceWithoutNamesOfMs01HasNullName() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(assistanceCheckin(ANA, TipoUbicacion.EDIFICIO, BUILDING, FIRST_REPORT)),
                        PageRequest.of(0, 20), 1));
        when(names.datosDe(List.of(ANA))).thenReturn(Map.of());
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA)))
                .thenReturn(List.of(request(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA)));

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, 0, 20, with("COORDINADOR"));

        assertNull(answer.contenido().get(0).nombre());
        assertEquals(ANA, answer.contenido().get(0).usuarioId());
        assertEquals("SOL-2026-0042", answer.contenido().get(0).solicitudAsistencia().id());
    }

    /**
     * A person without request still appears in the list, with a null request
     */
    @Test
    void assistanceWithoutRequestShowsThePersonWithANullRequest() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(assistanceCheckin(ANA, TipoUbicacion.EDIFICIO, BUILDING, FIRST_REPORT)),
                        PageRequest.of(0, 20), 1));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA))).thenReturn(List.of());

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, 0, 20, with("COORDINADOR"));

        assertEquals(1, answer.totalElementos());
        assertNull(answer.contenido().get(0).solicitudAsistencia());
    }

    /**
     * A1: if nobody needs assistance it answers the empty list and does not query requests
     *
     * @param page requested page number
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 7 })
    void ifNobodyNeedsAssistanceTheListIsEmpty(int page) {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(page, 20)))
                .thenReturn(Page.empty(PageRequest.of(page, 20)));

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, page, 20, with("COORDINADOR"));

        assertTrue(answer.contenido().isEmpty());
        assertEquals(page, answer.page());
        assertEquals(0, answer.totalElementos());
        assertEquals(0, answer.totalPaginas());
        verify(requests, never()).findByEventoIdAndUsuarioIdIn(any(), any());
        verify(brigadierZones, never()).zonasDe(any(), any());
    }

    /**
     * The list is paginated: only the page is requested to the database, and the total and the pages come from the
     * total of people
     */
    @Test
    void assistanceIsPaginated() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(1, 2)))
                .thenReturn(new PageImpl<>(
                        List.of(assistanceCheckin(LUIS, TipoUbicacion.ESPACIO, SPACE, SECOND_REPORT)),
                        PageRequest.of(1, 2), 3));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(LUIS)))
                .thenReturn(List.of(request(LUIS, null, EstadoEnvio.RECHAZADA)));

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, 1, 2, with("COORDINADOR"));

        assertEquals(1, answer.page());
        assertEquals(2, answer.size());
        assertEquals(3, answer.totalElementos());
        assertEquals(2, answer.totalPaginas());
        assertEquals(1, answer.contenido().size());
    }

    /**
     * A2: the brigadier asks MS05 for the zones and gets only the people in them
     */
    @Test
    void assistanceOfTheBrigadierListsOnlyTheirZones() {
        RequesterIdentity brigadier = with("BRIGADISTA");
        when(events.existsById(EVENT)).thenReturn(true);
        when(brigadierZones.zonasDe(brigadier, EVENT))
                .thenReturn(new ZonasAsignadas(Set.of(BUILDING), Set.of(SPACE), Set.of(POINT_A)));
        when(checkins.buscarVigentesConAsistencia(EVENT, true, Set.of(POINT_A), Set.of(BUILDING), Set.of(SPACE),
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(assistanceCheckin(ANA, TipoUbicacion.EDIFICIO, BUILDING, FIRST_REPORT)),
                        PageRequest.of(0, 20), 1));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA)))
                .thenReturn(List.of(request(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA)));

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, 0, 20, brigadier);

        assertEquals(1, answer.totalElementos());
        assertEquals(ANA, answer.contenido().get(0).usuarioId());
    }

    /**
     * Who has BRIGADISTA and another role with access sees every person of the event
     *
     * @param other role that sees the whole event
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR" })
    void assistanceOfABrigadierWithAnotherRoleWithAccessIsNotRestricted(String other) {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.asistencia(EVENT, 0, 20, with("BRIGADISTA", other));

        verify(brigadierZones, never()).zonasDe(any(), any());
    }

    /**
     * If MS05 does not deliver the zones, the brigadier gets 503 and nothing else is queried
     */
    @Test
    void assistanceOfTheBrigadierWithoutZonesOfMs05Answers503() {
        RequesterIdentity brigadier = with("BRIGADISTA");
        when(events.existsById(EVENT)).thenReturn(true);
        when(brigadierZones.zonasDe(brigadier, EVENT)).thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class, () -> service.asistencia(EVENT, 0, 20, brigadier));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        verifyNoInteractions(checkins, requests, names);
    }

    /**
     * A5: with an unknown event it answers 404 without querying anything else, the zones of the brigadier included
     *
     * @param role role of the requester
     */
    @ParameterizedTest
    @ValueSource(strings = { "BRIGADISTA", "COORDINADOR", "ORGANIZADOR", "AUDITOR" })
    void assistanceOfAnUnknownEventAnswers404(String role) {
        when(events.existsById(EVENT)).thenReturn(false);
        RequesterIdentity requester = with(role);

        ApiException error = assertThrows(ApiException.class, () -> service.asistencia(EVENT, 0, 20, requester));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(DashboardService.EVENTO_NO_ENCONTRADO, error.getMessage());
        verifyNoInteractions(checkins, requests, names, brigadierZones);
    }

    /**
     * The list works with the event in any state (RN-MS04-22): the service does not look at the state of the event
     *
     * @param state state of the event
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void assistanceWorksWithTheEventInAnyState(EstadoEvento state) {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(
                        List.of(assistanceCheckin(ANA, TipoUbicacion.EDIFICIO, BUILDING, FIRST_REPORT)),
                        PageRequest.of(0, 20), 1));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA)))
                .thenReturn(List.of(request(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA)));

        PageResponse<PersonaAsistencia> answer = service.asistencia(EVENT, 0, 20, with("COORDINADOR"));

        assertEquals(1, answer.totalElementos());
    }

    /**
     * Test user whose id grows with the number, to check the order of the list
     *
     * @param number number of the user, from 1 to 9
     * @return the id of the user
     */
    private static UUID user(int number) {
        return UUID.fromString("00000000-0000-4000-8000-00000000000" + number);
    }

    /**
     * Creates an event in execution with fixed convened roles and start
     *
     * @return the event
     */
    private Evento convenedEvent() {
        Evento event = event(EstadoEvento.EN_EJECUCION);
        event.setRolesConvocados(ROLES);
        event.setFechaInicio(START);
        return event;
    }

    /**
     * The pending people are the target population minus who has a current status, with the name of each one and
     * the total in total_elementos
     */
    @Test
    void pendingAreThePopulationMinusWhoReported() {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator))
                .thenReturn(Optional.of(Set.of(user(1), user(2), user(3), user(4))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of(user(2), user(4)));
        when(names.datosDe(List.of(user(1), user(3))))
                .thenReturn(Map.of(user(1), data("Ana Pérez"), user(3), data("Luis Gómez")));

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, coordinator);

        assertEquals(List.of(new Pendiente(user(1), "Ana Pérez", email("Ana Pérez")),
                new Pendiente(user(3), "Luis Gómez", email("Luis Gómez"))),
                answer.contenido());
        assertEquals(2, answer.totalElementos());
        assertEquals(1, answer.totalPaginas());
        assertEquals(0, answer.page());
        assertEquals(20, answer.size());
        verifyNoInteractions(brigadierZones);
    }

    /**
     * The list is sorted by user id whatever the order in which MS01 delivered the population
     */
    @Test
    void pendingAreSortedByUserId() {
        RequesterIdentity auditor = with("AUDITOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, auditor))
                .thenReturn(Optional.of(Set.of(user(9), user(3), user(7), user(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of(user(1), user(3), user(7), user(9)))).thenReturn(Map.of());

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, auditor);

        assertEquals(List.of(user(1), user(3), user(7), user(9)), answer.contenido().stream()
                .map(Pendiente::usuarioId)
                .toList());
    }

    /**
     * Without check-ins in the event the whole target population is pending (A1)
     */
    @Test
    void withoutCheckinsTheWholePopulationIsPending() {
        RequesterIdentity organizer = with("ORGANIZADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, organizer))
                .thenReturn(Optional.of(Set.of(user(1), user(2))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of(user(1), user(2)))).thenReturn(Map.of());

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, organizer);

        assertEquals(2, answer.totalElementos());
    }

    /**
     * If everybody reported it answers total 0 and an empty list (A2)
     */
    @Test
    void ifEverybodyReportedTheListIsEmpty() {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator))
                .thenReturn(Optional.of(Set.of(user(1), user(2))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of(user(1), user(2)));
        when(names.datosDe(List.of())).thenReturn(Map.of());

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, coordinator);

        assertTrue(answer.contenido().isEmpty());
        assertEquals(0, answer.totalElementos());
        assertEquals(0, answer.totalPaginas());
    }

    /**
     * Who reported but is no longer in the target population (for example after a change of role) does not change
     * the list
     */
    @Test
    void whoReportedOutsideThePopulationDoesNotChangeThePending() {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator))
                .thenReturn(Optional.of(Set.of(user(1), user(2))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of(user(2), user(8)));
        when(names.datosDe(List.of(user(1)))).thenReturn(Map.of());

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, coordinator);

        assertEquals(1, answer.totalElementos());
        assertEquals(user(1), answer.contenido().get(0).usuarioId());
    }

    /**
     * With convened roles without users the list is empty
     */
    @Test
    void withoutTargetPopulationTheListIsEmpty() {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator)).thenReturn(Optional.of(Set.of()));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of())).thenReturn(Map.of());

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, coordinator);

        assertTrue(answer.contenido().isEmpty());
        assertEquals(0, answer.totalElementos());
    }

    /**
     * The list is paginated: each page brings its part, the total is the one of the pending people and the names are
     * requested only for the page
     */
    @Test
    void pendingArePaginated() {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator)).thenReturn(Optional.of(
                Set.of(user(1), user(2), user(3), user(4), user(5))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of(user(3), user(4)))).thenReturn(Map.of(user(3), data("Tres")));

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 1, 2, coordinator);

        assertEquals(List.of(new Pendiente(user(3), "Tres", email("Tres")), new Pendiente(user(4), null, null)),
                answer.contenido());
        assertEquals(5, answer.totalElementos());
        assertEquals(3, answer.totalPaginas());
        assertEquals(1, answer.page());
        assertEquals(2, answer.size());
    }

    /**
     * A page beyond the last one returns the empty list with the totals, without failing nor overflowing
     *
     * @param page requested page number
     */
    @ParameterizedTest
    @ValueSource(ints = { 3, 100, Integer.MAX_VALUE })
    void aPageBeyondTheLastOneReturnsTheEmptyListWithTheTotals(int page) {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator)).thenReturn(Optional.of(
                Set.of(user(1), user(2), user(3), user(4), user(5))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of())).thenReturn(Map.of());

        PageResponse<Pendiente> answer = service.pendientes(EVENT, page, 2, coordinator);

        assertTrue(answer.contenido().isEmpty());
        assertEquals(5, answer.totalElementos());
        assertEquals(3, answer.totalPaginas());
    }

    /**
     * If MS01 does not deliver the names, the list has a null name and stays complete
     */
    @Test
    void withoutNamesOfMs01ThePendingHaveNullName() {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator))
                .thenReturn(Optional.of(Set.of(user(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of(user(1)))).thenReturn(Map.of());

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, coordinator);

        assertNull(answer.contenido().get(0).nombre());
        assertEquals(user(1), answer.contenido().get(0).usuarioId());
    }

    /**
     * If MS01 does not deliver the target population it answers 503 with the message of F-03 and queries nothing
     * else
     */
    @Test
    void withoutPopulationOfMs01Answers503WithTheMessageOfTheList() {
        RequesterIdentity coordinator = with("COORDINADOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.pendientes(EVENT, 0, 20, coordinator));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertEquals("La lista de pendientes no está disponible en este momento.", error.getMessage());
        verifyNoInteractions(checkins, names, brigadierZones);
    }

    /**
     * The brigadier gets an empty list with total 0 without querying MS01, MS05 nor the check-ins (A6)
     */
    @Test
    void theBrigadierGetsAnEmptyListWithoutQueryingMs01() {
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));

        PageResponse<Pendiente> answer = service.pendientes(EVENT, 0, 20, with("BRIGADISTA"));

        assertTrue(answer.contenido().isEmpty());
        assertEquals(0, answer.totalElementos());
        assertEquals(0, answer.totalPaginas());
        assertEquals(20, answer.size());
        verifyNoInteractions(targetPopulation, brigadierZones, checkins, names);
    }

    /**
     * Who has another role with the permission besides BRIGADISTA sees the whole list
     *
     * @param other role that sees the whole event
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR" })
    void whoAlsoHasAnotherRoleWithThePermissionSeesThePending(String other) {
        RequesterIdentity requester = with("BRIGADISTA", other);
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, requester))
                .thenReturn(Optional.of(Set.of(user(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of(user(1)))).thenReturn(Map.of());

        assertEquals(1, service.pendientes(EVENT, 0, 20, requester).totalElementos());
    }

    /**
     * An unknown event answers 404 without querying anything else, not even for a brigadier (A5)
     *
     * @param role role of the requester
     */
    @ParameterizedTest
    @ValueSource(strings = { "BRIGADISTA", "COORDINADOR" })
    void pendingOfAnUnknownEventAnswers404(String role) {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.pendientes(EVENT, 0, 20, with(role)));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(DashboardService.EVENTO_NO_ENCONTRADO, error.getMessage());
        verifyNoInteractions(targetPopulation, checkins, names, brigadierZones);
    }

    /**
     * The pending people are queried with the event in any state (RN-MS04-22)
     *
     * @param state state of the event
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void pendingWorksWithTheEventInAnyState(EstadoEvento state) {
        RequesterIdentity coordinator = with("COORDINADOR");
        Evento event = convenedEvent();
        event.setEstado(state);
        when(events.findById(EVENT)).thenReturn(Optional.of(event));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, START, coordinator))
                .thenReturn(Optional.of(Set.of(user(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of(user(1)))).thenReturn(Map.of());

        assertEquals(1, service.pendientes(EVENT, 0, 20, coordinator).totalElementos());
    }

    /**
     * The cut of the population is the start of the event if it already passed, and the moment of the query if the
     * event has not started yet or has no start date (RN-MS04-15)
     *
     * @param start start of the event; null if it has none
     * @param cut expected cut
     */
    @ParameterizedTest
    @CsvSource(value = { "2026-11-20T14:00:00Z,2026-11-20T14:00:00Z", "2026-11-20T16:00:00Z,2026-11-20T15:10:00Z",
            "null,2026-11-20T15:10:00Z" }, nullValues = "null")
    void theCutOfThePopulationIsTheStartOrTheQueryMoment(Instant start, Instant cut) {
        RequesterIdentity coordinator = with("COORDINADOR");
        Evento event = convenedEvent();
        event.setFechaInicio(start);
        when(events.findById(EVENT)).thenReturn(Optional.of(event));
        when(targetPopulation.usuariosActivosPorRoles(ROLES, cut, coordinator))
                .thenReturn(Optional.of(Set.of()));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of())).thenReturn(Map.of());

        assertTrue(service.pendientes(EVENT, 0, 20, coordinator).contenido().isEmpty());
    }
}
