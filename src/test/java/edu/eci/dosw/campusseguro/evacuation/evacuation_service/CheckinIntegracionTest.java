package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditEvent;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentro;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PuntoEncuentroClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.CheckinService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * RF-MS04-02 Registrar check-in y RF-MS04-03 Mantener y actualizar el estado vigente, de punta a punta con
 * PostgreSQL real. MS02 y el envío a MS08 se reemplazan por dobles
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
class CheckinIntegracionTest {

    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final String CORRELATION_ID = "7d2f4b1a-9c3e-4a5b-8d6f-0e1a2b3c4d5e";
    private static final UUID PUNTO_A = UUID.fromString("0b6f1d2e-3c4a-4b5d-8e9f-a1b2c3d4e5f6");
    private static final UUID PUNTO_B = UUID.fromString("1c7a2e3f-4d5b-4c6e-9fa0-b2c3d4e5f6a7");
    private static final UUID EDIFICIO = UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a");
    private static final UUID ESPACIO = UUID.fromString("6e5d4c3b-2a1f-4e0d-9c8b-7a6f5e4d3c2b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CheckinService service;

    @MockitoBean
    private PuntoEncuentroClient puntosEncuentro;

    @MockitoBean
    private AuditEventPublisher auditoria;

    @BeforeEach
    void puntosActivos() {
        when(puntosEncuentro.buscar(any(), any(), any())).thenAnswer(inv -> Optional.of(
                new PuntoEncuentro(inv.getArgument(0), "MP-01", "Punto", true)));
    }

    private Evento evento(EstadoEvento estado, List<String> roles) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Simulacro de evacuación");
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(estado == EstadoEvento.BORRADOR ? null : Instant.parse("2026-11-08T10:00:00Z"));
        evento.setFechaCierre(estado == EstadoEvento.FINALIZADO ? Instant.parse("2026-11-08T12:00:00Z") : null);
        evento.setRolesConvocados(roles);
        return eventos.saveAndFlush(evento);
    }

    private Evento enEjecucion() {
        return evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE", "PROFESOR"));
    }

    private ResultActions reportar(UUID eventoId, String roles, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/chi/eventos/{id}/checkins", eventoId)
                .header(IdentityHeaders.USER_ID, USUARIO.toString())
                .header(IdentityHeaders.USER_ROLE, roles)
                .header("X-Correlation-ID", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions reportar(UUID eventoId, String cuerpo) throws Exception {
        return reportar(eventoId, "ESTUDIANTE", cuerpo);
    }

    private static String safe(UUID punto) {
        return "{\"estado\":\"SAFE\",\"punto_encuentro_id\":\"" + punto + "\"}";
    }

    private static String asistencia(String tipo, UUID id) {
        return "{\"estado\":\"NEEDS_ASSISTANCE\",\"ubicacion\":{\"tipo\":\"" + tipo + "\",\"id\":\"" + id + "\"}}";
    }

    private static String soloEstado(String estado) {
        return "{\"estado\":\"" + estado + "\"}";
    }

    private long filas(UUID eventoId) {
        return jdbc.queryForObject("SELECT count(*) FROM checkins WHERE evento_id = ? AND usuario_id = ?",
                Long.class, eventoId, USUARIO);
    }

    private String estadoVigente(UUID eventoId) {
        return jdbc.queryForObject("SELECT estado FROM v_checkins_vigentes WHERE evento_id = ? AND usuario_id = ?",
                String.class, eventoId, USUARIO);
    }

    private List<AuditEvent> auditados() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditoria, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
    }

    private AuditEvent ultimoAuditado() {
        List<AuditEvent> todos = auditados();
        return todos.get(todos.size() - 1);
    }

    /** RF-MS04-02: registro del check-in */
    @Nested
    @Transactional
    class Registro {

        @Test
        void elPrimerCheckInSeGuardaYDevuelve201ConElContrato() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), safe(PUNTO_A))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.evento_id").value(evento.getId().toString()))
                    .andExpect(jsonPath("$.estado").value("SAFE"))
                    .andExpect(jsonPath("$.punto_encuentro_id").value(PUNTO_A.toString()))
                    .andExpect(jsonPath("$.ubicacion").value(nullValue()))
                    .andExpect(jsonPath("$.solicitud_asistencia").value(nullValue()))
                    .andExpect(jsonPath("$.fecha_hora").isNotEmpty());

