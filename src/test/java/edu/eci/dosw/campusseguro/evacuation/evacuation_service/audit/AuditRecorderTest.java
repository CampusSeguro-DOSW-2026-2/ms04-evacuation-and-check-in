package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;

class AuditRecorderTest {

    private static final Instant AHORA = Instant.parse("2026-11-20T15:04:12Z");
    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private AuditEventPublisher publicador;
    private AuditRecorder auditor;

    @BeforeEach
    void preparar() {
        publicador = mock(AuditEventPublisher.class);
        auditor = new AuditRecorder(publicador, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private AuditEvent publicado() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publicador).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordAsLlevaLaIdentidadDelUsuarioConSusRolesOrdenados() {
        RequesterIdentity usuario = new RequesterIdentity(USUARIO, Set.of(Rol.ESTUDIANTE, Rol.BRIGADISTA));

        auditor.recordAs(AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, usuario, "evento:" + EVENTO, null,
                CORRELATION);

        AuditEvent evento = publicado();
        assertEquals(AuditAction.REGISTRO_CHECKIN, evento.accion());
        assertEquals(AuditResult.EXITO, evento.resultado());
        assertEquals(USUARIO, evento.usuarioId());
        assertEquals("BRIGADISTA,ESTUDIANTE", evento.usuarioRoles());
        assertEquals("evento:" + EVENTO, evento.recursoAfectado());
        assertEquals(CORRELATION, evento.correlationId());
        assertEquals(AHORA, evento.fecha());
    }

    @Test
    void recordSystemNoLlevaUsuario() {
        auditor.recordSystem(AuditAction.CAMBIO_ESTADO_EVENTO, AuditResult.FALLO, "evento:" + EVENTO,
                "origen=ms06", CORRELATION);

        AuditEvent evento = publicado();
        assertNull(evento.usuarioId());
        assertNull(evento.usuarioRoles());
        assertEquals("origen=ms06", evento.detalle());
        assertEquals(AuditResult.FALLO, evento.resultado());
    }

    @Test
    void siElPublicadorFallaLaAuditoriaNoPropagaLaExcepcion() {
        doThrow(new IllegalStateException("MS08 caído")).when(publicador).publish(any());

        assertDoesNotThrow(() -> auditor.recordSystem(AuditAction.SOLICITUD_ASISTENCIA, AuditResult.EXITO,
                "checkin:" + USUARIO, null, CORRELATION));
    }

    @Test
    void losRecursosSeArmanComoTipoDosPuntosId() {
        assertEquals("checkin:" + USUARIO, AuditRecorder.checkinResource(USUARIO));
        assertEquals("evento:" + EVENTO, AuditRecorder.eventResource(EVENTO));
        assertNull(AuditRecorder.checkinResource(null));
        assertNull(AuditRecorder.eventResource(null));
    }

    @Test
    void elEventoExigeAccionResultadoYFecha() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new AuditEvent(null, AuditResult.EXITO, null, null, null, null, AHORA, null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new AuditEvent(AuditAction.REGISTRO_CHECKIN, null, null, null, null, null, AHORA, null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new AuditEvent(AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, null, null, null, null, null,
                        null));
    }
}
