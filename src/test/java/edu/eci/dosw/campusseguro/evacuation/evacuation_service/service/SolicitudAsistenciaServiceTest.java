package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditContext;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ResultadoEnvioMs05;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.SolicitudAsistenciaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.SolicitudAsistenciaService.EnvioSolicitud;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.SolicitudAsistenciaService.RegistroSolicitud;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * RF-MS04-04 Generate the assistance request in MS05, with the repositories, the MS05 client and the audit replaced
 * by mocks: one request per user and event, the sending state after each attempt, the identity used to send it, the
 * audit of every attempt and the periodic resend of the pending ones
 */
@ExtendWith(MockitoExtension.class)
class SolicitudAsistenciaServiceTest {

    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final Instant REPORTED_AT = Instant.parse("2026-11-20T15:04:12Z");
    private static final Instant NOW = Instant.parse("2026-11-20T15:04:13Z");

    @Mock
    private SolicitudAsistenciaRepository requests;

    @Mock
    private EventoRepository events;

    @Mock
    private SolicitudAsistenciaClient ms05;

    @Mock
    private AuditRecorder audit;

    @Mock
    private PlatformTransactionManager transactionManager;

    private SolicitudAsistenciaService service;

    private RequesterIdentity student;

    /**
     * Creates the service with a fixed clock and a student of the community
     */
    @BeforeEach
    void setUp() {
        service = new SolicitudAsistenciaService(requests, events, ms05, audit, Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
        student = TestIdentities.identity(USER, "ESTUDIANTE");
    }

    /**
     * Stored check-in of the user in the event
     *
     * @param state reported state
     * @return the check-in with its id and registration date
     */
    private static Checkin checkin(EstadoCheckin state) {
        Checkin checkin;
        if (state == EstadoCheckin.NEEDS_ASSISTANCE) {
            checkin = new Checkin(EVENT, USER, state, null, TipoUbicacion.EDIFICIO, BUILDING);
        } else {
            checkin = new Checkin(EVENT, USER, state, null, null, null);
        }
        ReflectionTestUtils.setField(checkin, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(checkin, "registradoEn", REPORTED_AT);
        return checkin;
    }

    /**
     * Stored request in PENDIENTE_ENVIO with the identity of the user who reported it
     *
     * @return the request with its id
     */
    private static SolicitudAsistencia pending() {
        SolicitudAsistencia request = new SolicitudAsistencia(UUID.randomUUID(), EVENT, USER, TipoUbicacion.EDIFICIO,
                BUILDING, REPORTED_AT);
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        request.setIdentidadSolicitante("ESTUDIANTE", "ms04:checkin:write");
        return request;
    }

    /**
     * Drill in execution that the audit reads to identify the event
     *
     * @return the event
     */
    private static Evento drill() {
        Evento event = new Evento(EVENT);
        event.setSimulacro(true);
        event.setEstado(EstadoEvento.EN_EJECUCION);
        return event;
    }

    /**
     * Makes the repository take the request as pending and return what it saves
     *
     * @param request pending request
     */
    private void takes(SolicitudAsistencia request) {
        when(requests.tomarPendiente(request.getId())).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(request)).thenReturn(request);
    }

    /**
     * Only a NEEDS_ASSISTANCE check-in originates a request
     */
    @Test
    void onlyNeedsAssistanceOriginatesARequest() {
        Checkin evacuating = checkin(EstadoCheckin.EVACUATING);

        assertThrows(IllegalArgumentException.class, () -> service.registrar(evacuating));

        verifyNoInteractions(requests);
    }

    /**
     * The first NEEDS_ASSISTANCE of the user in the event creates the request in PENDIENTE_ENVIO with the location
     * and the date of the check-in
     */
    @Test
    void theFirstNeedsAssistanceCreatesAPendingRequest() {
        Checkin checkin = checkin(EstadoCheckin.NEEDS_ASSISTANCE);
        when(requests.findByEventoIdAndUsuarioId(EVENT, USER)).thenReturn(Optional.empty());
        when(requests.saveAndFlush(any(SolicitudAsistencia.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegistroSolicitud registered = service.registrar(checkin);

        assertTrue(registered.nueva());
        SolicitudAsistencia request = registered.solicitud();
        assertEquals(checkin.getId(), request.getCheckinId());
        assertEquals(EVENT, request.getEventoId());
        assertEquals(USER, request.getUsuarioId());
        assertEquals(TipoUbicacion.EDIFICIO, request.getUbicacionTipo());
        assertEquals(BUILDING, request.getUbicacionId());
        assertEquals(REPORTED_AT, request.getReportadaEn());
        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, request.getEstadoEnvio());
        assertEquals(0, request.getIntentos());
    }

    /**
     * A user has at most one request per event: a new NEEDS_ASSISTANCE reuses the existing one in any state
     */
    @Test
    void anExistingRequestIsReused() {
        SolicitudAsistencia existing = pending();
        existing.setEstadoEnvio(EstadoEnvio.RECHAZADA);
        when(requests.findByEventoIdAndUsuarioId(EVENT, USER)).thenReturn(Optional.of(existing));

        RegistroSolicitud registered = service.registrar(checkin(EstadoCheckin.NEEDS_ASSISTANCE));

        assertFalse(registered.nueva());
        assertSame(existing, registered.solicitud());
        verify(requests, never()).saveAndFlush(any());
    }

    /**
     * A request created by MS05 is ENVIADA with its id, the attempt is counted and it is audited as a success of the
     * user on the check-in that originated it
     */
    @Test
    void aCreatedRequestIsSentAndAuditedAsSuccess() {
        SolicitudAsistencia request = pending();
        takes(request);
        when(ms05.enviar(request, "ESTUDIANTE", student.permisosComoCabecera(), CORRELATION))
                .thenReturn(ResultadoEnvioMs05.creada("INC-204"));
        when(events.findById(EVENT)).thenReturn(Optional.of(drill()));

        EnvioSolicitud sent = service.enviar(request.getId(), student, CORRELATION);

        assertEquals(ResultadoEnvioMs05.Tipo.CREADA, sent.resultado().tipo());
        assertEquals(EstadoEnvio.ENVIADA, request.getEstadoEnvio());
        assertEquals("INC-204", request.getSolicitudMs05Id());
        assertEquals(1, request.getIntentos());
        assertEquals(NOW, request.getUltimoIntentoEn());
        ArgumentCaptor<AuditContext> context = ArgumentCaptor.forClass(AuditContext.class);
        verify(audit).recordAs(eq(AuditAction.SOLICITUD_ASISTENCIA), eq(AuditResult.EXITO), eq(student),
                eq("checkin:" + request.getCheckinId()), eq("ENVIADA"), eq(CORRELATION), context.capture());
        assertEquals("SIMULACRO", context.getValue().tipoEvento());
        assertEquals("EDIFICIO", context.getValue().atributos().get("ubicacion_tipo"));
        assertEquals(BUILDING.toString(), context.getValue().atributos().get("ubicacion_id"));
    }

    /**
     * A request that MS05 rejects as invalid stays RECHAZADA and is audited as a failure
     */
    @Test
    void aRejectedRequestStaysRejected() {
        SolicitudAsistencia request = pending();
        takes(request);
        when(ms05.enviar(request, "ESTUDIANTE", student.permisosComoCabecera(), CORRELATION))
                .thenReturn(ResultadoEnvioMs05.rechazada("400 VALIDACION"));
        when(events.findById(EVENT)).thenReturn(Optional.of(drill()));

        service.enviar(request.getId(), student, CORRELATION);

        assertEquals(EstadoEnvio.RECHAZADA, request.getEstadoEnvio());
        assertNull(request.getSolicitudMs05Id());
        verify(audit).recordAs(eq(AuditAction.SOLICITUD_ASISTENCIA), eq(AuditResult.FALLO), eq(student),
                eq("checkin:" + request.getCheckinId()), eq("RECHAZADA"), eq(CORRELATION), any(AuditContext.class));
    }

    /**
     * When MS05 does not answer the request stays PENDIENTE_ENVIO with the attempt counted, and it is audited as a
     * failure even if the event cannot be read
     */
    @Test
    void anUnavailableMs05LeavesTheRequestPending() {
        SolicitudAsistencia request = pending();
        request.setIntentos(2);
        takes(request);
        when(ms05.enviar(request, "ESTUDIANTE", student.permisosComoCabecera(), CORRELATION))
                .thenReturn(ResultadoEnvioMs05.noDisponible("timeout"));
        when(events.findById(EVENT)).thenThrow(new IllegalStateException("database"));

        service.enviar(request.getId(), student, CORRELATION);

        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, request.getEstadoEnvio());
        assertEquals(3, request.getIntentos());
        ArgumentCaptor<AuditContext> context = ArgumentCaptor.forClass(AuditContext.class);
        verify(audit).recordAs(eq(AuditAction.SOLICITUD_ASISTENCIA), eq(AuditResult.FALLO), eq(student),
                eq("checkin:" + request.getCheckinId()), eq("PENDIENTE_ENVIO"), eq(CORRELATION), context.capture());
        assertNull(context.getValue().eventoId());
        assertEquals("EDIFICIO", context.getValue().atributos().get("ubicacion_tipo"));
    }

    /**
     * A request that is no longer pending, or that another send has taken, is not sent nor audited
     */
    @Test
    void aRequestThatIsNotPendingIsNotSent() {
        SolicitudAsistencia request = pending();
        request.setEstadoEnvio(EstadoEnvio.ENVIADA);
        when(requests.tomarPendiente(request.getId())).thenReturn(Optional.empty());
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));

        EnvioSolicitud result = service.enviar(request.getId(), student, CORRELATION);

        assertSame(request, result.solicitud());
        assertNull(result.resultado());
        verifyNoInteractions(ms05, audit);
    }