            assertEquals(1, filas(evento.getId()));
            assertEquals("SAFE", estadoVigente(evento.getId()));
        }

        @Test
        void estoyASalvoValidaElPuntoConMs02ConLaIdentidadYElCorrelationId() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), safe(PUNTO_A)).andExpect(status().isCreated());

            ArgumentCaptor<RequesterIdentity> quien = ArgumentCaptor.forClass(RequesterIdentity.class);
            verify(puntosEncuentro).buscar(eq(PUNTO_A), quien.capture(), eq(CORRELATION_ID));
            assertEquals(USUARIO, quien.getValue().userId());
        }

        @Test
        void losEstadosSinPuntoNoConsultanMs02() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), soloEstado("EVACUATING")).andExpect(status().isCreated());
            reportar(evento.getId(), soloEstado("OFF_CAMPUS")).andExpect(status().isOk());
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO)).andExpect(status().isOk());

            verify(puntosEncuentro, never()).buscar(any(), any(), any());
        }

        @Test
        void requieroAsistenciaGuardaLaUbicacion() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), asistencia("ESPACIO", ESPACIO))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("NEEDS_ASSISTANCE"))
                    .andExpect(jsonPath("$.punto_encuentro_id").value(nullValue()))
                    .andExpect(jsonPath("$.ubicacion.tipo").value("ESPACIO"))
                    .andExpect(jsonPath("$.ubicacion.id").value(ESPACIO.toString()));
        }

        @Test
        void unEventoQueNoExisteEs404() throws Exception {
            reportar(UUID.randomUUID(), soloEstado("EVACUATING"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                    .andExpect(jsonPath("$.mensaje").value(CheckinService.EVENTO_NO_ENCONTRADO));
        }

        @Test
        void unEventoQueNoEstaEnEjecucionEs409YNoGuardaNada() throws Exception {
            for (EstadoEvento estado : new EstadoEvento[] { EstadoEvento.BORRADOR, EstadoEvento.PROGRAMADO,
                    EstadoEvento.FINALIZADO }) {
                Evento evento = evento(estado, List.of("ESTUDIANTE"));
                reportar(evento.getId(), soloEstado("EVACUATING"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                        .andExpect(jsonPath("$.mensaje").value(CheckinService.CHECKIN_NO_HABILITADO));
                assertEquals(0, filas(evento.getId()));
            }
        }

        @Test
        void unEventoQueNoConvocaElRolDelUsuarioEs422() throws Exception {
            Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("BRIGADISTA"));

            reportar(evento.getId(), soloEstado("EVACUATING"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                    .andExpect(jsonPath("$.mensaje").value(CheckinService.EVENTO_NO_APLICA));
            assertEquals(0, filas(evento.getId()));
        }

        @Test
        void conVariosRolesBastaConQueUnoEsteConvocado() throws Exception {
            Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("BRIGADISTA"));

            reportar(evento.getId(), "BRIGADISTA,ESTUDIANTE", soloEstado("EVACUATING"))
                    .andExpect(status().isCreated());
        }

        @Test
        void unPuntoQueNoExisteEnMs02Es404() throws Exception {
            Evento evento = enEjecucion();
            when(puntosEncuentro.buscar(any(), any(), any())).thenReturn(Optional.empty());

            reportar(evento.getId(), safe(PUNTO_A))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(CheckinService.PUNTO_NO_ENCONTRADO));
            assertEquals(0, filas(evento.getId()));
        }

        @Test
        void unPuntoInactivoEs422() throws Exception {
            Evento evento = enEjecucion();
            when(puntosEncuentro.buscar(any(), any(), any()))
                    .thenReturn(Optional.of(new PuntoEncuentro(PUNTO_A, "MP-01", "Punto", false)));

            reportar(evento.getId(), safe(PUNTO_A))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                    .andExpect(jsonPath("$.mensaje").value(CheckinService.PUNTO_INACTIVO));
            assertEquals(0, filas(evento.getId()));
        }

        @Test
        void siMs02NoRespondeEs503YNoGuardaNada() throws Exception {
            Evento evento = enEjecucion();
            when(puntosEncuentro.buscar(any(), any(), any())).thenThrow(ApiException.dependenciaNoDisponible());

            reportar(evento.getId(), safe(PUNTO_A))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"));
            assertEquals(0, filas(evento.getId()));
        }

        @Test
        void unEventoQueTerminaMientrasSeConsultaMs02SeRechazaDentroDeLaTransaccion() throws Exception {
            Evento evento = enEjecucion();
            when(puntosEncuentro.buscar(any(), any(), any())).thenAnswer(inv -> {
                Evento finalizado = eventos.findById(evento.getId()).orElseThrow();
                finalizado.setEstado(EstadoEvento.FINALIZADO);
                finalizado.setFechaCierre(Instant.parse("2026-11-08T12:00:00Z"));
                eventos.saveAndFlush(finalizado);
                return Optional.of(new PuntoEncuentro(PUNTO_A, "MP-01", "Punto", true));
            });

            reportar(evento.getId(), safe(PUNTO_A))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensaje").value(CheckinService.CHECKIN_NO_HABILITADO));
            assertEquals(0, filas(evento.getId()));
        }

        @Test
        void unCuerpoInvalidoEs400ConElDetalleDelCampo() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), soloEstado("SAFE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                    .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"));
            assertEquals(0, filas(evento.getId()));
        }

        @Test
        void unIdDeUsuarioEnElCuerpoSeRechaza() throws Exception {
            Evento evento = enEjecucion();

            reportar(evento.getId(), "{\"estado\":\"EVACUATING\",\"usuario_id\":\"" + UUID.randomUUID() + "\"}")
                    .andExpect(status().isBadRequest());
            assertEquals(0, filas(evento.getId()));
        }
    }

    /** RF-MS04-03: estado vigente */
    @Nested
    @Transactional
    class EstadoVigente {

        @Test
        void unEstadoDistintoAgregaUnCheckInNuevoYDevuelve200() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), soloEstado("EVACUATING")).andExpect(status().isCreated());

            reportar(evento.getId(), safe(PUNTO_A))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("SAFE"));

            assertEquals(2, filas(evento.getId()));
            assertEquals("SAFE", estadoVigente(evento.getId()));
        }

        @Test
        void elMismoEstadoNoAgregaNadaYDevuelveElVigente() throws Exception {
            Evento evento = enEjecucion();
            String id = com.jayway.jsonpath.JsonPath.read(reportar(evento.getId(), soloEstado("EVACUATING"))
                    .andReturn().getResponse().getContentAsString(), "$.id");

            reportar(evento.getId(), soloEstado("EVACUATING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id));

            assertEquals(1, filas(evento.getId()));
        }

        @Test
        void estoyASalvoEnOtroPuntoEsUnCheckInNuevo() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), safe(PUNTO_A)).andExpect(status().isCreated());

            reportar(evento.getId(), safe(PUNTO_A)).andExpect(status().isOk());
            assertEquals(1, filas(evento.getId()));

            reportar(evento.getId(), safe(PUNTO_B))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.punto_encuentro_id").value(PUNTO_B.toString()));
            assertEquals(2, filas(evento.getId()));
        }

        @Test
        void requieroAsistenciaConOtraUbicacionSigueSiendoElMismoEstado() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO)).andExpect(status().isCreated());

            reportar(evento.getId(), asistencia("ESPACIO", ESPACIO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ubicacion.tipo").value("EDIFICIO"))
                    .andExpect(jsonPath("$.ubicacion.id").value(EDIFICIO.toString()));

            assertEquals(1, filas(evento.getId()));
        }

        @Test
        void elHistorialConservaTodosLosCambiosYElVigenteEsElUltimo() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), soloEstado("EVACUATING"));
            reportar(evento.getId(), asistencia("EDIFICIO", EDIFICIO));
            reportar(evento.getId(), safe(PUNTO_A));

            assertEquals(List.of("EVACUATING", "NEEDS_ASSISTANCE", "SAFE"), jdbc.queryForList(
                    "SELECT estado FROM checkins WHERE evento_id = ? AND usuario_id = ? ORDER BY numero",
                    String.class, evento.getId(), USUARIO));
            assertEquals("SAFE", estadoVigente(evento.getId()));
        }

        @Test
        void elEstadoDeUnEventoNoAfectaAOtro() throws Exception {
            Evento primero = enEjecucion();
            Evento segundo = enEjecucion();
            reportar(primero.getId(), soloEstado("EVACUATING")).andExpect(status().isCreated());

            reportar(segundo.getId(), soloEstado("EVACUATING")).andExpect(status().isCreated());

            assertEquals(1, filas(primero.getId()));
            assertEquals(1, filas(segundo.getId()));
        }
    }

    /** Auditoría de cada intento */
    @Nested
    @Transactional
    class Auditoria {

        @Test
        void elPrimerCheckInSeAuditaComoRegistro() throws Exception {
            Evento evento = enEjecucion();
            String id = com.jayway.jsonpath.JsonPath.read(reportar(evento.getId(), safe(PUNTO_A))
                    .andReturn().getResponse().getContentAsString(), "$.id");

            AuditEvent e = ultimoAuditado();
            assertEquals(AuditAction.REGISTRO_CHECKIN, e.accion());
            assertEquals(AuditResult.EXITO, e.resultado());
            assertEquals("checkin:" + id, e.recursoAfectado());
            assertEquals(USUARIO, e.usuarioId());
            assertEquals("ESTUDIANTE", e.usuarioRoles());
            assertEquals(CORRELATION_ID, e.correlationId());
        }

        @Test
        void unCambioSeAuditaConElEstadoAnteriorYElNuevo() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), soloEstado("EVACUATING"));
            reportar(evento.getId(), safe(PUNTO_A));

            AuditEvent e = ultimoAuditado();
            assertEquals(AuditAction.CAMBIO_ESTADO_CHECKIN, e.accion());
            assertEquals(AuditResult.EXITO, e.resultado());
            assertEquals("EVACUATING->SAFE", e.detalle());
        }

        @Test
        void repetirElVigenteSeAuditaSinCambio() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), soloEstado("EVACUATING"));
            reportar(evento.getId(), soloEstado("EVACUATING"));

            AuditEvent e = ultimoAuditado();
            assertEquals(AuditAction.CAMBIO_ESTADO_CHECKIN, e.accion());
            assertEquals(CheckinService.SIN_CAMBIO, e.detalle());
        }

        @Test
        void unIntentoFallidoSeAuditaSobreElEventoConElCodigoDelError() throws Exception {
            Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("BRIGADISTA"));
            reportar(evento.getId(), soloEstado("EVACUATING")).andExpect(status().isUnprocessableContent());

            AuditEvent e = ultimoAuditado();
            assertEquals(AuditAction.REGISTRO_CHECKIN, e.accion());
            assertEquals(AuditResult.FALLO, e.resultado());
            assertEquals("evento:" + evento.getId(), e.recursoAfectado());
            assertEquals("REGLA_NEGOCIO", e.detalle());
        }

        @Test
        void unIntentoFallidoConEstadoVigenteSeAuditaComoCambio() throws Exception {
            Evento evento = enEjecucion();
            reportar(evento.getId(), soloEstado("EVACUATING")).andExpect(status().isCreated());
            when(puntosEncuentro.buscar(any(), any(), any())).thenThrow(ApiException.dependenciaNoDisponible());

            reportar(evento.getId(), safe(PUNTO_A)).andExpect(status().isServiceUnavailable());

            AuditEvent e = ultimoAuditado();
            assertEquals(AuditAction.CAMBIO_ESTADO_CHECKIN, e.accion());
            assertEquals(AuditResult.FALLO, e.resultado());
            assertEquals("DEPENDENCIA_NO_DISPONIBLE", e.detalle());
        }

        @Test
        void unaFallaDeAuditoriaNoCambiaLaRespuesta() throws Exception {
            Evento evento = enEjecucion();
            org.mockito.Mockito.doThrow(new IllegalStateException("MS08 caído")).when(auditoria).publish(any());

            reportar(evento.getId(), soloEstado("EVACUATING")).andExpect(status().isCreated());
            assertEquals(1, filas(evento.getId()));
        }
    }

    /**
     * Reportes simultáneos del mismo usuario en el mismo evento (RNF-MS04-04): sin transacción de prueba, cada
     * reporte confirma la suya. El evento se finaliza al terminar para no dejar un evento activo en la base
     */
    @Test
    void reportesSimultaneosDelMismoEstadoDejanUnSoloCheckIn() throws Exception {
        Evento evento = enEjecucion();
        RequesterIdentity usuario = new RequesterIdentity(USUARIO, Set.of(Rol.ESTUDIANTE));
        CheckinRequest request = new CheckinRequest(EstadoCheckin.SAFE, PUNTO_A, null);
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<ResultadoCheckin>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                Callable<ResultadoCheckin> tarea = () -> {
                    salida.await();
                    return service.registrar(evento.getId(), request, usuario, "corr-" + UUID.randomUUID());
                };
                resultados.add(pool.submit(tarea));
            }
            salida.countDown();
            int creados = 0;
            for (Future<ResultadoCheckin> resultado : resultados) {
                if (resultado.get().creado()) {
                    creados++;
                }
            }

            assertEquals(1, creados);
            assertEquals(1, filas(evento.getId()));
        } finally {
            pool.shutdownNow();
            Evento finalizado = eventos.findById(evento.getId()).orElseThrow();
            finalizado.setEstado(EstadoEvento.FINALIZADO);
            finalizado.setFechaCierre(Instant.parse("2026-11-08T12:00:00Z"));
            eventos.saveAndFlush(finalizado);
        }
    }
}
