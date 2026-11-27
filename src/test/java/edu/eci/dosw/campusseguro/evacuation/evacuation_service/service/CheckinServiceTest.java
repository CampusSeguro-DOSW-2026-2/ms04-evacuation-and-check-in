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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditContext;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentro;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentroClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ResultadoEnvioMs05;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
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
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.SolicitudAsistenciaService.EnvioSolicitud;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.SolicitudAsistenciaService.RegistroSolicitud;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * RF-MS04-02 Register check-in and RF-MS04-03 Keep and update the current status, with the repositories, MS02, the
 * assistance requests and the audit replaced by mocks: validation of the event and the meeting point, the append only
 * rule, the detection of a repeated status, the assistance request of NEEDS_ASSISTANCE and the audit of every attempt
 */
@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID POINT_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID POINT_B = UUID.fromString("9c3e5a1d-2b4f-4c6e-8a1b-3d5f7e9a1c24");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID SPACE = UUID.fromString("5e2a8c4d-6f1b-4d3e-9c7a-1b3d5f7a9c46");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-20T15:04:12Z");

    @Mock
    private EventoRepository events;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private PuntoEncuentroClient meetingPoints;

    @Mock
    private AuditRecorder audit;

    @Mock
    private SolicitudAsistenciaService requests;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CheckinService service;

    private RequesterIdentity student;

    /**
     * Creates the service under test with its mocks and a student of the community
     */
    @BeforeEach
    void setUp() {
        service = new CheckinService(events, checkins, meetingPoints, audit, requests, transactionManager);
        student = TestIdentities.identity(USER, "ESTUDIANTE");
    }

    /**
     * Creates an event as MS06 leaves it stored
     *
     * @param state state of the event
     * @param drill true for a drill, false for a real emergency
     * @param roles convened roles
     * @return the event
     */
    private static Evento event(EstadoEvento state, boolean drill, String... roles) {
        Evento event = new Evento(EVENT);
        event.setNombre("Simulacro bloque B");
        event.setSimulacro(drill);
        event.setEstado(state);
        event.setFechaInicio(Instant.parse("2026-11-20T15:00:00Z"));
        event.setRolesConvocados(List.of(roles));
        return event;
    }

    /**
     * Creates a check-in as the database returns it, with the id, number and date that the database assigns
     *
     * @param state reported state
     * @param point meeting point, only with SAFE
     * @param type location type, only with NEEDS_ASSISTANCE
     * @param location location id, only with NEEDS_ASSISTANCE
     * @return the stored check-in
     */
    private static Checkin stored(EstadoCheckin state, UUID point, TipoUbicacion type, UUID location) {
        Checkin checkin = new Checkin(EVENT, USER, state, point, type, location);
        return withDatabaseValues(checkin);
    }

    /**
     * Sets on a check-in the values that the database assigns when it is inserted
     *
     * @param checkin new check-in
     * @return the same check-in with its id, number and registration date
     */
    private static Checkin withDatabaseValues(Checkin checkin) {
        ReflectionTestUtils.setField(checkin, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(checkin, "numero", 1L);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTERED_AT);
        return checkin;
    }

    /**
     * Creates the assistance request of a NEEDS_ASSISTANCE check-in
     *
     * @param checkin check-in that originates the request
     * @param state sending state of the request
     * @param ms05Id id that MS05 assigned, null while it has not been sent
     * @return the request with its id
     */
    private static SolicitudAsistencia request(Checkin checkin, EstadoEnvio state, String ms05Id) {
        SolicitudAsistencia request = new SolicitudAsistencia(checkin.getId(), EVENT, USER, checkin.getUbicacionTipo(),
                checkin.getUbicacionId(), REGISTERED_AT);
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        request.setEstadoEnvio(state);
        request.setSolicitudMs05Id(ms05Id);
        return request;
    }

    /**
     * Prepares an event in execution that convenes the student and is read the same way inside the transaction
     *
     * @param drill true for a drill, false for a real emergency
     * @return the event
     */
    private Evento eventInExecution(boolean drill) {
        Evento event = event(EstadoEvento.EN_EJECUCION, drill, "ESTUDIANTE", "PROFESOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(event));
        when(events.buscarParaCheckin(EVENT)).thenReturn(Optional.of(event));
        return event;
    }

    /**
     * Makes the repository return the saved check-in with the values that the database assigns
     */
    private void saveAssignsDatabaseValues() {
        when(checkins.save(any(Checkin.class))).thenAnswer(invocation -> withDatabaseValues(invocation.getArgument(0)));
    }

    /**
     * Captures the audit context of the only successful or failed record of a check-in
     *
     * @param action expected audit action
     * @param result expected audit result
     * @param resource expected audited resource
     * @param detail expected detail
     * @return the captured context
     */
    private AuditContext auditedAs(AuditAction action, AuditResult result, String resource, String detail) {
        ArgumentCaptor<AuditContext> context = ArgumentCaptor.forClass(AuditContext.class);
        verify(audit).recordAs(eq(action), eq(result), eq(student), eq(resource), eq(detail), eq(CORRELATION),
                context.capture());
        return context.getValue();
    }

    /**
     * The first SAFE report checks the meeting point with MS02, appends the check-in with the point, answers as
     * created and is audited as REGISTRO_CHECKIN with the event type and the attributes for MS08
     */
    @Test
    void firstSafeReportIsCreatedAndAuditedAsRegistration() {
        eventInExecution(true);
        when(meetingPoints.buscar(POINT_A, student, CORRELATION))
                .thenReturn(Optional.of(new PuntoEncuentro(POINT_A, "PE-01", "Cancha central", true)));
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.SAFE, POINT_A, null),
                student, CORRELATION);

        assertTrue(result.creado());
        assertEquals(EstadoCheckin.SAFE, result.checkin().estado());
        assertEquals(POINT_A, result.checkin().puntoEncuentroId());
        assertNull(result.checkin().ubicacion());
        assertNull(result.checkin().solicitudAsistencia());
        assertEquals(REGISTERED_AT, result.checkin().fechaHora());
        ArgumentCaptor<Checkin> saved = ArgumentCaptor.forClass(Checkin.class);
        verify(checkins).save(saved.capture());
        assertEquals(USER, saved.getValue().getUsuarioId());
        assertNull(saved.getValue().getUbicacionTipo());
        AuditContext context = auditedAs(AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO,
                "checkin:" + result.checkin().id(), "SAFE");
        assertEquals(EVENT, context.eventoId());
        assertEquals("SIMULACRO", context.tipoEvento());
        assertEquals("SAFE", context.atributos().get("estado"));
        assertEquals(POINT_A.toString(), context.atributos().get("punto_encuentro_id"));
        verifyNoInteractions(requests);
    }

    /**
     * The check-in transaction takes the lock of the user in the event before reading the event again and the
     * current status, so simultaneous reports of the same user are served one after the other
     */
    @Test
    void theTransactionLocksTheUserBeforeReadingTheCurrentStatus() {
        eventInExecution(false);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();

        service.registrar(EVENT, new CheckinRequest(EstadoCheckin.EVACUATING, null, null), student, CORRELATION);

        InOrder order = inOrder(checkins, events);
        order.verify(checkins).bloquearUsuarioEnEvento(EVENT, USER);
        order.verify(events).buscarParaCheckin(EVENT);
        order.verify(checkins).buscarVigente(EVENT, USER);
        order.verify(checkins).save(any(Checkin.class));
        order.verify(checkins).flush();
    }

    /**
     * EVACUATING and OFF_CAMPUS do not call MS02, carry neither point nor location and do not create a request
     *
     * @param state reported state
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "OFF_CAMPUS" })
    void statesWithoutPlaceDoNotCallOtherServices(EstadoCheckin state) {
        eventInExecution(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(state, null, null), student,
                CORRELATION);

        assertEquals(state, result.checkin().estado());
        assertNull(result.checkin().puntoEncuentroId());
        assertNull(result.checkin().ubicacion());
        verifyNoInteractions(meetingPoints, requests);
    }

    /**
     * NEEDS_ASSISTANCE stores the location, registers the request in the same transaction with the identity of the
     * user for the periodic resend, sends it to MS05 once confirmed and answers with its id and sending state
     */
    @Test
    void needsAssistanceStoresTheLocationAndSendsTheRequest() {
        eventInExecution(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();
        when(requests.registrar(any(Checkin.class))).thenAnswer(invocation -> new RegistroSolicitud(
                request(invocation.getArgument(0), EstadoEnvio.PENDIENTE_ENVIO, null), true));
        when(requests.enviar(any(UUID.class), eq(student), eq(CORRELATION))).thenAnswer(invocation -> {
            SolicitudAsistencia sent = request(stored(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO,
                    BUILDING), EstadoEnvio.ENVIADA, "INC-204");
            return new EnvioSolicitud(sent, ResultadoEnvioMs05.creada("INC-204"));
        });

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(TipoUbicacion.EDIFICIO, BUILDING)), student, CORRELATION);

        assertTrue(result.creado());
        assertEquals(new Ubicacion(TipoUbicacion.EDIFICIO, BUILDING), result.checkin().ubicacion());
        assertEquals("INC-204", result.checkin().solicitudAsistencia().id());
        assertEquals(EstadoEnvio.ENVIADA, result.checkin().solicitudAsistencia().estadoEnvio());
        ArgumentCaptor<Checkin> registered = ArgumentCaptor.forClass(Checkin.class);
        verify(requests).registrar(registered.capture());
        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, registered.getValue().getEstado());
        AuditContext context = auditedAs(AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO,
                "checkin:" + result.checkin().id(), "NEEDS_ASSISTANCE");
        assertEquals("EDIFICIO", context.atributos().get("ubicacion_tipo"));
        assertEquals(BUILDING.toString(), context.atributos().get("ubicacion_id"));
    }

    /**
     * The identity of the user is stored with a new request, so the periodic resend can send it on behalf of the
     * user when there is no request in progress
     */
    @Test
    void aNewRequestKeepsTheIdentityOfTheUser() {
        eventInExecution(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();
        SolicitudAsistencia[] created = new SolicitudAsistencia[1];
        when(requests.registrar(any(Checkin.class))).thenAnswer(invocation -> {
            created[0] = request(invocation.getArgument(0), EstadoEnvio.PENDIENTE_ENVIO, null);
            return new RegistroSolicitud(created[0], true);
        });
        when(requests.enviar(any(UUID.class), eq(student), eq(CORRELATION)))
                .thenAnswer(invocation -> new EnvioSolicitud(created[0], ResultadoEnvioMs05.noDisponible("timeout")));

        service.registrar(EVENT, new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(TipoUbicacion.ESPACIO, SPACE)), student, CORRELATION);

        assertEquals("ESTUDIANTE", created[0].getRolesSolicitante());
        assertEquals(student.permisosComoCabecera(), created[0].getPermisosSolicitante());
    }

    /**
     * An error while sending the request does not reject the check-in: it is accepted and the request stays
     * PENDIENTE_ENVIO for the periodic resend
     */
    @Test
    void aFailureSendingTheRequestDoesNotRejectTheCheckin() {
        eventInExecution(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();
        when(requests.registrar(any(Checkin.class))).thenAnswer(invocation -> new RegistroSolicitud(
                request(invocation.getArgument(0), EstadoEnvio.PENDIENTE_ENVIO, null), true));
        when(requests.enviar(any(UUID.class), eq(student), eq(CORRELATION)))
                .thenThrow(new IllegalStateException("unexpected"));

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(TipoUbicacion.EDIFICIO, BUILDING)), student, CORRELATION);

        assertTrue(result.creado());
        assertNull(result.checkin().solicitudAsistencia().id());
        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, result.checkin().solicitudAsistencia().estadoEnvio());
    }

    /**
     * When the send was not attempted because another send had the request, the answer keeps the request as it was
     * registered
     */
    @Test
    void aSendThatWasNotAttemptedKeepsTheRegisteredRequest() {
        eventInExecution(true);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();
        when(requests.registrar(any(Checkin.class))).thenAnswer(invocation -> new RegistroSolicitud(
                request(invocation.getArgument(0), EstadoEnvio.PENDIENTE_ENVIO, null), true));
        when(requests.enviar(any(UUID.class), eq(student), eq(CORRELATION)))
                .thenReturn(new EnvioSolicitud(null, null));

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(TipoUbicacion.EDIFICIO, BUILDING)), student, CORRELATION);

        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, result.checkin().solicitudAsistencia().estadoEnvio());
    }

    /**
     * A second NEEDS_ASSISTANCE in the same event, after another state, reuses the only request of the user and
     * does not send it again
     */
    @Test
    void aSecondNeedsAssistanceReusesTheRequest() {
        eventInExecution(true);
        Checkin evacuating = stored(EstadoCheckin.EVACUATING, null, null, null);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.of(evacuating));
        saveAssignsDatabaseValues();
        SolicitudAsistencia existing = request(stored(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO,
                BUILDING), EstadoEnvio.ENVIADA, "INC-204");
        when(requests.registrar(any(Checkin.class))).thenReturn(new RegistroSolicitud(existing, false));

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(TipoUbicacion.ESPACIO, SPACE)), student, CORRELATION);

        assertFalse(result.creado());
        assertEquals("INC-204", result.checkin().solicitudAsistencia().id());
        verify(requests, never()).enviar(any(), any(), any());
        assertNull(existing.getRolesSolicitante());
    }

    /**
     * With several roles it is enough that one of them is convened
     */
    @Test
    void withSeveralRolesOneConvenedRoleIsEnough() {
        RequesterIdentity brigadier = TestIdentities.identity(USER, "BRIGADISTA", "PROFESOR");
        Evento event = event(EstadoEvento.EN_EJECUCION, true, "PROFESOR");
        when(events.findById(EVENT)).thenReturn(Optional.of(event));
        when(events.buscarParaCheckin(EVENT)).thenReturn(Optional.of(event));
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        saveAssignsDatabaseValues();

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.OFF_CAMPUS, null, null),
                brigadier, CORRELATION);

        assertTrue(result.creado());
    }

    /**
     * Repeating the current state appends nothing, answers with the current check-in and is audited as a state
     * change without change
     */
    @Test
    void repeatingTheCurrentStateAppendsNothing() {
        eventInExecution(true);
        Checkin current = stored(EstadoCheckin.EVACUATING, null, null, null);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.of(current));

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.EVACUATING, null, null),
                student, CORRELATION);

        assertFalse(result.creado());
        assertEquals(current.getId(), result.checkin().id());
        verify(checkins, never()).save(any());
        AuditContext context = auditedAs(AuditAction.CAMBIO_ESTADO_CHECKIN, AuditResult.EXITO,
                "checkin:" + current.getId(), CheckinService.SIN_CAMBIO);
        assertEquals("EVACUATING", context.atributos().get("estado_anterior"));
    }

    /**
     * Repeating NEEDS_ASSISTANCE with another location is still the same state: nothing is appended and the answer
     * carries the request of the user
     */
    @Test
    void needsAssistanceWithAnotherLocationIsTheSameState() {
        eventInExecution(true);
        Checkin current = stored(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING);
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.of(current));
        when(requests.buscar(EVENT, USER))
                .thenReturn(Optional.of(request(current, EstadoEnvio.PENDIENTE_ENVIO, null)));

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(TipoUbicacion.ESPACIO, SPACE)), student, CORRELATION);

        assertFalse(result.creado());
        assertEquals(new Ubicacion(TipoUbicacion.EDIFICIO, BUILDING), result.checkin().ubicacion());
        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, result.checkin().solicitudAsistencia().estadoEnvio());
        verify(checkins, never()).save(any());
        verify(requests, never()).enviar(any(), any(), any());
    }

    /**
     * SAFE at another meeting point is a new check-in, even if the state is the same
     */
    @Test
    void safeAtAnotherPointIsANewCheckin() {
        eventInExecution(true);
        when(meetingPoints.buscar(POINT_B, student, CORRELATION))
                .thenReturn(Optional.of(new PuntoEncuentro(POINT_B, "PE-02", "Parqueadero", true)));
        when(checkins.buscarVigente(EVENT, USER))
                .thenReturn(Optional.of(stored(EstadoCheckin.SAFE, POINT_A, null, null)));
        saveAssignsDatabaseValues();

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.SAFE, POINT_B, null),
                student, CORRELATION);

        assertFalse(result.creado());
        assertEquals(POINT_B, result.checkin().puntoEncuentroId());
        verify(checkins).save(any(Checkin.class));
        auditedAs(AuditAction.CAMBIO_ESTADO_CHECKIN, AuditResult.EXITO, "checkin:" + result.checkin().id(),
                "SAFE->SAFE");
    }

    /**
     * A change of state appends a new check-in and is audited with the previous state and the new one
     */
    @Test
    void aChangeIsAuditedWithThePreviousAndTheNewState() {
        eventInExecution(false);
        when(checkins.buscarVigente(EVENT, USER))
                .thenReturn(Optional.of(stored(EstadoCheckin.EVACUATING, null, null, null)));
        saveAssignsDatabaseValues();

        ResultadoCheckin result = service.registrar(EVENT, new CheckinRequest(EstadoCheckin.OFF_CAMPUS, null, null),
                student, CORRELATION);

        assertFalse(result.creado());
        AuditContext context = auditedAs(AuditAction.CAMBIO_ESTADO_CHECKIN, AuditResult.EXITO,
                "checkin:" + result.checkin().id(), "EVACUATING->OFF_CAMPUS");
        assertEquals("REAL", context.tipoEvento());
        assertEquals("EVACUATING", context.atributos().get("estado_anterior"));
        assertEquals("OFF_CAMPUS", context.atributos().get("estado"));
    }

    /**
     * An invalid body is rejected with 400 before reading the event or calling MS02, and the attempt is audited
     * without event
     */
    @Test
    void anInvalidBodyIsRejectedAndAudited() {
        CheckinRequest withoutPoint = new CheckinRequest(EstadoCheckin.SAFE, null, null);

        ApiException error = assertThrows(ApiException.class,
                () -> service.registrar(EVENT, withoutPoint, student, CORRELATION));

        assertEquals(ErrorCode.VALIDATION, error.getCode());
        verifyNoInteractions(meetingPoints);
        verify(checkins, never()).save(any());
        AuditContext context = auditedAs(AuditAction.REGISTRO_CHECKIN, AuditResult.FALLO, "evento:" + EVENT,
                "VALIDACION");
        assertSame(AuditContext.NINGUNO, context);
    }

    /**
     * An event that MS04 does not know is not found, and the attempt is audited without event type
     */
    @Test
    void anUnknownEventIsNotFound() {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.EVACUATING, null, null), student, CORRELATION));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(CheckinService.EVENTO_NO_ENCONTRADO, error.getMessage());
        AuditContext context = auditedAs(AuditAction.REGISTRO_CHECKIN, AuditResult.FALLO, "evento:" + EVENT,
                "NO_ENCONTRADO");
        assertNull(context.eventoId());
    }

    /**
     * An event that is not in execution does not accept check-ins (409), and the attempt is audited on the event
     * with its type
     *
     * @param state state of the event
     */
    @ParameterizedTest
    @EnumSource(value = EstadoEvento.class, names = { "BORRADOR", "PROGRAMADO", "FINALIZADO" })
    void anEventThatIsNotInExecutionRejectsTheCheckin(EstadoEvento state) {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(state, true, "ESTUDIANTE")));

        ApiException error = assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.EVACUATING, null, null), student, CORRELATION));

        assertEquals(ErrorCode.CONFLICT, error.getCode());
        assertEquals(CheckinService.CHECKIN_NO_HABILITADO, error.getMessage());
        AuditContext context = auditedAs(AuditAction.REGISTRO_CHECKIN, AuditResult.FALLO, "evento:" + EVENT,
                "CONFLICTO");
        assertEquals(EVENT, context.eventoId());
        assertEquals("SIMULACRO", context.tipoEvento());
    }

    /**
     * An event that does not convene any role of the user does not apply to the user (422)
     */
    @Test
    void anEventThatDoesNotConveneTheRoleIsABusinessRuleError() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION, true, "PROFESOR")));

        ApiException error = assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.EVACUATING, null, null), student, CORRELATION));

        assertEquals(ErrorCode.BUSINESS_RULE, error.getCode());
        assertEquals(CheckinService.EVENTO_NO_APLICA, error.getMessage());
    }

    /**
     * A meeting point that MS02 does not know is not found (404)
     */
    @Test
    void anUnknownMeetingPointIsNotFound() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION, true, "ESTUDIANTE")));
        when(meetingPoints.buscar(POINT_A, student, CORRELATION)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.SAFE, POINT_A, null), student, CORRELATION));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(CheckinService.PUNTO_NO_ENCONTRADO, error.getMessage());
        verify(checkins, never()).save(any());
    }

    /**
     * An inactive meeting point is a business rule error (422)
     */
    @Test
    void anInactiveMeetingPointIsABusinessRuleError() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION, true, "ESTUDIANTE")));
        when(meetingPoints.buscar(POINT_A, student, CORRELATION))
                .thenReturn(Optional.of(new PuntoEncuentro(POINT_A, "PE-01", "Cancha central", false)));

        ApiException error = assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.SAFE, POINT_A, null), student, CORRELATION));

        assertEquals(ErrorCode.BUSINESS_RULE, error.getCode());
        assertEquals(CheckinService.PUNTO_INACTIVO, error.getMessage());
    }

    /**
     * When MS02 does not answer the check-in is rejected with 503 and the attempt is audited with that code
     */
    @Test
    void whenMs02IsUnavailableTheCheckinIsRejected() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION, true, "ESTUDIANTE")));
        when(meetingPoints.buscar(POINT_A, student, CORRELATION))
                .thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.SAFE, POINT_A, null), student, CORRELATION));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        auditedAs(AuditAction.REGISTRO_CHECKIN, AuditResult.FALLO, "evento:" + EVENT, "DEPENDENCIA_NO_DISPONIBLE");
    }

    /**
     * An event that finishes while MS02 is being consulted is checked again inside the transaction, so no check-in
     * is appended after the closing
     */
    @Test
    void anEventClosedDuringTheCallToMs02RejectsTheCheckin() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION, true, "ESTUDIANTE")));
        when(meetingPoints.buscar(POINT_A, student, CORRELATION))
                .thenReturn(Optional.of(new PuntoEncuentro(POINT_A, "PE-01", "Cancha central", true)));
        when(events.buscarParaCheckin(EVENT)).thenReturn(Optional.of(event(EstadoEvento.FINALIZADO, true,
                "ESTUDIANTE")));

        ApiException error = assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.SAFE, POINT_A, null), student, CORRELATION));

        assertEquals(ErrorCode.CONFLICT, error.getCode());
        verify(checkins, never()).save(any());
        verify(transactionManager).rollback(any());
    }

    /**
     * A failed attempt of a user who already has a current status is audited as a state change
     */
    @Test
    void aFailedAttemptWithCurrentStatusIsAuditedAsAChange() {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.FINALIZADO, true, "ESTUDIANTE")));
        when(checkins.buscarVigente(EVENT, USER))
                .thenReturn(Optional.of(stored(EstadoCheckin.EVACUATING, null, null, null)));

        assertThrows(ApiException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.OFF_CAMPUS, null, null), student, CORRELATION));

        auditedAs(AuditAction.CAMBIO_ESTADO_CHECKIN, AuditResult.FALLO, "evento:" + EVENT, "CONFLICTO");
    }

    /**
     * An unexpected error is audited as an internal error and reaches the caller unchanged, even if the audit
     * cannot read the event
     */
    @Test
    void anUnexpectedErrorIsAuditedAsInternalError() {
        Evento event = event(EstadoEvento.EN_EJECUCION, true, "ESTUDIANTE");
        when(events.findById(EVENT)).thenReturn(Optional.of(event)).thenThrow(new IllegalStateException("down"));
        when(events.buscarParaCheckin(EVENT)).thenReturn(Optional.of(event));
        when(checkins.buscarVigente(EVENT, USER)).thenReturn(Optional.empty());
        IllegalStateException failure = new IllegalStateException("database");
        when(checkins.save(any(Checkin.class))).thenThrow(failure);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.registrar(EVENT,
                new CheckinRequest(EstadoCheckin.EVACUATING, null, null), student, CORRELATION));

        assertSame(failure, error);
        AuditContext context = auditedAs(AuditAction.REGISTRO_CHECKIN, AuditResult.FALLO, "evento:" + EVENT,
                "ERROR_INTERNO");
        assertEquals(Map.of(), context.atributos());
    }

    /**
     * A report repeats the current status only with the same state and, with SAFE, the same meeting point
     */
    @Test
    void aReportRepeatsTheCurrentStatusOnlyWithTheSameStateAndPoint() {
        Checkin safeAtA = stored(EstadoCheckin.SAFE, POINT_A, null, null);
        Checkin assistance = stored(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING);

        assertTrue(CheckinService.repiteElVigente(safeAtA, new CheckinRequest(EstadoCheckin.SAFE, POINT_A, null)));
        assertFalse(CheckinService.repiteElVigente(safeAtA, new CheckinRequest(EstadoCheckin.SAFE, POINT_B, null)));
        assertFalse(CheckinService.repiteElVigente(safeAtA,
                new CheckinRequest(EstadoCheckin.EVACUATING, null, null)));
        assertTrue(CheckinService.repiteElVigente(assistance, new CheckinRequest(EstadoCheckin.NEEDS_ASSISTANCE, null,
                new Ubicacion(TipoUbicacion.ESPACIO, SPACE))));
    }
}