    /**
     * Without a user in the request, the send uses the identity stored with the request and is audited as an action
     * of the system
     */
    @Test
    void withoutUserTheStoredIdentityIsUsedAndTheSystemIsAudited() {
        SolicitudAsistencia request = pending();
        takes(request);
        when(ms05.enviar(request, "ESTUDIANTE", "ms04:checkin:write", CORRELATION))
                .thenReturn(ResultadoEnvioMs05.creada("INC-205"));
        when(events.findById(EVENT)).thenReturn(Optional.of(drill()));

        service.enviar(request.getId(), null, CORRELATION);

        verify(audit).recordSystem(eq(AuditAction.SOLICITUD_ASISTENCIA), eq(AuditResult.EXITO),
                eq("checkin:" + request.getCheckinId()), eq("ENVIADA"), eq(CORRELATION), any(AuditContext.class));
        verify(audit, never()).recordAs(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * The request of a user in an event is read from its repository
     */
    @Test
    void theRequestOfAUserIsRead() {
        SolicitudAsistencia request = pending();
        when(requests.findByEventoIdAndUsuarioId(EVENT, USER)).thenReturn(Optional.of(request));

        assertSame(request, service.buscar(EVENT, USER).orElseThrow());
    }

    /**
     * The periodic resend sends the pending requests from the oldest one, each with a new correlation id, and a
     * failure with one of them does not stop the others
     */
    @Test
    void theResendSendsEveryPendingRequestWithANewCorrelationId() {
        SolicitudAsistencia first = pending();
        SolicitudAsistencia failing = pending();
        SolicitudAsistencia third = pending();
        when(requests.idsPorEstadoEnvio(EstadoEnvio.PENDIENTE_ENVIO))
                .thenReturn(List.of(first.getId(), failing.getId(), third.getId()));
        takes(first);
        when(requests.tomarPendiente(failing.getId())).thenThrow(new IllegalStateException("database"));
        takes(third);
        when(ms05.enviar(eq(first), eq("ESTUDIANTE"), eq("ms04:checkin:write"), anyString()))
                .thenReturn(ResultadoEnvioMs05.creada("INC-206"));
        when(ms05.enviar(eq(third), eq("ESTUDIANTE"), eq("ms04:checkin:write"), anyString()))
                .thenReturn(ResultadoEnvioMs05.noDisponible("timeout"));
        when(events.findById(EVENT)).thenReturn(Optional.of(drill()));

        int sent = service.reenviarPendientes();

        assertEquals(1, sent);
        ArgumentCaptor<String> correlations = ArgumentCaptor.forClass(String.class);
        verify(ms05, times(2)).enviar(any(SolicitudAsistencia.class), anyString(), anyString(),
                correlations.capture());
        String firstCorrelation = correlations.getAllValues().get(0);
        String secondCorrelation = correlations.getAllValues().get(1);
        assertEquals(firstCorrelation, UUID.fromString(firstCorrelation).toString());
        assertNotEquals(firstCorrelation, secondCorrelation);
        verify(audit, times(2)).recordSystem(eq(AuditAction.SOLICITUD_ASISTENCIA), any(AuditResult.class),
                anyString(), anyString(), anyString(), any(AuditContext.class));
    }

    /**
     * Without pending requests the resend sends nothing
     */
    @Test
    void withoutPendingRequestsNothingIsSent() {
        when(requests.idsPorEstadoEnvio(EstadoEnvio.PENDIENTE_ENVIO)).thenReturn(List.of());

        assertEquals(0, service.reenviarPendientes());

        verifyNoInteractions(ms05, audit);
    }
}
