package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditAction;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditResult;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoEventoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoEventoValidator;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;
import jakarta.persistence.EntityManager;

/**
 * RF-MS04-11 Recibir el estado del evento (historia KAIR-588), de punta a punta: la petición de MS06 con
 * X-Service-Name, la validación de F-05, las transiciones de RN-MS04-21 sobre PostgreSQL real, la habilitación
 * del check-in y la auditoría hacia MS08. Cada prueba se deshace al terminar
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
@Transactional
class EstadoEventoIntegracionTest {

    private static final String BASE = "/api/v1/chi/eventos/";
    private static final String ESTUDIANTE = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String INICIO = "2026-11-20T10:00:00-05:00";
    private static final Instant INICIO_UTC = Instant.parse("2026-11-20T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private AuditRecorder auditoria;

    private final UUID evento = UUID.randomUUID();

    /** Cuerpo de F-05 con los datos de la prueba; {@code fechaInicio} null no se envía */
    private static String cuerpo(UUID eventoId, boolean simulacro, String estado, String fechaInicio) {
        return "{\"eventoId\":\"" + eventoId + "\",\"nombre\":\"Simulacro de evacuación bloque A\","
                + "\"simulacro\":" + simulacro + ",\"estado\":\"" + estado + "\","
                + (fechaInicio == null ? "" : "\"fechaInicio\":\"" + fechaInicio + "\",")
                + "\"escenario\":{\"edificios\":[\"Bloque A\"],\"zonas\":[\"Zona norte\"]},"
                + "\"rolesConvocados\":[\"ESTUDIANTE\",\"PROFESOR\"]}";
    }

    private ResultActions enviar(String cuerpo) throws Exception {
        return mockMvc.perform(put(BASE + evento + "/estado").header(IdentityHeaders.SERVICE_NAME, "ms06")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions enviar(String estado, String fechaInicio) throws Exception {
        return enviar(cuerpo(evento, true, estado, fechaInicio));
    }

    /** Lleva el evento de la prueba (simulacro) al estado indicado por un camino permitido */
    private void llevarA(EstadoEvento estado) throws Exception {
        switch (estado) {
            case BORRADOR -> enviar("BORRADOR", null);
            case PROGRAMADO -> enviar("PROGRAMADO", INICIO);
            case EN_EJECUCION -> enviar("EN_EJECUCION", INICIO);
            case FINALIZADO -> {
                enviar("EN_EJECUCION", INICIO);
                enviar("FINALIZADO", null);
            }
        }
    }

    private Evento guardado() {
        entityManager.clear();
        return eventos.findById(evento).orElseThrow();
    }

    private ResultActions checkin() throws Exception {
        return mockMvc.perform(post(BASE + evento + "/checkins")
                .header(IdentityHeaders.USER_ID, ESTUDIANTE).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE")
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EVACUATING\"}"));
    }

    @Test
    void unProgramadoNuevoSeGuardaSinHabilitarElCheckinYRepetirloLoReprograma() throws Exception {
        enviar("PROGRAMADO", INICIO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventoId").value(evento.toString()))
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.fechaInicio").value("2026-11-20T15:00:00Z"))
                .andExpect(jsonPath("$.checkinHabilitado").value(false))
                .andExpect(jsonPath("$.rolesConvocados[1]").value("PROFESOR"));
        checkin().andExpect(status().isConflict());

        enviar("PROGRAMADO", "2026-11-21T08:30:00-05:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.fechaInicio").value("2026-11-21T13:30:00Z"));
    }

    @Test
    void enEjecucionHabilitaElCheckinYFinalizadoLoDeshabilita() throws Exception {
        llevarA(EstadoEvento.PROGRAMADO);

        enviar("EN_EJECUCION", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkinHabilitado").value(true))
                // Sin fecha nueva conserva la programada
                .andExpect(jsonPath("$.fechaInicio").value("2026-11-20T15:00:00Z"));
        checkin().andExpect(status().isCreated());

        enviar("FINALIZADO", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.checkinHabilitado").value(false))
                .andExpect(jsonPath("$.fechaCierre").isNotEmpty());
        checkin().andExpect(status().isConflict());
    }

    @Test
    void unBorradorSobreUnProgramadoEliminaLaFecha() throws Exception {
        llevarA(EstadoEvento.PROGRAMADO);

        enviar("BORRADOR", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.fechaInicio").doesNotExist())
                .andExpect(jsonPath("$.checkinHabilitado").value(false));
        assertNull(guardado().getFechaInicio());
    }

    @Test
    void unEventoRealSigueLosEstadosQueEnviaMs06() throws Exception {
        enviar(cuerpo(evento, false, "BORRADOR", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulacro").value(false))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.checkinHabilitado").value(false));
        assertNull(guardado().getFechaInicio());
        checkin().andExpect(status().isConflict());

        enviar(cuerpo(evento, false, "PROGRAMADO", INICIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROGRAMADO"))
                .andExpect(jsonPath("$.checkinHabilitado").value(false));

        enviar(cuerpo(evento, false, "EN_EJECUCION", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.checkinHabilitado").value(true));
        checkin().andExpect(status().isCreated());
    }

    @Test
    void unEventoRealProgramadoSinFechaEsInvalido() throws Exception {
        enviar(cuerpo(evento, false, "PROGRAMADO", null)).andExpect(status().isBadRequest());
    }

    @Test
    void unaEmergenciaPuedeFinalizarseYQuedaDefinitiva() throws Exception {
        enviar(cuerpo(evento, false, "EN_EJECUCION", null)).andExpect(status().isCreated());

        enviar(cuerpo(evento, false, "FINALIZADO", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"));
        // FINALIZADO es definitivo, también para un evento real
        enviar(cuerpo(evento, false, "PROGRAMADO", INICIO)).andExpect(status().isConflict());
    }

    @Test
    void cambiarElTipoDeEventoEsConflictoYNoModificaNada() throws Exception {
        llevarA(EstadoEvento.PROGRAMADO);

        enviar(cuerpo(evento, false, "PROGRAMADO", INICIO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(EstadoEventoService.TIPO_NO_PUEDE_CAMBIAR));

        Evento despues = guardado();
        assertEquals(EstadoEvento.PROGRAMADO, despues.getEstado());
        assertEquals(true, despues.isSimulacro());
    }

    @Test
    void repetirEnEjecucionOFinalizadoResponde200SinCambios() throws Exception {
        llevarA(EstadoEvento.EN_EJECUCION);
        Instant actualizado = guardado().getActualizadoEn();

        enviar("EN_EJECUCION", "2026-12-01T10:00:00-05:00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaInicio").value("2026-11-20T15:00:00Z"));
        assertEquals(actualizado, guardado().getActualizadoEn());

        enviar("FINALIZADO", null).andExpect(status().isOk());
        Evento finalizado = guardado();
        enviar("FINALIZADO", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADO"));
        assertEquals(finalizado.getFechaCierre(), guardado().getFechaCierre());
        assertEquals(finalizado.getActualizadoEn(), guardado().getActualizadoEn());
    }

    /** Tabla de transiciones de RN-MS04-21 para un simulacro: lo que no está permitido responde 409 */
    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "BORRADOR, BORRADOR, 200", "BORRADOR, PROGRAMADO, 200", "BORRADOR, EN_EJECUCION, 200",
            "BORRADOR, FINALIZADO, 409",
            "PROGRAMADO, BORRADOR, 200", "PROGRAMADO, PROGRAMADO, 200", "PROGRAMADO, EN_EJECUCION, 200",
            "PROGRAMADO, FINALIZADO, 409",
            "EN_EJECUCION, BORRADOR, 409", "EN_EJECUCION, PROGRAMADO, 409", "EN_EJECUCION, EN_EJECUCION, 200",
            "EN_EJECUCION, FINALIZADO, 200",
            "FINALIZADO, BORRADOR, 409", "FINALIZADO, PROGRAMADO, 409", "FINALIZADO, EN_EJECUCION, 409",
            "FINALIZADO, FINALIZADO, 200" })
    void cadaTransicionResponde(EstadoEvento guardado, EstadoEvento recibido, int esperado) throws Exception {
        llevarA(guardado);

        String fecha = recibido == EstadoEvento.BORRADOR ? null : INICIO;
        enviar(recibido.name(), fecha).andExpect(status().is(esperado));

        assertEquals(esperado == 200 ? recibido : guardado, guardado().getEstado());
    }

    @ParameterizedTest
    @CsvSource({ "BORRADOR, false", "PROGRAMADO, false", "EN_EJECUCION, true", "FINALIZADO, false" })
    void unEventoNuevoPuedeLlegarEnCualquierEstado(EstadoEvento estado, boolean habilitado) throws Exception {
        String fecha = estado == EstadoEvento.BORRADOR ? null : INICIO;

        enviar(estado.name(), fecha)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value(estado.name()))
                .andExpect(jsonPath("$.checkinHabilitado").value(habilitado));

        Evento nuevo = guardado();
        assertEquals(estado == EstadoEvento.BORRADOR ? null : INICIO_UTC, nuevo.getFechaInicio());
        assertEquals(estado == EstadoEvento.FINALIZADO, nuevo.getFechaCierre() != null);
    }

    @Test
    void elCuerpoSeValidaConLosMensajesDeF05YNoModificaNada() throws Exception {
        enviar("{\"eventoId\":\"" + evento + "\",\"nombre\":\"  \",\"estado\":\"CANCELADO\","
                + "\"fechaInicio\":\"2026-11-20T10:00:00\",\"escenario\":{\"edificios\":[\"\"]},"
                + "\"rolesConvocados\":[\"ESTUDIANTE\",\"ADMIN\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')].mensaje")
                        .value(EstadoEventoValidator.NOMBRE_NO_VALIDO))
                .andExpect(jsonPath("$.errores[?(@.campo=='simulacro')].mensaje")
                        .value(EstadoEventoValidator.FALTA_SIMULACRO))
                .andExpect(jsonPath("$.errores[?(@.campo=='estado')].mensaje")
                        .value(EstadoEventoValidator.ESTADO_NO_VALIDO))
                .andExpect(jsonPath("$.errores[?(@.campo=='fechaInicio')].mensaje")
                        .value(EstadoEventoValidator.FECHA_NO_VALIDA))
                .andExpect(jsonPath("$.errores[?(@.campo=='escenario.edificios')].mensaje")
                        .value(EstadoEventoValidator.EDIFICIO_NO_VALIDO))
                .andExpect(jsonPath("$.errores[?(@.campo=='rolesConvocados')].mensaje")
                        .value(EstadoEventoValidator.ROL_NO_VALIDO));

        assertFalse(eventos.existsById(evento));
    }

    @Test
    void elIdDelCuerpoDebeSerElDeLaRuta() throws Exception {
        enviar(cuerpo(UUID.randomUUID(), true, "PROGRAMADO", INICIO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(EstadoEventoValidator.EVENTO_DISTINTO_DE_LA_RUTA));
    }

    @Test
    void laFechaDeInicioDependeDelEstado() throws Exception {
        enviar("PROGRAMADO", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(EstadoEventoValidator.FALTA_FECHA));
        enviar("BORRADOR", INICIO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(EstadoEventoValidator.FECHA_EN_BORRADOR));
    }

    @Test
    void losRolesConvocadosNoSePuedenRepetirNiFaltar() throws Exception {
        enviar(cuerpo(evento, true, "PROGRAMADO", INICIO).replace("\"PROFESOR\"", "\"ESTUDIANTE\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(EstadoEventoValidator.ROL_NO_VALIDO));
        enviar(cuerpo(evento, true, "PROGRAMADO", INICIO).replace("\"ESTUDIANTE\",\"PROFESOR\"", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value(EstadoEventoValidator.FALTAN_ROLES));
    }

    @Test
    void cadaCambioExitosoSeAuditaConElOrigenYLosEstados() throws Exception {
        enviar("PROGRAMADO", INICIO).andExpect(status().isCreated());
        enviar("EN_EJECUCION", null).andExpect(status().isOk());

        verify(auditoria).recordSystem(eq(AuditAction.CAMBIO_ESTADO_EVENTO), eq(AuditResult.EXITO),
                eq("evento:" + evento), eq("origen=ms06 NUEVO->PROGRAMADO"), org.mockito.ArgumentMatchers.any());
        verify(auditoria).recordSystem(eq(AuditAction.CAMBIO_ESTADO_EVENTO), eq(AuditResult.EXITO),
                eq("evento:" + evento), eq("origen=ms06 PROGRAMADO->EN_EJECUCION"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cadaCambioFallidoSeAuditaConElCodigoDelError() throws Exception {
        llevarA(EstadoEvento.FINALIZADO);

        enviar("PROGRAMADO", INICIO).andExpect(status().isConflict());
        enviar("PROGRAMADO", null).andExpect(status().isBadRequest());

        verify(auditoria).recordSystem(eq(AuditAction.CAMBIO_ESTADO_EVENTO), eq(AuditResult.FALLO),
                eq("evento:" + evento), eq("origen=ms06 CONFLICTO"), org.mockito.ArgumentMatchers.any());
        verify(auditoria).recordSystem(eq(AuditAction.CAMBIO_ESTADO_EVENTO), eq(AuditResult.FALLO),
                eq("evento:" + evento), eq("origen=ms06 VALIDACION"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sinServicioEs401YConOtroServicioEs403() throws Exception {
        mockMvc.perform(put(BASE + evento + "/estado").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(evento, true, "PROGRAMADO", INICIO)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + evento + "/estado").header(IdentityHeaders.SERVICE_NAME, "ms03")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(evento, true, "PROGRAMADO", INICIO)))
                .andExpect(status().isForbidden());

        assertFalse(eventos.existsById(evento));
    }
}
