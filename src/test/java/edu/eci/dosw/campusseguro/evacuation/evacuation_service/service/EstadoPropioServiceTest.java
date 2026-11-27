package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * RF-MS04-05 Query the own current status, with the repositories replaced by mocks
 */
@ExtendWith(MockitoExtension.class)
class EstadoPropioServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID POINT = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID CHECKIN = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-20T15:04:12Z");

    @Mock
    private EventoRepository events;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private SolicitudAsistenciaRepository requests;

    private EstadoPropioService service;

    private RequesterIdentity requester;

    /**
     * Creates the service with the mocked repositories and a student as requester
     */
    @BeforeEach
    void setUp() {
        service = new EstadoPropioService(events, checkins, requests);
        requester = TestIdentities.identity(USER, "ESTUDIANTE");
    }

    /**
     * Creates a check-in as the database returns it, with the id and the date that it assigns
     *
     * @param state reported state
     * @param point meeting point, only with SAFE
     * @param type location type, only with NEEDS_ASSISTANCE
     * @param location location id, only with NEEDS_ASSISTANCE
     * @return the check-in with its id and registration date
     */
    private Checkin checkin(EstadoCheckin state, UUID point, TipoUbicacion type, UUID location) {
        Checkin checkin = new Checkin(EVENT, USER, state, point, type, location);
        ReflectionTestUtils.setField(checkin, "id", CHECKIN);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTERED_AT);
        return checkin;
    }

    /**
     * Creates the assistance request of the user with the given sending state and MS05 id
     *
     * @param sendingState sending state to MS05
     * @param ms05Id id of the request in MS05, null if it did not arrive
     * @return the assistance request
     */
    private SolicitudAsistencia request(EstadoEnvio sendingState, String ms05Id) {
        SolicitudAsistencia request = new SolicitudAsistencia(CHECKIN, EVENT, USER, TipoUbicacion.EDIFICIO,
                BUILDING, REGISTERED_AT);
        request.setEstadoEnvio(sendingState);
        request.setSolicitudMs05Id(ms05Id);
        return request;
    }

    /**
     * With SAFE the meeting point is answered, without location nor request, and the requests are not queried
     */
    @Test
    void withSafeReturnsTheMeetingPointWithoutLocationNorRequest() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, POINT, null, null)));

        CheckinResponse answer = service.consultar(EVENT, requester);

        assertEquals(CHECKIN, answer.id());
        assertEquals(EVENT, answer.eventoId());
        assertEquals(EstadoCheckin.SAFE, answer.estado());
        assertEquals(POINT, answer.puntoEncuentroId());
        assertNull(answer.ubicacion());
        assertNull(answer.solicitudAsistencia());
        assertEquals(REGISTERED_AT, answer.fechaHora());
        verifyNoInteractions(requests);
    }

    /**
     * With EVACUATING and OFF_CAMPUS there is no meeting point, location nor request
     *
     * @param state current state of the user
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "OFF_CAMPUS" })
    void withEvacuatingOrOffCampusThereIsNoPointLocationNorRequest(EstadoCheckin state) {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.of(checkin(state, null, null, null)));

        CheckinResponse answer = service.consultar(EVENT, requester);

        assertEquals(state, answer.estado());
        assertNull(answer.puntoEncuentroId());
        assertNull(answer.ubicacion());
        assertNull(answer.solicitudAsistencia());
        verifyNoInteractions(requests);
    }

    /**
     * With NEEDS_ASSISTANCE the location and the state of the request sent to MS05 are answered
     */
    @Test
    void withNeedsAssistanceReturnsTheLocationAndTheSentRequest() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING)));
        when(requests.findByEventoIdAndUsuarioId(EVENT, USER))
                .thenReturn(Optional.of(request(EstadoEnvio.ENVIADA, "SOL-2026-0042")));

        CheckinResponse answer = service.consultar(EVENT, requester);

        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, answer.estado());
        assertNull(answer.puntoEncuentroId());
        assertEquals(TipoUbicacion.EDIFICIO, answer.ubicacion().tipo());
        assertEquals(BUILDING, answer.ubicacion().id());
        assertEquals("SOL-2026-0042", answer.solicitudAsistencia().id());
        assertEquals(EstadoEnvio.ENVIADA, answer.solicitudAsistencia().estadoEnvio());
    }

    /**
     * A pending or rejected request has no MS05 id yet
     *
     * @param sendingState sending state of the request
     */
    @ParameterizedTest
    @EnumSource(value = EstadoEnvio.class, names = { "PENDIENTE_ENVIO", "RECHAZADA" })
    void aRequestThatDidNotReachMs05HasNoId(EstadoEnvio sendingState) {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.ESPACIO, BUILDING)));
        when(requests.findByEventoIdAndUsuarioId(EVENT, USER))
                .thenReturn(Optional.of(request(sendingState, null)));

        CheckinResponse answer = service.consultar(EVENT, requester);

        assertNull(answer.solicitudAsistencia().id());
        assertEquals(sendingState, answer.solicitudAsistencia().estadoEnvio());
    }

    /**
     * If the assistance check-in has no request, the state is answered without it
     */
    @Test
    void withoutTheRequestTheStateIsAnsweredWithoutIt() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING)));
        when(requests.findByEventoIdAndUsuarioId(EVENT, USER)).thenReturn(Optional.empty());

        CheckinResponse answer = service.consultar(EVENT, requester);

        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, answer.estado());
        assertEquals(BUILDING, answer.ubicacion().id());
        assertNull(answer.solicitudAsistencia());
    }

    /**
     * An unknown event is 404 and the check-ins are not queried
     */
    @Test
    void anUnknownEventIsNotFoundAndTheCheckinsAreNotQueried() {
        when(events.existsById(EVENT)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> service.consultar(EVENT, requester));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("Evento no encontrado.", error.getMessage());
        verifyNoInteractions(checkins, requests);
    }

    /**
     * Without check-in in the event it is 404 with the message of RF-MS04-05
     */
    @Test
    void withoutCheckinInTheEventIsNotFound() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.consultar(EVENT, requester));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("No tiene check-in en este evento.", error.getMessage());
        verifyNoInteractions(requests);
    }

    /**
     * The query only uses the user id of the identity of the requester
     */
    @Test
    void theQueryOnlyUsesTheUserIdOfTheIdentity() {
        UUID otherUser = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
        RequesterIdentity other = TestIdentities.identity(otherUser, "PROFESOR");
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, otherUser)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.consultar(EVENT, other));

        verify(checkins).buscarVigente(EVENT, otherUser);
        verify(checkins, never()).buscarVigente(EVENT, USER);
    }

    /**
     * The query only reads: it uses no write operation
     */
    @Test
    void theQueryModifiesNoData() {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, POINT, null, null)));

        service.consultar(EVENT, requester);

        verify(events).existsById(EVENT);
        verify(checkins).buscarVigente(EVENT, USER);
        verifyNoMoreInteractions(events, checkins, requests);
    }
}
