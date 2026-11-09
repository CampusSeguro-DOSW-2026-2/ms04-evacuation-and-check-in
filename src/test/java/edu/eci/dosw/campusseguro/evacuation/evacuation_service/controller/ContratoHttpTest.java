package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinsMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Escenario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoInterno;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Resultados;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoEstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.ConsultaEventoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DetalleCheckinsService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoEventoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoPropioService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EventoActivoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.HistorialService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.CheckinService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.ResultadosService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * Contrato HTTP de MS04 (api/swagger.yml): las 14 rutas, el permiso que exige cada una según la Matriz
 * Rol-Permiso para cada uno de los 8 roles, la forma de las respuestas y la validación de las entradas. Los
 * services son dobles: la lógica de cada uno se prueba en su propia clase de pruebas
 */
@WebMvcTest(controllers = { EventoActivoController.class, CheckinController.class, EstadoPropioController.class,
        DashboardController.class, HistorialController.class, ResultadosController.class,
        ConsultaEventoController.class, EventoInternoController.class })
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class })
@TestPropertySource(properties = {
        "internal.allowed-services=ms06",
        "spring.jackson.deserialization.fail-on-unknown-properties=true" })
class ContratoHttpTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final String BASE = "/api/v1/chi/eventos";
    private static final Instant AHORA = Instant.parse("2026-11-20T15:04:12Z");

    private static final Set<Rol> COMUNIDAD = EnumSet.of(Rol.ESTUDIANTE, Rol.PROFESOR, Rol.ADMINISTRATIVO,
            Rol.BRIGADISTA, Rol.COORDINADOR, Rol.ORGANIZADOR);
    private static final Set<Rol> DASHBOARD = EnumSet.of(Rol.BRIGADISTA, Rol.COORDINADOR, Rol.ORGANIZADOR,
            Rol.AUDITOR);
    private static final Set<Rol> HISTORIAL = EnumSet.of(Rol.COORDINADOR, Rol.ORGANIZADOR, Rol.AUDITOR);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoActivoService eventoActivoService;
    @MockitoBean
    private CheckinService checkinService;
    @MockitoBean
    private EstadoPropioService estadoPropioService;
    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private HistorialService historialService;
    @MockitoBean
    private ResultadosService resultadosService;
    @MockitoBean
    private ConsultaEventoService consultaEventoService;
    @MockitoBean
    private EstadoEventoService estadoEventoService;
    @MockitoBean
    private DetalleCheckinsService detalleCheckinsService;

    private static EventoInterno eventoInterno() {
        return new EventoInterno(EVENTO, "Simulacro de evacuación bloque A", true, EstadoEvento.EN_EJECUCION, AHORA,
                null, new Escenario(List.of("Bloque A"), List.of("Zona norte")), List.of("ESTUDIANTE"), true);
    }

    private static EventoResponse eventoResponse() {
        return new EventoResponse(EVENTO, "Simulacro de evacuación bloque A", true, EstadoEvento.EN_EJECUCION, AHORA,
                null, new Escenario(List.of("Bloque A"), List.of("Zona norte")), List.of("ESTUDIANTE"), true);
    }

    private static CheckinResponse checkinResponse() {
        return new CheckinResponse(UUID.randomUUID(), EVENTO, EstadoCheckin.SAFE, PUNTO, null, null, AHORA);
    }

    @BeforeEach
    void prepararDobles() {
        when(eventoActivoService.activos(any())).thenReturn(List.of(eventoResponse()));
        when(checkinService.registrar(any(), any(), any(), any()))
                .thenReturn(new ResultadoCheckin(checkinResponse(), true));
        when(estadoPropioService.consultar(any(), any())).thenReturn(checkinResponse());
        when(dashboardService.consolidado(any(), any())).thenReturn(new Consolidado(EVENTO, EstadoEvento.EN_EJECUCION,
                177, new TotalesPorEstado(120, 35, 4, 18), List.of(new TotalPorPunto(PUNTO, 80)), AHORA));
        when(dashboardService.personas(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(dashboardService.pendientes(any(), anyInt(), anyInt(), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(dashboardService.asistencia(any(), anyInt(), anyInt(), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(historialService.historial(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(resultadosService.resultados(any(), any())).thenReturn(new Resultados(EVENTO, true, EstadoEvento.FINALIZADO,
                AHORA, AHORA, true, 200L, 177, 23L, new TotalesPorEstado(120, 35, 4, 18), List.of(), List.of(),
                List.of()));
        when(consultaEventoService.listar(any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(eventoResponse()), 0, 20, 1));
        when(consultaEventoService.obtener(any())).thenReturn(eventoResponse());
        when(estadoEventoService.recibir(any(), any(), any(), any()))
                .thenReturn(new ResultadoEstadoEvento(eventoInterno(), false));
        when(detalleCheckinsService.detalle(any(), any(), anyInt(), anyInt()))
                .thenReturn(new DetalleCheckinsMs06(0, 20, 1,
                        List.of(new DetalleCheckinMs06(UUID.fromString(USUARIO), "Ana María Pérez Gómez",
                                EstadoCheckin.SAFE, PUNTO, AHORA))));
    }

    /** Una ruta protegida del contrato y los roles que la Matriz autoriza a usarla */
    private record Ruta(String nombre, HttpMethod metodo, String ruta, String cuerpo, Set<Rol> permitidos) {
        @Override
        public String toString() {
            return nombre;
        }
    }

    private static final String CHECKIN_SAFE = "{\"estado\":\"SAFE\",\"punto_encuentro_id\":\"" + PUNTO + "\"}";
    private static final String CHECKIN_ASISTENCIA = "{\"estado\":\"NEEDS_ASSISTANCE\",\"ubicacion\":"
            + "{\"tipo\":\"EDIFICIO\",\"id\":\"" + PUNTO + "\"}}";

    static Stream<Arguments> rutasProtegidas() {
        String e = BASE + "/" + EVENTO;
        return Stream.of(
                new Ruta("GET /activos (RF-01)", HttpMethod.GET, BASE + "/activos", null, COMUNIDAD),
                new Ruta("POST /{id}/checkins SAFE (RF-02/03)", HttpMethod.POST, e + "/checkins", CHECKIN_SAFE,
                        COMUNIDAD),
                new Ruta("POST /{id}/checkins NEEDS_ASSISTANCE (RF-04)", HttpMethod.POST, e + "/checkins",
                        CHECKIN_ASISTENCIA, COMUNIDAD),
                new Ruta("GET /{id}/checkins/propio (RF-05)", HttpMethod.GET, e + "/checkins/propio", null,
                        COMUNIDAD),
                new Ruta("GET /{id}/consolidado (RF-06)", HttpMethod.GET, e + "/consolidado", null, DASHBOARD),
                new Ruta("GET /{id}/personas (RF-06)", HttpMethod.GET, e + "/personas", null, DASHBOARD),
                new Ruta("GET /{id}/pendientes (RF-07)", HttpMethod.GET, e + "/pendientes", null, DASHBOARD),
                new Ruta("GET /{id}/asistencia (RF-08)", HttpMethod.GET, e + "/asistencia", null, DASHBOARD),
                new Ruta("GET /{id}/historial (RF-09)", HttpMethod.GET, e + "/historial", null, HISTORIAL),
                new Ruta("GET /{id}/resultados (RF-10)", HttpMethod.GET, e + "/resultados", null, HISTORIAL),
                new Ruta("GET / (RF-13)", HttpMethod.GET, BASE, null, DASHBOARD),
                new Ruta("GET /{id} (RF-13)", HttpMethod.GET, e, null, DASHBOARD))
                .map(Arguments::of);
    }

    private MockHttpServletRequestBuilder peticion(Ruta ruta, Rol rol) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(ruta.metodo(), ruta.ruta())
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, rol.name());
        if (ruta.cuerpo() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(ruta.cuerpo());
        }
        return builder;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rutasProtegidas")
    void cadaRutaProtegidaAutorizaExactamenteLosRolesDeLaMatriz(Ruta ruta) throws Exception {
        for (Rol rol : Rol.values()) {
            int estado = mockMvc.perform(peticion(ruta, rol)).andReturn().getResponse().getStatus();
            if (ruta.permitidos().contains(rol)) {
                assertEquals(true, estado == 200 || estado == 201, rol + " debería poder usar " + ruta + " y dio " + estado);
            } else {
                assertEquals(403, estado, rol + " no debería poder usar " + ruta);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rutasProtegidas")
    void sinCabecerasDeIdentidadCadaRutaProtegidaEsNoAutenticada(Ruta ruta) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(ruta.metodo(), ruta.ruta());
        if (ruta.cuerpo() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(ruta.cuerpo());
        }

        mockMvc.perform(builder)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void unRolSinPermisoNuncaLlegaAlService() throws Exception {
        mockMvc.perform(get(BASE + "/" + EVENTO + "/consolidado")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + EVENTO + "/historial")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "BRIGADISTA"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService, historialService);
    }

    @Test
    void elAdminSinOtrosRolesNoTienePermisosEnMs04() throws Exception {
        mockMvc.perform(get(BASE + "/activos")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void unUsuarioConVariosRolesTieneLaUnionDeSusPermisos() throws Exception {
        mockMvc.perform(get(BASE + "/" + EVENTO + "/consolidado")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE,BRIGADISTA"))
                .andExpect(status().isOk());
    }

    @Test
    void elPrimerCheckinResponde201ConElContratoEnSnakeCase() throws Exception {
        mockMvc.perform(peticion(new Ruta("x", HttpMethod.POST, BASE + "/" + EVENTO + "/checkins", CHECKIN_SAFE,
                COMUNIDAD), Rol.ESTUDIANTE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evento_id").value(EVENTO.toString()))
                .andExpect(jsonPath("$.estado").value("SAFE"))
                .andExpect(jsonPath("$.punto_encuentro_id").value(PUNTO.toString()))
                .andExpect(jsonPath("$.fecha_hora").value("2026-11-20T15:04:12Z"));
    }

    @Test
    void unCambioDeEstadoResponde200() throws Exception {
        when(checkinService.registrar(any(), any(), any(), any()))
                .thenReturn(new ResultadoCheckin(checkinResponse(), false));

        mockMvc.perform(comoEstudiante(post(BASE + "/" + EVENTO + "/checkins"), CHECKIN_SAFE))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder comoEstudiante(MockHttpServletRequestBuilder builder,
            String cuerpo) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE");
    }

    // ------------------------------------------------------------------ validación del cuerpo y la consulta

    @Test
    void rechazaUnIdDeUsuarioEnElCuerpoDelCheckin() throws Exception {
        mockMvc.perform(comoEstudiante(post(BASE + "/" + EVENTO + "/checkins"),
                "{\"estado\":\"EVACUATING\",\"usuario_id\":\"" + USUARIO + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El campo no está permitido."));

        verifyNoInteractions(checkinService);
    }

    /**
     * Las reglas del cuerpo del check-in las valida el service (para auditar cada intento fallido, RF-MS04-02 A1):
     * un estado o un tipo de ubicación fuera del catálogo llega como null en lugar de cortar la petición aquí
     */
    @Test
    void unEstadoOTipoFueraDelCatalogoLlegaAlServiceComoNulo() throws Exception {
        mockMvc.perform(comoEstudiante(post(BASE + "/" + EVENTO + "/checkins"),
                "{\"estado\":\"OTRO\",\"ubicacion\":{\"tipo\":\"OTRO\",\"id\":\"" + PUNTO + "\"}}"))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest> cuerpo =
                org.mockito.ArgumentCaptor.forClass(
                        edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest.class);
        org.mockito.Mockito.verify(checkinService).registrar(any(), cuerpo.capture(), any(), any());
        org.junit.jupiter.api.Assertions.assertNull(cuerpo.getValue().estado());
        org.junit.jupiter.api.Assertions.assertNull(cuerpo.getValue().ubicacion().tipo());
        assertEquals(PUNTO, cuerpo.getValue().ubicacion().id());
    }

    @Test
    void elPuntoDeEncuentroDebeSerUnUuidConElNombreDelContrato() throws Exception {
        mockMvc.perform(comoEstudiante(post(BASE + "/" + EVENTO + "/checkins"),
                "{\"estado\":\"SAFE\",\"punto_encuentro_id\":\"3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"));
    }

    @Test
    void unEventoIdQueNoEsUuidEsValidacion() throws Exception {
        mockMvc.perform(get(BASE + "/no-es-uuid/consolidado")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"));
    }

    @Test
    void laPaginacionInvalidaEsValidacionPeroElPermisoSeVerificaPrimero() throws Exception {
        mockMvc.perform(get(BASE + "/" + EVENTO + "/pendientes?page=-1&size=101")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].campo").value("page"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Página no válida."))
                .andExpect(jsonPath("$.errores[1].campo").value("size"))
                .andExpect(jsonPath("$.errores[1].mensaje").value("Tamaño de página no válido (1 a 100)."));
        mockMvc.perform(get(BASE + "/" + EVENTO + "/pendientes?page=-1")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lasListasUsanPagina0YTamano20PorDefecto() throws Exception {
        mockMvc.perform(get(BASE + "/" + EVENTO + "/asistencia")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0))
                .andExpect(jsonPath("$.contenido", hasSize(0)));
    }

    @Test
    void unEstadoInvalidoEnElFiltroEsValidacion() throws Exception {
        mockMvc.perform(get(BASE + "/" + EVENTO + "/personas?estado=OTRO")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
        mockMvc.perform(get(BASE + "?estado=OTRO")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
    }

    // ------------------------------------------------------------------ forma de las respuestas

    @Test
    void elConsolidadoSiempreTraeLosCuatroEstadosYLosPuntosDeEncuentro() throws Exception {
        mockMvc.perform(get(BASE + "/" + EVENTO + "/consolidado")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento_id").value(EVENTO.toString()))
                .andExpect(jsonPath("$.estado_evento").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.total_reportados").value(177))
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(120))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(35))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(4))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(18))
                .andExpect(jsonPath("$.por_punto_encuentro[0].punto_encuentro_id").value(PUNTO.toString()))
                .andExpect(jsonPath("$.por_punto_encuentro[0].total").value(80))
                .andExpect(jsonPath("$.calculado_en").value("2026-11-20T15:04:12Z"));
    }

    @Test
    void losEventosActivosYLaConsultaDeEventosUsanSnakeCase() throws Exception {
        mockMvc.perform(get(BASE + "/activos")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evento_id").value(EVENTO.toString()))
                .andExpect(jsonPath("$[0].checkin_habilitado").value(true))
                .andExpect(jsonPath("$[0].roles_convocados[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$[0].escenario.edificios[0]").value("Bloque A"))
                .andExpect(jsonPath("$[0].fecha_cierre").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void laListaDeEventosUsaLaPaginacionDeLosEstandares() throws Exception {
        mockMvc.perform(get(BASE + "?estado=EN_EJECUCION&page=0&size=10")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    // ------------------------------------------------------------------ rutas internas de MS06

    private static MockHttpServletRequestBuilder comoMs06(MockHttpServletRequestBuilder builder) {
        return builder.header(IdentityHeaders.SERVICE_NAME, "ms06");
    }

    private static final String ESTADO_VALIDO = "{\"eventoId\":\"" + EVENTO + "\",\"nombre\":\"Simulacro de "
            + "evacuación bloque A\",\"simulacro\":true,\"estado\":\"PROGRAMADO\","
            + "\"fechaInicio\":\"2026-11-20T10:00:00-05:00\","
            + "\"escenario\":{\"edificios\":[\"Bloque A\"],\"zonas\":[\"Zona norte\"]},"
            + "\"rolesConvocados\":[\"ESTUDIANTE\",\"PROFESOR\"]}";

    @Test
    void ms06RecibeElEventoAlmacenadoEnCamelCase() throws Exception {
        mockMvc.perform(comoMs06(put(BASE + "/" + EVENTO + "/estado")).contentType(MediaType.APPLICATION_JSON)
                .content(ESTADO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(EVENTO.toString()))
                .andExpect(jsonPath("$.checkinHabilitado").value(true))
                .andExpect(jsonPath("$.rolesConvocados[0]").value("ESTUDIANTE"));
    }

    @Test
    void elEventoNuevoResponde201() throws Exception {
        when(estadoEventoService.recibir(any(), any(), any(), any()))
                .thenReturn(new ResultadoEstadoEvento(eventoInterno(), true));

        mockMvc.perform(comoMs06(put(BASE + "/" + EVENTO + "/estado")).contentType(MediaType.APPLICATION_JSON)
                .content(ESTADO_VALIDO))
                .andExpect(status().isCreated());
    }

    /**
     * El cuerpo de F-05 lo valida el service, para que cada intento fallido quede auditado (RF-MS04-11, A1): un
     * cuerpo incompleto o con valores fuera del catálogo llega al service. Los mensajes se prueban en
     * EstadoEventoIntegracionTest
     */
    @Test
    void elCuerpoDelEstadoLoValidaElServiceParaAuditarlo() throws Exception {
        mockMvc.perform(comoMs06(put(BASE + "/" + EVENTO + "/estado")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventoId\":\"" + EVENTO + "\",\"nombre\":\"\",\"estado\":\"CANCELADO\","
                        + "\"fechaInicio\":\"2026-11-20T10:00:00\",\"rolesConvocados\":[]}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(estadoEventoService).recibir(any(), any(), any(), any());
    }

    @Test
    void rechazaUnCampoDeMs06QueElContratoNoDefine() throws Exception {
        mockMvc.perform(comoMs06(put(BASE + "/" + EVENTO + "/estado")).contentType(MediaType.APPLICATION_JSON)
                .content(ESTADO_VALIDO.replace("\"simulacro\":true,", "\"simulacro\":true,\"endDate\":\"x\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("endDate"));
    }

    @Test
    void ms06ConsultaElDetalleDeCheckinsConSuFormato() throws Exception {
        mockMvc.perform(comoMs06(get(BASE + "/" + EVENTO + "/checkins?estado=SAFE&page=0&size=50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].usuarioId").value(USUARIO))
                .andExpect(jsonPath("$.items[0].nombre").value("Ana María Pérez Gómez"))
                .andExpect(jsonPath("$.items[0].estado").value("SAFE"))
                .andExpect(jsonPath("$.items[0].zonaId").value(PUNTO.toString()))
                .andExpect(jsonPath("$.items[0].registradoEn").value("2026-11-20T15:04:12Z"));
    }

    @Test
    void elDetalleDeMs06ValidaLaPaginacion() throws Exception {
        mockMvc.perform(comoMs06(get(BASE + "/" + EVENTO + "/checkins?size=0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("size"));
    }

    @Test
    void lasRutasInternasNoLasUsanLosUsuariosNiOtrosServicios() throws Exception {
        for (MockHttpServletRequestBuilder interna : List.of(
                put(BASE + "/" + EVENTO + "/estado").contentType(MediaType.APPLICATION_JSON).content(ESTADO_VALIDO),
                get(BASE + "/" + EVENTO + "/checkins"))) {
            mockMvc.perform(interna)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_SERVICE_MESSAGE));
        }
        mockMvc.perform(get(BASE + "/" + EVENTO + "/checkins")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + EVENTO + "/checkins").header(IdentityHeaders.SERVICE_NAME, "ms03"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(estadoEventoService, detalleCheckinsService);
    }

    @Test
    void laCorrelacionLlegaALosServices() throws Exception {
        String correlation = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(comoMs06(put(BASE + "/" + EVENTO + "/estado")).contentType(MediaType.APPLICATION_JSON)
                .content(ESTADO_VALIDO).header(CorrelationIds.HEADER, correlation))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(estadoEventoService).recibir(any(), any(), org.mockito.ArgumentMatchers.eq("ms06"),
                org.mockito.ArgumentMatchers.eq(correlation));
    }
}
