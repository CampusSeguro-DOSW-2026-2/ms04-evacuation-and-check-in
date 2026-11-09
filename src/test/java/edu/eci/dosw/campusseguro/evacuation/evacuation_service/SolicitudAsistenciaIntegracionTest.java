package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentro;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentroClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ResultadoEnvioMs05;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.SolicitudAsistenciaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.SolicitudAsistenciaService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * RF-MS04-04 Generar solicitud de asistencia en MS05, de punta a punta con PostgreSQL real. MS05, MS02 y el envío a
 * MS08 se reemplazan por dobles. El reenvío periódico se aleja una hora para que no corra durante las pruebas; las
 * pruebas lo invocan directamente
 */
@SpringBootTest(properties = "solicitudes-asistencia.reenvio-ms=3600000")
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
class SolicitudAsistenciaIntegracionTest {

    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final String CORRELATION_ID = "7d2f4b1a-9c3e-4a5b-8d6f-0e1a2b3c4d5e";
    private static final UUID EDIFICIO = UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a");
    private static final UUID ESPACIO = UUID.fromString("6e5d4c3b-2a1f-4e0d-9c8b-7a6f5e4d3c2b");
    private static final UUID PUNTO = UUID.fromString("0b6f1d2e-3c4a-4b5d-8e9f-a1b2c3d4e5f6");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SolicitudAsistenciaService service;

    @MockitoBean
    private SolicitudAsistenciaClient ms05;

    @MockitoBean
    private PuntoEncuentroClient puntosEncuentro;

    @MockitoBean
    private AuditEventPublisher auditoria;

