package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * Audit records of MS04 for MS08: the identity of the user or of the system, the affected resource, the event with
 * its type and the attributes, and a failure of the publisher that never reaches the operation
 */
class AuditRecorderTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:04:12Z");
    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private AuditEventPublisher publisher;
    private AuditRecorder recorder;

    /**
     * Creates the recorder with a mocked publisher and a fixed clock
     */
    @BeforeEach
    void setUp() {
        publisher = mock(AuditEventPublisher.class);
        recorder = new AuditRecorder(publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * Captures the only published audit event
     *
     * @return the published event
     */
    private AuditEvent published() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }

    /**
     * A record of a user carries its identity with the roles in order
     */
    @Test
    void recordAsCarriesTheIdentityOfTheUser() {
        RequesterIdentity user = TestIdentities.identity(USER, "ESTUDIANTE", "BRIGADISTA");

        AuditContext context = new AuditContext(EVENT, true, Map.of()).con("estado", "SAFE");

        recorder.recordAs(AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, user, "evento:" + EVENT, null,
                CORRELATION, context);

        AuditEvent event = published();
        assertEquals(AuditAction.REGISTRO_CHECKIN, event.accion());
        assertEquals(AuditResult.EXITO, event.resultado());
        assertEquals(USER, event.usuarioId());
        assertEquals("BRIGADISTA,ESTUDIANTE", event.usuarioRoles());
        assertEquals(TestIdentities.permissions("BRIGADISTA,ESTUDIANTE"), event.usuarioPermisos());
        assertEquals("evento:" + EVENT, event.recursoAfectado());
        assertEquals(CORRELATION, event.correlationId());
        assertEquals(NOW, event.fecha());
        assertEquals(context, event.contexto());
        assertNotNull(event.id());
    }

    /**
     * A record of the system carries no user
     */
    @Test
    void recordSystemCarriesNoUser() {
        recorder.recordSystem(AuditAction.CAMBIO_ESTADO_EVENTO, AuditResult.FALLO, "evento:" + EVENT,
                "origen=ms06", CORRELATION, null);

        AuditEvent event = published();
        assertNull(event.usuarioId());
        assertNull(event.usuarioRoles());
        assertEquals("origen=ms06", event.detalle());
        assertEquals(AuditResult.FALLO, event.resultado());
        assertEquals(AuditContext.NINGUNO, event.contexto());
    }

    /**
     * When the publisher fails the audit does not propagate the exception
     */
    @Test
    void aFailureOfThePublisherIsNotPropagated() {
        doThrow(new IllegalStateException("MS08 caído")).when(publisher).publish(any());

        assertDoesNotThrow(() -> recorder.recordSystem(AuditAction.SOLICITUD_ASISTENCIA, AuditResult.EXITO,
                "checkin:" + USER, null, CORRELATION, AuditContext.NINGUNO));
    }

    /**
     * The resources are written as type, colon and id
     */
    @Test
    void theResourcesAreTypeColonId() {
        assertEquals("checkin:" + USER, AuditRecorder.checkinResource(USER));
        assertEquals("evento:" + EVENT, AuditRecorder.eventResource(EVENT));
        assertNull(AuditRecorder.checkinResource(null));
        assertNull(AuditRecorder.eventResource(null));
    }

    /**
     * The event requires id, action, result and date
     */
    @Test
    void theEventRequiresIdActionResultAndDate() {
        UUID id = UUID.randomUUID();
        assertThrows(NullPointerException.class,
                () -> new AuditEvent(null, AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, null, null, null, null,
                        null, NOW, null, null));
        assertThrows(NullPointerException.class,
                () -> new AuditEvent(id, null, AuditResult.EXITO, null, null, null, null, null, NOW, null, null));
        assertThrows(NullPointerException.class,
                () -> new AuditEvent(id, AuditAction.REGISTRO_CHECKIN, null, null, null, null, null, null, NOW, null,
                        null));
        assertThrows(NullPointerException.class,
                () -> new AuditEvent(id, AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, null, null, null, null,
                        null, null, null, null));
    }

    /**
     * The context requires the event and its type together and leaves out the null attributes
     */
    @Test
    void theContextRequiresEventAndTypeTogetherAndSkipsNullAttributes() {
        assertThrows(IllegalArgumentException.class, () -> new AuditContext(EVENT, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AuditContext(null, false, Map.of()));

        AuditContext context = new AuditContext(EVENT, false, null)
                .con("estado", "SAFE")
                .con("ubicacion_id", null);

        assertEquals("REAL", context.tipoEvento());
        assertEquals(Map.of("estado", "SAFE"), context.atributos());
        assertNull(AuditContext.NINGUNO.tipoEvento());
    }
}
