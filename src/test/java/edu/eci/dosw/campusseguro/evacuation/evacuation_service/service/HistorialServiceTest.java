package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.DatosUsuario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.RegistroHistorial;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * RF-MS04-09 History of the check-ins of the event, with the repositories replaced by mocks: each report arrives with
 * its user, state, point or location and date, in the order of the query, and the pagination and the filters reach
 * the query as they are requested
 */
@ExtendWith(MockitoExtension.class)
class HistorialServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID POINT_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final Instant FIRST = Instant.parse("2026-11-20T15:02:05Z");
    private static final Instant SECOND = Instant.parse("2026-11-20T15:04:12Z");
    private static final UUID NO_USER = null;
    private static final EstadoCheckin NO_STATE = null;

    @Mock
    private EventoRepository events;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private UsuarioNombresClient names;

    private HistorialService service;

    /**
     * Creates the service with the mocked repositories and an event that exists
     */
    @BeforeEach
    void setUp() {
        service = new HistorialService(events, checkins, names);
        lenient().when(events.existsById(EVENT)).thenReturn(true);
    }

    /**
     * Creates a check-in as the database returns it, with the id and the date that it assigns
     *
     * @param user user that reports
     * @param state reported state
     * @param point meeting point, only with SAFE
     * @param type location type, only with NEEDS_ASSISTANCE
     * @param location location id, only with NEEDS_ASSISTANCE
     * @param registeredAt date of the report
     * @return the check-in with its id and registration date
     */
    private Checkin checkin(UUID user, EstadoCheckin state, UUID point, TipoUbicacion type, UUID location,
            Instant registeredAt) {
        Checkin checkin = new Checkin(EVENT, user, state, point, type, location);
        ReflectionTestUtils.setField(checkin, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(checkin, "registradoEn", registeredAt);
        return checkin;
    }

    /**
     * Each check-in arrives with its id, user, state, meeting point and registration date
     */
    @Test
    void eachCheckinArrivesWithItsIdUserStatePointAndDate() {
        Checkin safe = checkin(ANA, EstadoCheckin.SAFE, POINT_A, null, null, FIRST);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(safe), PageRequest.of(0, 20), 1));

        PageResponse<RegistroHistorial> answer = service.historial(EVENT, NO_USER, NO_STATE, 0, 20);

        assertEquals(1, answer.contenido().size());
        RegistroHistorial record = answer.contenido().get(0);
        assertEquals(safe.getId(), record.checkinId());
        assertEquals(ANA, record.usuarioId());
        assertEquals(EstadoCheckin.SAFE, record.estado());
        assertEquals(POINT_A, record.puntoEncuentroId());
        assertNull(record.ubicacion());
        assertEquals(FIRST, record.registradoEn());
    }

    /**
     * Each record brings the name and the email of MS01, requested once per person of the page; who is missing in
     * the answer of MS01 keeps both null (RN-MS04-25)
     */
    @Test
    void eachRecordArrivesWithTheNameAndTheEmailOfMs01() {
        Checkin ofAna = checkin(ANA, EstadoCheckin.EVACUATING, null, null, null, FIRST);
        Checkin anotherOfAna = checkin(ANA, EstadoCheckin.SAFE, POINT_A, null, null, SECOND);
        Checkin ofLuis = checkin(LUIS, EstadoCheckin.OFF_CAMPUS, null, null, null, SECOND);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(ofAna, anotherOfAna, ofLuis), PageRequest.of(0, 20), 3));
        when(names.datosDe(List.of(ANA, LUIS)))
                .thenReturn(Map.of(ANA, new DatosUsuario("Ana Pérez", "ana.perez@escuelaing.edu.co")));

        List<RegistroHistorial> records = service.historial(EVENT, NO_USER, NO_STATE, 0, 20).contenido();

        assertEquals("Ana Pérez", records.get(0).nombre());
        assertEquals("ana.perez@escuelaing.edu.co", records.get(0).correo());
        assertEquals("ana.perez@escuelaing.edu.co", records.get(1).correo());
        assertNull(records.get(2).nombre());
        assertNull(records.get(2).correo());
    }

    /**
     * Who asked for assistance arrives with the location and without meeting point
     */
    @Test
    void whoAskedForAssistanceArrivesWithTheLocation() {
        Checkin assistance = checkin(LUIS, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING,
                SECOND);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(assistance), PageRequest.of(0, 20), 1));

        RegistroHistorial record = service.historial(EVENT, NO_USER, NO_STATE, 0, 20).contenido().get(0);

        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, record.estado());
        assertNull(record.puntoEncuentroId());
        assertEquals(new Ubicacion(TipoUbicacion.EDIFICIO, BUILDING), record.ubicacion());
    }

    /**
     * EVACUATING and OFF_CAMPUS carry neither meeting point nor location
     *
     * @param state state without point nor location
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "OFF_CAMPUS" })
    void theStatesWithoutPlaceArriveWithoutIt(EstadoCheckin state) {
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(List.of(checkin(ANA, state, null, null, null, FIRST)), PageRequest.of(0, 20), 1));

        RegistroHistorial record = service.historial(EVENT, NO_USER, NO_STATE, 0, 20).contenido().get(0);

        assertEquals(state, record.estado());
        assertNull(record.puntoEncuentroId());
        assertNull(record.ubicacion());
    }

    /**
     * Every report of a person arrives, in the order of the query (RN-MS04-08)
     */
    @Test
    void everyReportOfAPersonArrivesInTheOrderOfTheQuery() {
        Checkin evacuating = checkin(ANA, EstadoCheckin.EVACUATING, null, null, null, FIRST);
        Checkin assistance = checkin(ANA, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING,
                SECOND);
        Checkin safe = checkin(ANA, EstadoCheckin.SAFE, POINT_A, null, null, Instant.parse("2026-11-20T15:09:40Z"));
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(List.of(evacuating, assistance, safe), PageRequest.of(0, 20), 3));

        PageResponse<RegistroHistorial> answer = service.historial(EVENT, NO_USER, NO_STATE, 0, 20);

        assertEquals(List.of(EstadoCheckin.EVACUATING, EstadoCheckin.NEEDS_ASSISTANCE, EstadoCheckin.SAFE),
                answer.contenido().stream()
                        .map(RegistroHistorial::estado)
                        .toList());
        assertEquals(List.of(evacuating.getId(), assistance.getId(), safe.getId()),
                answer.contenido().stream()
                        .map(RegistroHistorial::checkinId)
                        .toList());
        assertEquals(3, answer.totalElementos());
    }

    /**
     * A4: an event without check-ins returns the empty list, with total 0 and without pages
     */
    @Test
    void anEventWithoutCheckinsReturnsTheEmptyList() {
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<RegistroHistorial> answer = service.historial(EVENT, NO_USER, NO_STATE, 0, 20);

        assertEquals(List.of(), answer.contenido());
        assertEquals(0, answer.page());
        assertEquals(20, answer.size());
        assertEquals(0, answer.totalElementos());
        assertEquals(0, answer.totalPaginas());
    }

    /**
     * A page beyond the last one is empty and keeps the total and the requested page
     */
    @Test
    void aPageBeyondTheLastOneIsEmptyAndKeepsTheTotal() {
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(7, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(7, 20), 45));

        PageResponse<RegistroHistorial> answer = service.historial(EVENT, NO_USER, NO_STATE, 7, 20);

        assertEquals(List.of(), answer.contenido());
        assertEquals(7, answer.page());
        assertEquals(45, answer.totalElementos());
        assertEquals(3, answer.totalPaginas());
    }

    /**
     * The total of pages comes from the total of check-ins and the page size
     */
    @Test
    void theTotalOfPagesComesFromTheTotalAndTheSize() {
        Checkin checkin = checkin(ANA, EstadoCheckin.EVACUATING, null, null, null, FIRST);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(2, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin), PageRequest.of(2, 20), 41));

        PageResponse<RegistroHistorial> answer = service.historial(EVENT, NO_USER, NO_STATE, 2, 20);

        assertEquals(2, answer.page());
        assertEquals(20, answer.size());
        assertEquals(41, answer.totalElementos());
        assertEquals(3, answer.totalPaginas());
    }

    /**
     * The requested user, state and page reach the query unchanged
     */
    @Test
    void theRequestedFiltersAndPageReachTheQuery() {
        when(checkins.buscarHistorial(EVENT, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        service.historial(EVENT, ANA, EstadoCheckin.SAFE, 2, 5);

        verify(checkins).buscarHistorial(EVENT, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5));
    }

    /**
     * Without filters the whole event is requested
     */
    @Test
    void withoutFiltersTheWholeEventIsRequested() {
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.historial(EVENT, NO_USER, NO_STATE, 0, 100);

        verify(checkins).buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 100));
    }

    /**
     * A3: an event that does not exist answers 404 and the check-ins are not queried
     */
    @Test
    void anUnknownEventAnswers404WithoutQueryingTheCheckins() {
        UUID other = UUID.randomUUID();
        when(events.existsById(other)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.historial(other, NO_USER, NO_STATE, 0, 20));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("Evento no encontrado.", error.getMessage());
        verifyNoInteractions(checkins);
    }

    /**
     * Querying the history adds, modifies and deletes nothing (RN-MS04-17)
     */
    @Test
    void queryingTheHistoryOnlyReadsTheData() {
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.historial(EVENT, NO_USER, NO_STATE, 0, 20);

        verify(checkins, never()).save(any());
        verify(checkins, never()).flush();
        verify(events, never()).save(any());
        verify(checkins).buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20));
        verifyNoMoreInteractions(checkins);
    }
}