    @BeforeEach
    void dobles() {
        when(puntosEncuentro.buscar(any(), any(), any())).thenAnswer(inv -> Optional.of(
                new PuntoEncuentro(inv.getArgument(0), "MP-01", "Punto", true)));
        when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.creada("SOL-1"));
    }

    private Evento enEjecucion() {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Simulacro de evacuación");
        evento.setSimulacro(true);
        evento.setEstado(EstadoEvento.EN_EJECUCION);
        evento.setFechaInicio(Instant.parse("2026-11-08T10:00:00Z"));
        evento.setRolesConvocados(List.of("ESTUDIANTE"));
        return eventos.saveAndFlush(evento);
    }

    private void finalizar(Evento evento) {
        Evento finalizado = eventos.findById(evento.getId()).orElseThrow();
        finalizado.setEstado(EstadoEvento.FINALIZADO);
        finalizado.setFechaCierre(Instant.parse("2026-11-08T12:00:00Z"));
        eventos.saveAndFlush(finalizado);
    }

    private ResultActions reportar(UUID eventoId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/chi/eventos/{id}/checkins", eventoId)
                .header(IdentityHeaders.USER_ID, USUARIO.toString())
                .header(IdentityHeaders.USER_ROLE, "ESTUDIANTE")
                .header("X-Correlation-ID", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private static String asistencia(String tipo, UUID id) {
        return "{\"estado\":\"NEEDS_ASSISTANCE\",\"ubicacion\":{\"tipo\":\"" + tipo + "\",\"id\":\"" + id + "\"}}";
    }

    private static String soloEstado(String estado) {
        return "{\"estado\":\"" + estado + "\"}";
    }

    private List<Map<String, Object>> solicitudes(UUID eventoId) {
        return jdbc.queryForList("SELECT * FROM solicitudes_asistencia WHERE evento_id = ? AND usuario_id = ?",
                eventoId, USUARIO);
    }

    private Map<String, Object> unicaSolicitud(UUID eventoId) {
        List<Map<String, Object>> filas = solicitudes(eventoId);
        assertEquals(1, filas.size());
        return filas.get(0);
    }

    private List<AuditEvent> auditados(AuditAction accion) {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditoria, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues().stream().filter(e -> e.accion() == accion).toList();
    }

    /** Solicitud generada con el check-in Requiero asistencia */
    @Nested
    @Transactional
    class ConElCheckIn {

        @Test
        void requieroAsistenciaCreaLaSolicitudEnMs05YConservaSuIdAsociadoAlCheckIn() throws Exception {
            Evento evento = enEjecucion();

            String respuesta = reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.solicitud_asistencia.id").value("SOL-1"))
                    .andExpect(jsonPath("$.solicitud_asistencia.estado_envio").value("ENVIADA"))
                    .andReturn().getResponse().getContentAsString();

            Map<String, Object> fila = unicaSolicitud(evento.getId());
            assertEquals(JsonPath.read(respuesta, "$.id"), fila.get("checkin_id").toString());
            assertEquals("SOL-1", fila.get("solicitud_ms05_id"));
            assertEquals("ENVIADA", fila.get("estado_envio"));
            assertEquals(1, fila.get("intentos"));
            assertEquals("EDIFICIO", fila.get("ubicacion_tipo"));
            assertEquals(EDIFICIO, fila.get("ubicacion_id"));
        }

        @Test
        void elEnvioAMs05LlevaLaSolicitudLosRolesDelUsuarioYElCorrelationId() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), asistencia("ESPACIO", ESPACIO)).andExpect(status().isCreated());

            ArgumentCaptor<SolicitudAsistencia> enviada = ArgumentCaptor.forClass(SolicitudAsistencia.class);
            verify(ms05).enviar(enviada.capture(), eq("ESTUDIANTE"), eq(CORRELATION_ID));
            assertEquals(unicaSolicitud(evento.getId()).get("id"), enviada.getValue().getId());
            assertEquals(USUARIO, enviada.getValue().getUsuarioId());
            assertEquals(ESPACIO, enviada.getValue().getUbicacionId());
        }

        @Test
        void conMs05DetenidoElCheckInSeRegistraYLaSolicitudQuedaPendiente() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("timeout"));

            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("NEEDS_ASSISTANCE"))
                    .andExpect(jsonPath("$.solicitud_asistencia.id").value(nullValue()))
                    .andExpect(jsonPath("$.solicitud_asistencia.estado_envio").value("PENDIENTE_ENVIO"));

            Map<String, Object> fila = unicaSolicitud(evento.getId());
            assertEquals("PENDIENTE_ENVIO", fila.get("estado_envio"));
            assertEquals(1, fila.get("intentos"));
            assertNull(fila.get("solicitud_ms05_id"));
        }

        @Test
        void unErrorInesperadoAlEnviarNoRechazaElCheckIn() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenThrow(new IllegalStateException("fallo inesperado"));

            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.solicitud_asistencia.estado_envio").value("PENDIENTE_ENVIO"));
        }

        @Test
        void siMs05RechazaLaSolicitudQuedaRechazadaYElCheckInSeConserva() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.rechazada("HTTP 422"));

            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.solicitud_asistencia.estado_envio").value("RECHAZADA"));

            assertEquals("RECHAZADA", unicaSolicitud(evento.getId()).get("estado_envio"));
        }

        @Test
        void unSegundoRequieroAsistenciaEnElMismoEventoNoCreaOtraSolicitud() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO)).andExpect(status().isCreated());
            reportar(evento.getId(), soloEstado("EVACUATING")).andExpect(status().isOk());

            reportar(evento.getId(), asistencia("ESPACIO", ESPACIO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ubicacion.tipo").value("ESPACIO"))
                    .andExpect(jsonPath("$.solicitud_asistencia.id").value("SOL-1"))
                    .andExpect(jsonPath("$.solicitud_asistencia.estado_envio").value("ENVIADA"));

            assertEquals(EDIFICIO, unicaSolicitud(evento.getId()).get("ubicacion_id"));
            verify(ms05, times(1)).enviar(any(), any(), any());
        }

        @Test
        void repetirRequieroAsistenciaDevuelveLaSolicitudExistente() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO)).andExpect(status().isCreated());

            reportar(evento.getId(), asistencia("ESPACIO", ESPACIO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.solicitud_asistencia.id").value("SOL-1"));

            assertEquals(1, solicitudes(evento.getId()).size());
            verify(ms05, times(1)).enviar(any(), any(), any());
        }

        @Test
        void losOtrosEstadosNoGeneranSolicitud() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), soloEstado("EVACUATING"))
                    .andExpect(jsonPath("$.solicitud_asistencia").value(nullValue()));
            reportar(evento.getId(), "{\"estado\":\"SAFE\",\"punto_encuentro_id\":\"" + PUNTO + "\"}")
                    .andExpect(jsonPath("$.solicitud_asistencia").value(nullValue()));
            reportar(evento.getId(), soloEstado("OFF_CAMPUS"))
                    .andExpect(jsonPath("$.solicitud_asistencia").value(nullValue()));

            assertEquals(0, solicitudes(evento.getId()).size());
            verify(ms05, never()).enviar(any(), any(), any());
        }

        @Test
        void unCheckInRechazadoNoGeneraSolicitud() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), "{\"estado\":\"NEEDS_ASSISTANCE\"}").andExpect(status().isBadRequest());

            assertEquals(0, solicitudes(evento.getId()).size());
            verify(ms05, never()).enviar(any(), any(), any());
        }
    }

    /** Reenvío de las solicitudes pendientes (RN-MS04-12) */
    @Nested
    @Transactional
    class Reenvio {

        @Test
        void alRecuperarseMs05LaPendienteSeEnviaUnaSolaVezYSeGuardaSuId() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("caído"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO)).andExpect(status().isCreated());

            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.creada("SOL-9"));
            assertEquals(1, service.reenviarPendientes());
            assertEquals(0, service.reenviarPendientes());

            Map<String, Object> fila = unicaSolicitud(evento.getId());
            assertEquals("ENVIADA", fila.get("estado_envio"));
            assertEquals("SOL-9", fila.get("solicitud_ms05_id"));
            assertEquals(2, fila.get("intentos"));
            verify(ms05, times(2)).enviar(any(), any(), any());
        }

        @Test
        void elReenvioPropagaElUsuarioPeroNoRolesYUsaUnCorrelationIdNuevo() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("caído"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));

            service.reenviarPendientes();

            ArgumentCaptor<String> correlation = ArgumentCaptor.forClass(String.class);
            verify(ms05).enviar(any(), isNull(), correlation.capture());
            assertTrue(!CORRELATION_ID.equals(correlation.getValue()));
            assertEquals(4, UUID.fromString(correlation.getValue()).version());
        }

        @Test
        void mientrasMs05SigaCaidoLaSolicitudSiguePendienteYCuentaLosIntentos() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("caído"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));

            service.reenviarPendientes();
            service.reenviarPendientes();

            Map<String, Object> fila = unicaSolicitud(evento.getId());
            assertEquals("PENDIENTE_ENVIO", fila.get("estado_envio"));
            assertEquals(3, fila.get("intentos"));
        }

        @Test
        void unaSolicitudRechazadaNoSeReenvia() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.rechazada("HTTP 400"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));

            assertEquals(0, service.reenviarPendientes());

            verify(ms05, times(1)).enviar(any(), any(), any());
            assertEquals("RECHAZADA", unicaSolicitud(evento.getId()).get("estado_envio"));
        }

        @Test
        void siElUsuarioCambiaDeEstadoLaPendienteSeEnviaIgual() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("caído"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));
            reportar(evento.getId(), soloEstado("EVACUATING")).andExpect(status().isOk());

            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.creada("SOL-2"));
            assertEquals(1, service.reenviarPendientes());
            assertEquals("ENVIADA", unicaSolicitud(evento.getId()).get("estado_envio"));
        }
    }

    /** Auditoría de cada intento de envío */
    @Nested
    @Transactional
    class Auditoria {

        @Test
        void unEnvioExitosoSeAuditaConElUsuarioSobreElCheckIn() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));

            List<AuditEvent> eventosAuditados = auditados(AuditAction.SOLICITUD_ASISTENCIA);
            assertEquals(1, eventosAuditados.size());
            AuditEvent e = eventosAuditados.get(0);
            assertEquals(AuditResult.EXITO, e.resultado());
            assertEquals("ENVIADA", e.detalle());
            assertEquals(USUARIO, e.usuarioId());
            assertEquals("ESTUDIANTE", e.usuarioRoles());
            assertEquals(CORRELATION_ID, e.correlationId());
            assertEquals("checkin:" + unicaSolicitud(evento.getId()).get("checkin_id"), e.recursoAfectado());
        }

        @Test
        void unEnvioFallidoSeAuditaComoFalloConElEstadoEnQueQueda() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("caído"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));

            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.rechazada("HTTP 422"));
            service.reenviarPendientes();

            List<AuditEvent> eventosAuditados = auditados(AuditAction.SOLICITUD_ASISTENCIA);
            assertEquals(2, eventosAuditados.size());
            assertEquals(AuditResult.FALLO, eventosAuditados.get(0).resultado());
            assertEquals("PENDIENTE_ENVIO", eventosAuditados.get(0).detalle());
            assertEquals(AuditResult.FALLO, eventosAuditados.get(1).resultado());
            assertEquals("RECHAZADA", eventosAuditados.get(1).detalle());
        }

        @Test
        void elReenvioSeAuditaComoAccionDelSistema() throws Exception {
            Evento evento = enEjecucion();
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("caído"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));

            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.creada("SOL-3"));
            service.reenviarPendientes();

            List<AuditEvent> eventosAuditados = auditados(AuditAction.SOLICITUD_ASISTENCIA);
            AuditEvent reenvio = eventosAuditados.get(eventosAuditados.size() - 1);
            assertEquals(AuditResult.EXITO, reenvio.resultado());
            assertNull(reenvio.usuarioId());
            assertNull(reenvio.usuarioRoles());
        }

        @Test
        void unaSolicitudQueNoSeIntentaNoSeAudita() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));

            service.reenviarPendientes();

            assertEquals(1, auditados(AuditAction.SOLICITUD_ASISTENCIA).size());
        }
    }

    /**
     * Dos reenvíos simultáneos de la misma pendiente (p. ej. dos instancias de MS04): el bloqueo de fila hace que solo
     * uno la envíe. Sin transacción de prueba, cada envío confirma la suya; al final la solicitud queda ENVIADA para
     * no dejar pendientes en la base, y el evento se finaliza
     */
    @Test
    void dosReenviosSimultaneosEnvianLaPendienteUnaSolaVez() throws Exception {
        Evento evento = enEjecucion();
        try {
            when(ms05.enviar(any(), any(), any())).thenReturn(ResultadoEnvioMs05.noDisponible("caído"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO)).andExpect(status().isCreated());
            UUID solicitudId = (UUID) unicaSolicitud(evento.getId()).get("id");

            AtomicInteger llamadas = new AtomicInteger();
            CountDownLatch enMs05 = new CountDownLatch(1);
            CountDownLatch soltar = new CountDownLatch(1);
            // Solo el primer envío se queda "dentro de MS05" hasta que el segundo termine
            when(ms05.enviar(any(), any(), anyString())).thenAnswer(inv -> {
                if (llamadas.incrementAndGet() == 1) {
                    enMs05.countDown();
                    soltar.await(10, TimeUnit.SECONDS);
                }
                return ResultadoEnvioMs05.creada("SOL-UNICA");
            });

            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> primero = pool.submit(() -> service.enviar(solicitudId, null, UUID.randomUUID().toString()));
                assertTrue(enMs05.await(10, TimeUnit.SECONDS));
                SolicitudAsistenciaService.EnvioSolicitud segundo = pool.submit(
                        () -> service.enviar(solicitudId, null, UUID.randomUUID().toString()))
                        .get(10, TimeUnit.SECONDS);
                soltar.countDown();
                primero.get(10, TimeUnit.SECONDS);

                assertNull(segundo.resultado());
                assertEquals(1, llamadas.get());
            } finally {
                soltar.countDown();
                pool.shutdownNow();
            }
            Map<String, Object> fila = unicaSolicitud(evento.getId());
            assertEquals("ENVIADA", fila.get("estado_envio"));
            assertEquals("SOL-UNICA", fila.get("solicitud_ms05_id"));
        } finally {
            finalizar(evento);
        }
    }
}
