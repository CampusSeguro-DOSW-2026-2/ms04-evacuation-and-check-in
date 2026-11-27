package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
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
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.DetalleCheckinsMs06;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Escenario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoInterno;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EventoResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoEstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Resultados;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.CheckinService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.ConsultaEventoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DetalleCheckinsService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoEventoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoPropioService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EventoActivoService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.HistorialService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.ResultadosService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * HTTP contract of MS04 (api/openapi.yml): the 14 routes, the permission that each one requires according to the
 * role-permission matrix for each of the 8 roles, the shape of the answers and the validation of the inputs. The
 * services are mocks: the logic of each one is tested in its own test class
 */
@WebMvcTest(controllers = { EventoActivoController.class, CheckinController.class, EstadoPropioController.class,
        DashboardController.class, HistorialController.class, ResultadosController.class,
        ConsultaEventoController.class, EventoInternoController.class })
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class })
@TestPropertySource(properties = {
        "internal.allowed-services=ms06",
        "spring.jackson.deserialization.fail-on-unknown-properties=true" })
class ContractHttpTest {

    private static final String USER = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID POINT = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final String BASE = "/api/v1/chi/eventos";
    private static final Instant NOW = Instant.parse("2026-11-20T15:04:12Z");

    /** The 8 initial roles, with the permissions that the matrix gives them when deployed (TestIdentities) */
    private static final List<String> INITIAL_ROLES = List.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO",
            "BRIGADISTA", "COORDINADOR", "ORGANIZADOR", "ADMIN", "AUDITOR");
    private static final Set<String> COMMUNITY = Set.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO",
            "BRIGADISTA", "COORDINADOR", "ORGANIZADOR");
    private static final Set<String> DASHBOARD = Set.of("BRIGADISTA", "COORDINADOR", "ORGANIZADOR", "AUDITOR");
    private static final Set<String> HISTORY = Set.of("COORDINADOR", "ORGANIZADOR", "AUDITOR");

    private static final String CHECKIN_SAFE = "{\"estado\":\"SAFE\",\"punto_encuentro_id\":\"" + POINT + "\"}";
    private static final String CHECKIN_ASSISTANCE = "{\"estado\":\"NEEDS_ASSISTANCE\",\"ubicacion\":"
            + "{\"tipo\":\"EDIFICIO\",\"id\":\"" + POINT + "\"}}";
    private static final String VALID_STATE = "{\"eventoId\":\"" + EVENT + "\",\"nombre\":\"Simulacro de "
            + "evacuación bloque A\",\"simulacro\":true,\"estado\":\"PROGRAMADO\","
            + "\"fechaInicio\":\"2026-11-20T10:00:00-05:00\","
            + "\"escenario\":{\"edificios\":[\"Bloque A\"],\"zonas\":[\"Zona norte\"]},"
            + "\"rolesConvocados\":[\"ESTUDIANTE\",\"PROFESOR\"]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoActivoService activeEventService;
    @MockitoBean
    private CheckinService checkinService;
    @MockitoBean
    private EstadoPropioService ownStatusService;
    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private HistorialService historyService;
    @MockitoBean
    private ResultadosService resultsService;
    @MockitoBean
    private ConsultaEventoService eventQueryService;
    @MockitoBean
    private EstadoEventoService eventStateService;
    @MockitoBean
    private DetalleCheckinsService checkinDetailService;

    /**
     * A protected route of the contract and the roles that the matrix allows to use it
     *
     * @param name readable name of the route
     * @param method HTTP method
     * @param route path of the route
     * @param body body of the request, or null
     * @param allowed roles allowed by the matrix
     */
    private record Route(String name, HttpMethod method, String route, String body, Set<String> allowed) {

        /**
         * Name shown in the report of the parameterized tests
         *
         * @return the name of the route
         */
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Event as the internal route of MS06 answers it
     *
     * @return the internal event
     */
    private static EventoInterno internalEvent() {
        return new EventoInterno(EVENT, "Simulacro de evacuación bloque A", true, EstadoEvento.EN_EJECUCION, NOW,
                null, new Escenario(List.of("Bloque A"), List.of("Zona norte")), List.of("ESTUDIANTE"), true);
    }

    /**
     * Event as the protected routes answer it
     *
     * @return the event
     */
    private static EventoResponse eventResponse() {
        return new EventoResponse(EVENT, "Simulacro de evacuación bloque A", true, EstadoEvento.EN_EJECUCION, NOW,
                null, new Escenario(List.of("Bloque A"), List.of("Zona norte")), List.of("ESTUDIANTE"), true);
    }

    /**
     * Check-in as the routes of the user answer it
     *
     * @return the check-in
     */
    private static CheckinResponse checkinResponse() {
        return new CheckinResponse(UUID.randomUUID(), EVENT, EstadoCheckin.SAFE, POINT, null, null, NOW);
    }

    /**
     * Makes every mocked service answer a valid value
     */
    @BeforeEach
    void prepareMocks() {
        when(activeEventService.activos(any())).thenReturn(List.of(eventResponse()));
        when(checkinService.registrar(any(), any(), any(), any()))
                .thenReturn(new ResultadoCheckin(checkinResponse(), true));
        when(ownStatusService.consultar(any(), any())).thenReturn(checkinResponse());
        when(dashboardService.consolidado(any(), any())).thenReturn(new Consolidado(EVENT, EstadoEvento.EN_EJECUCION,
                177, new TotalesPorEstado(120, 35, 4, 18), List.of(new TotalPorPunto(POINT, 80)), NOW));
        when(dashboardService.personas(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(dashboardService.pendientes(any(), anyInt(), anyInt(), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(dashboardService.asistencia(any(), anyInt(), anyInt(), any()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(historyService.historial(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));
        when(resultsService.resultados(any(), any())).thenReturn(new Resultados(EVENT, true, EstadoEvento.FINALIZADO,
                NOW, NOW, true, 200L, 177, 23L, new TotalesPorEstado(120, 35, 4, 18), List.of(), List.of(),
                List.of()));
        when(eventQueryService.listar(any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.of(List.of(eventResponse()), 0, 20, 1));
        when(eventQueryService.obtener(any())).thenReturn(eventResponse());
        when(eventStateService.recibir(any(), any(), any(), any()))
                .thenReturn(new ResultadoEstadoEvento(internalEvent(), false));
        when(checkinDetailService.detalle(any(), any(), anyInt(), anyInt()))
                .thenReturn(new DetalleCheckinsMs06(0, 20, 1,
                        List.of(new DetalleCheckinMs06(UUID.fromString(USER), "Ana María Pérez Gómez",
                                EstadoCheckin.SAFE, POINT, NOW))));
    }

    /**
     * Protected routes of the contract with the roles that the matrix allows
     *
     * @return one argument per route
     */
    static Stream<Arguments> protectedRoutes() {
        String event = BASE + "/" + EVENT;
        return Stream.of(
                new Route("GET /activos (RF-01)", HttpMethod.GET, BASE + "/activos", null, COMMUNITY),
                new Route("POST /{id}/checkins SAFE (RF-02/03)", HttpMethod.POST, event + "/checkins", CHECKIN_SAFE,
                        COMMUNITY),
                new Route("POST /{id}/checkins NEEDS_ASSISTANCE (RF-04)", HttpMethod.POST, event + "/checkins",
                        CHECKIN_ASSISTANCE, COMMUNITY),
                new Route("GET /{id}/checkins/propio (RF-05)", HttpMethod.GET, event + "/checkins/propio", null,
                        COMMUNITY),
                new Route("GET /{id}/consolidado (RF-06)", HttpMethod.GET, event + "/consolidado", null, DASHBOARD),
                new Route("GET /{id}/personas (RF-06)", HttpMethod.GET, event + "/personas", null, DASHBOARD),
                new Route("GET /{id}/pendientes (RF-07)", HttpMethod.GET, event + "/pendientes", null, DASHBOARD),
                new Route("GET /{id}/asistencia (RF-08)", HttpMethod.GET, event + "/asistencia", null, DASHBOARD),
                new Route("GET /{id}/historial (RF-09)", HttpMethod.GET, event + "/historial", null, HISTORY),
                new Route("GET /{id}/resultados (RF-10)", HttpMethod.GET, event + "/resultados", null, HISTORY),
                new Route("GET / (RF-13)", HttpMethod.GET, BASE, null, DASHBOARD),
                new Route("GET /{id} (RF-13)", HttpMethod.GET, event, null, DASHBOARD))
                .map(Arguments::of);
    }

    /**
     * Request of a route by a user with a role
     *
     * @param route route of the contract
     * @param role role of the user
     * @return the request with the identity headers and the body of the route
     */
    private MockHttpServletRequestBuilder request(Route route, String role) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(route.method(), route.route())
                .header(IdentityHeaders.USER_ID, USER)
                .header(IdentityHeaders.USER_ROLE, role)
                .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions(role));
        if (route.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(route.body());
        }
        return builder;
    }

    /**
     * Adds the identity headers of a user with a role
     *
     * @param builder request to send
     * @param role role of the user
     * @return the request with the identity headers
     */
    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String role) {
        return builder.header(IdentityHeaders.USER_ID, USER)
                .header(IdentityHeaders.USER_ROLE, role)
                .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions(role));
    }

    /**
     * Request of a student with a JSON body
     *
     * @param builder request to send
     * @param body JSON body
     * @return the request with the body and the identity headers
     */
    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder builder, String body) {
        return as(builder.contentType(MediaType.APPLICATION_JSON).content(body), "ESTUDIANTE");
    }

    /**
     * Request of MS06 through an internal route
     *
     * @param builder request to send
     * @return the request with the name of the calling service
     */
    private static MockHttpServletRequestBuilder asMs06(MockHttpServletRequestBuilder builder) {
        return builder.header(IdentityHeaders.SERVICE_NAME, "ms06");
    }

    /**
     * Each protected route authorizes exactly the roles of the matrix
     *
     * @param route route of the contract
     * @throws Exception if a request fails
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedRoutes")
    void eachProtectedRouteAuthorizesExactlyTheRolesOfTheMatrix(Route route) throws Exception {
        for (String role : INITIAL_ROLES) {
            int status = mockMvc.perform(request(route, role)).andReturn().getResponse().getStatus();
            if (route.allowed().contains(role)) {
                assertTrue(status == 200 || status == 201, role + " should use " + route + " and got " + status);
            } else {
                assertEquals(403, status, role + " should not use " + route);
            }
        }
    }

    /**
     * Without identity headers each protected route is unauthenticated
     *
     * @param route route of the contract
     * @throws Exception if the request fails
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedRoutes")
    void withoutIdentityHeadersEachProtectedRouteIsUnauthenticated(Route route) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(route.method(), route.route());
        if (route.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(route.body());
        }

        mockMvc.perform(builder)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * A role without permission never reaches the service
     *
     * @throws Exception if a request fails
     */
    @Test
    void aRoleWithoutPermissionNeverReachesTheService() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/consolidado"), "ESTUDIANTE"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/historial"), "BRIGADISTA"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboardService, historyService);
    }

    /**
     * The administrator without other roles has no permission in MS04
     *
     * @throws Exception if the request fails
     */
    @Test
    void theAdministratorWithoutOtherRolesHasNoPermissionInMs04() throws Exception {
        mockMvc.perform(as(get(BASE + "/activos"), "ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    /**
     * A user with several roles has the union of their permissions
     *
     * @throws Exception if the request fails
     */
    @Test
    void aUserWithSeveralRolesHasTheUnionOfTheirPermissions() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/consolidado"), "ESTUDIANTE,BRIGADISTA"))
                .andExpect(status().isOk());
    }

    /**
     * The first check-in answers 201 with the contract in snake_case
     *
     * @throws Exception if the request fails
     */
    @Test
    void theFirstCheckinAnswers201WithTheContractInSnakeCase() throws Exception {
        mockMvc.perform(asStudent(post(BASE + "/" + EVENT + "/checkins"), CHECKIN_SAFE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evento_id").value(EVENT.toString()))
                .andExpect(jsonPath("$.estado").value("SAFE"))
                .andExpect(jsonPath("$.punto_encuentro_id").value(POINT.toString()))
                .andExpect(jsonPath("$.fecha_hora").value("2026-11-20T15:04:12Z"));
    }

    /**
     * A change of state answers 200
     *
     * @throws Exception if the request fails
     */
    @Test
    void aChangeOfStateAnswers200() throws Exception {
        when(checkinService.registrar(any(), any(), any(), any()))
                .thenReturn(new ResultadoCheckin(checkinResponse(), false));

        mockMvc.perform(asStudent(post(BASE + "/" + EVENT + "/checkins"), CHECKIN_SAFE))
                .andExpect(status().isOk());
    }

    /**
     * A user id in the body of the check-in is rejected
     *
     * @throws Exception if the request fails
     */
    @Test
    void rejectsAUserIdInTheBodyOfTheCheckin() throws Exception {
        mockMvc.perform(asStudent(post(BASE + "/" + EVENT + "/checkins"),
                        "{\"estado\":\"EVACUATING\",\"usuario_id\":\"" + USER + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El campo no está permitido."));

        verifyNoInteractions(checkinService);
    }

    /**
     * The rules of the body of the check-in are validated by the service, so every failed attempt is audited
     * (RF-MS04-02 A1): a state or a location type outside the catalog reaches the service as null
     *
     * @throws Exception if the request fails
     */
    @Test
    void aStateOrTypeOutsideTheCatalogReachesTheServiceAsNull() throws Exception {
        mockMvc.perform(asStudent(post(BASE + "/" + EVENT + "/checkins"),
                        "{\"estado\":\"OTRO\",\"ubicacion\":{\"tipo\":\"OTRO\",\"id\":\"" + POINT + "\"}}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CheckinRequest> body = ArgumentCaptor.forClass(CheckinRequest.class);
        verify(checkinService).registrar(any(), body.capture(), any(), any());
        assertNull(body.getValue().estado());
        assertNull(body.getValue().ubicacion().tipo());
        assertEquals(POINT, body.getValue().ubicacion().id());
    }

    /**
     * The meeting point must be a UUID and is reported with the name of the contract
     *
     * @throws Exception if the request fails
     */
    @Test
    void theMeetingPointMustBeAUuid() throws Exception {
        mockMvc.perform(asStudent(post(BASE + "/" + EVENT + "/checkins"),
                        "{\"estado\":\"SAFE\",\"punto_encuentro_id\":\"3\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"));
    }

    /**
     * An event id that is not a UUID is a validation error
     *
     * @throws Exception if the request fails
     */
    @Test
    void anEventIdThatIsNotAUuidIsAValidationError() throws Exception {
        mockMvc.perform(as(get(BASE + "/no-es-uuid/consolidado"), "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"));
    }

    /**
     * An invalid pagination is a validation error, but the permission is checked first
     *
     * @throws Exception if a request fails
     */
    @Test
    void anInvalidPaginationIsAValidationErrorButThePermissionComesFirst() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/pendientes?page=-1&size=101"), "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].campo").value("page"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Página no válida."))
                .andExpect(jsonPath("$.errores[1].campo").value("size"))
                .andExpect(jsonPath("$.errores[1].mensaje").value("Tamaño de página no válido (1 a 100)."));
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/pendientes?page=-1"), "ESTUDIANTE"))
                .andExpect(status().isForbidden());
    }

    /**
     * The lists use page 0 and size 20 by default
     *
     * @throws Exception if the request fails
     */
    @Test
    void theListsUsePageZeroAndSizeTwentyByDefault() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/asistencia"), "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0))
                .andExpect(jsonPath("$.contenido", hasSize(0)));
    }

    /**
     * An invalid state in the filter is a validation error
     *
     * @throws Exception if a request fails
     */
    @Test
    void anInvalidStateInTheFilterIsAValidationError() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/personas?estado=OTRO"), "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
        mockMvc.perform(as(get(BASE + "?estado=OTRO"), "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"));
    }

    /**
     * The consolidation always brings the four states and the meeting points
     *
     * @throws Exception if the request fails
     */
    @Test
    void theConsolidationAlwaysBringsTheFourStatesAndThePoints() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/consolidado"), "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evento_id").value(EVENT.toString()))
                .andExpect(jsonPath("$.estado_evento").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.total_reportados").value(177))
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(120))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(35))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(4))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(18))
                .andExpect(jsonPath("$.por_punto_encuentro[0].punto_encuentro_id").value(POINT.toString()))
                .andExpect(jsonPath("$.por_punto_encuentro[0].total").value(80))
                .andExpect(jsonPath("$.calculado_en").value("2026-11-20T15:04:12Z"));
    }

    /**
     * The active events and the query of events use snake_case
     *
     * @throws Exception if the request fails
     */
    @Test
    void theActiveEventsAndTheQueryOfEventsUseSnakeCase() throws Exception {
        mockMvc.perform(as(get(BASE + "/activos"), "ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evento_id").value(EVENT.toString()))
                .andExpect(jsonPath("$[0].checkin_habilitado").value(true))
                .andExpect(jsonPath("$[0].roles_convocados[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$[0].escenario.edificios[0]").value("Bloque A"))
                .andExpect(jsonPath("$[0].fecha_cierre").value(nullValue()));
    }

    /**
     * The list of events uses the pagination of the standards
     *
     * @throws Exception if the request fails
     */
    @Test
    void theListOfEventsUsesThePaginationOfTheStandards() throws Exception {
        mockMvc.perform(as(get(BASE + "?estado=EN_EJECUCION&page=0&size=10"), "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * MS06 receives the stored event in camelCase
     *
     * @throws Exception if the request fails
     */
    @Test
    void ms06ReceivesTheStoredEventInCamelCase() throws Exception {
        mockMvc.perform(asMs06(put(BASE + "/" + EVENT + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventoId").value(EVENT.toString()))
                .andExpect(jsonPath("$.checkinHabilitado").value(true))
                .andExpect(jsonPath("$.rolesConvocados[0]").value("ESTUDIANTE"));
    }

    /**
     * A new event answers 201
     *
     * @throws Exception if the request fails
     */
    @Test
    void aNewEventAnswers201() throws Exception {
        when(eventStateService.recibir(any(), any(), any(), any()))
                .thenReturn(new ResultadoEstadoEvento(internalEvent(), true));

        mockMvc.perform(asMs06(put(BASE + "/" + EVENT + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STATE))
                .andExpect(status().isCreated());
    }

    /**
     * The body of F-05 is validated by the service, so every failed attempt is audited (RF-MS04-11, A1): an
     * incomplete body or one with values outside the catalog reaches the service. The messages are tested in
     * EstadoEventoValidatorTest
     *
     * @throws Exception if the request fails
     */
    @Test
    void theBodyOfTheStateIsValidatedByTheServiceToAuditIt() throws Exception {
        mockMvc.perform(asMs06(put(BASE + "/" + EVENT + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":\"" + EVENT + "\",\"nombre\":\"\",\"estado\":\"CANCELADO\","
                                + "\"fechaInicio\":\"2026-11-20T10:00:00\",\"rolesConvocados\":[]}"))
                .andExpect(status().isOk());

        verify(eventStateService).recibir(any(), any(), any(), any());
    }

    /**
     * A field of MS06 that the contract does not define is rejected
     *
     * @throws Exception if the request fails
     */
    @Test
    void rejectsAFieldOfMs06ThatTheContractDoesNotDefine() throws Exception {
        mockMvc.perform(asMs06(put(BASE + "/" + EVENT + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STATE.replace("\"simulacro\":true,", "\"simulacro\":true,\"endDate\":\"x\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("endDate"));
    }

    /**
     * MS06 queries the detail of the check-ins with its own format
     *
     * @throws Exception if the request fails
     */
    @Test
    void ms06QueriesTheDetailWithItsFormat() throws Exception {
        mockMvc.perform(asMs06(get(BASE + "/" + EVENT + "/checkins?estado=SAFE&page=0&size=50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].usuarioId").value(USER))
                .andExpect(jsonPath("$.items[0].nombre").value("Ana María Pérez Gómez"))
                .andExpect(jsonPath("$.items[0].estado").value("SAFE"))
                .andExpect(jsonPath("$.items[0].zonaId").value(POINT.toString()))
                .andExpect(jsonPath("$.items[0].registradoEn").value("2026-11-20T15:04:12Z"));
    }

    /**
     * The detail of MS06 validates the pagination
     *
     * @throws Exception if the request fails
     */
    @Test
    void theDetailOfMs06ValidatesThePagination() throws Exception {
        mockMvc.perform(asMs06(get(BASE + "/" + EVENT + "/checkins?size=0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("size"));
    }

    /**
     * The internal routes are used neither by users nor by other services
     *
     * @throws Exception if a request fails
     */
    @Test
    void theInternalRoutesAreUsedNeitherByUsersNorByOtherServices() throws Exception {
        for (MockHttpServletRequestBuilder internal : List.of(
                put(BASE + "/" + EVENT + "/estado").contentType(MediaType.APPLICATION_JSON).content(VALID_STATE),
                get(BASE + "/" + EVENT + "/checkins"))) {
            mockMvc.perform(internal)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_SERVICE_MESSAGE));
        }
        mockMvc.perform(as(get(BASE + "/" + EVENT + "/checkins"), "COORDINADOR"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + EVENT + "/checkins").header(IdentityHeaders.SERVICE_NAME, "ms03"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(eventStateService, checkinDetailService);
    }

    /**
     * The correlation reaches the services
     *
     * @throws Exception if the request fails
     */
    @Test
    void theCorrelationReachesTheServices() throws Exception {
        String correlation = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(asMs06(put(BASE + "/" + EVENT + "/estado"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_STATE)
                        .header(CorrelationIds.HEADER, correlation))
                .andExpect(status().isOk());

        verify(eventStateService).recibir(any(), any(), eq("ms06"), eq(correlation));
    }
}
