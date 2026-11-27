package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.DatosUsuario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstadoYPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * HTTP contract of RF-MS04-06, RF-MS04-07 and RF-MS04-08: GET /api/v1/chi/eventos/{eventoId}/consolidado, /personas,
 * /pendientes and /asistencia with the real service and the repositories and the MS01 and MS05 clients replaced by
 * mocks. It checks the shape of the answers, the standard errors, the permission ms04:dashboard:read and that the
 * identity comes only from the headers
 */
@WebMvcTest(controllers = DashboardController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        DashboardService.class, DashboardHttpTest.FixedClock.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class DashboardHttpTest {

    private static final String USER = "6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13";
    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID POINT_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID POINT_B = UUID.fromString("d3f5a7c9-1b2e-4d6f-8a0c-4e6b8d0f2a41");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID SPACE = UUID.fromString("d2a8c6e0-4f1b-4735-9e3d-5a7c9b1f3e60");
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-20T15:04:12Z");
    private static final Instant START = Instant.parse("2026-11-20T14:00:00Z");
    private static final List<String> ROLES = List.of("ESTUDIANTE", "PROFESOR");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String BASE = "/api/v1/chi/eventos/" + EVENT;
    private static final Set<UUID> NONE = Set.of();

    /**
     * Fixed clock to check the date of the calculation
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        /**
         * Clock that always shows the same time
         *
         * @return the fixed clock in UTC
         */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-20T15:10:00Z"), ZoneOffset.UTC);
        }
    }

    /**
     * Count row as the database returns it
     *
     * @param state state of the check-in
     * @param point meeting point, only with SAFE
     * @param total number of people
     */
    private record Row(String state, UUID point, long total) implements ConteoPorEstadoYPunto {

        /**
         * @return the state of the row
         */
        @Override
        public String getEstado() {
            return state;
        }

        /**
         * @return the meeting point of the row
         */
        @Override
        public UUID getPunto() {
            return point;
        }

        /**
         * @return the total of the row
         */
        @Override
        public long getTotal() {
            return total;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoRepository events;

    @MockitoBean
    private CheckinRepository checkins;

    @MockitoBean
    private SolicitudAsistenciaRepository requests;

    @MockitoBean
    private UsuarioNombresClient names;

    @MockitoBean
    private ZonasBrigadistaClient brigadierZones;

    @MockitoBean
    private PoblacionObjetivoClient targetPopulation;

    /**
     * Creates the event in the given state
     *
     * @param state state of the event
     * @return the event
     */
    private Evento event(EstadoEvento state) {
        Evento event = new Evento(EVENT);
        event.setEstado(state);
        return event;
    }

    /**
     * Creates an event in execution with fixed convened roles and start
     *
     * @return the event in execution
     */
    private Evento convenedEvent() {
        Evento event = event(EstadoEvento.EN_EJECUCION);
        event.setRolesConvocados(ROLES);
        event.setFechaInicio(START);
        return event;
    }

    /**
     * Creates a check-in as the database returns it, with the date that it assigns
     *
     * @param user user that reports
     * @param state reported state
     * @param point meeting point, only with SAFE
     * @return the check-in with its registration date
     */
    private Checkin checkin(UUID user, EstadoCheckin state, UUID point) {
        Checkin checkin = new Checkin(EVENT, user, state, point, null, null);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTERED_AT);
        return checkin;
    }

    /**
     * Creates the current NEEDS_ASSISTANCE check-in of a user, as the database returns it
     *
     * @param user user that reports
     * @param type type of the reported location
     * @param location MS02 id of the location
     * @return the check-in with its registration date
     */
    private Checkin assistanceCheckin(UUID user, TipoUbicacion type, UUID location) {
        Checkin checkin = new Checkin(EVENT, user, EstadoCheckin.NEEDS_ASSISTANCE, null, type, location);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTERED_AT);
        return checkin;
    }

    /**
     * Creates the assistance request of a user with its sending state
     *
     * @param user user of the request
     * @param ms05Id id that MS05 returned, null if it did not arrive
     * @param sendingState sending state
     * @return the request
     */
    private SolicitudAsistencia request(UUID user, String ms05Id, EstadoEnvio sendingState) {
        SolicitudAsistencia request = new SolicitudAsistencia(UUID.randomUUID(), EVENT, user,
                TipoUbicacion.EDIFICIO, BUILDING, REGISTERED_AT);
        request.setSolicitudMs05Id(ms05Id);
        request.setEstadoEnvio(sendingState);
        return request;
    }

    /**
     * Request of a user with the given role
     *
     * @param route route and query of the request
     * @param role role or roles of the X-User-Role header
     * @return the request with the identity and correlation headers
     */
    private MockHttpServletRequestBuilder as(String route, String role) {
        return get(route).header(IdentityHeaders.USER_ID, USER)
                .header(IdentityHeaders.USER_ROLE, role)
                .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions(role))
                .header(CorrelationIds.HEADER, CORRELATION);
    }

    /**
     * The consolidation answers 200 with the fields of the contract in snake_case: the four states, the meeting
     * points and the date of the calculation
     *
     * @throws Exception if the request fails
     */
    @Test
    void consolidationAnswers200WithTheContractInSnakeCase() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of(
                new Row("SAFE", POINT_A, 120), new Row("SAFE", POINT_B, 30), new Row("EVACUATING", null, 35),
                new Row("NEEDS_ASSISTANCE", null, 4), new Row("OFF_CAMPUS", null, 18)));

        mockMvc.perform(as(BASE + "/consolidado", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.evento_id").value(EVENT.toString()))
                .andExpect(jsonPath("$.estado_evento").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.total_reportados").value(207))
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(150))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(35))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(4))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(18))
                .andExpect(jsonPath("$.por_punto_encuentro", hasSize(2)))
                .andExpect(jsonPath("$.por_punto_encuentro[0].punto_encuentro_id").value(POINT_A.toString()))
                .andExpect(jsonPath("$.por_punto_encuentro[0].total").value(120))
                .andExpect(jsonPath("$.por_punto_encuentro[1].punto_encuentro_id").value(POINT_B.toString()))
                .andExpect(jsonPath("$.calculado_en").value("2026-11-20T15:10:00Z"));
    }

    /**
     * An event without check-ins answers the four states in 0 and an empty list of points
     *
     * @throws Exception if the request fails
     */
    @Test
    void consolidationWithoutCheckinsAnswersTheFourStatesInZero() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.PROGRAMADO)));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        mockMvc.perform(as(BASE + "/consolidado", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado_evento").value("PROGRAMADO"))
                .andExpect(jsonPath("$.total_reportados").value(0))
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(0))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(0))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(0))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(0))
                .andExpect(jsonPath("$.por_punto_encuentro", hasSize(0)));
    }

    /**
     * The roles with ms04:dashboard:read can consolidate the event, the brigadier with their zones included
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA", "BRIGADISTA,COORDINADOR" })
    void theRolesWithThePermissionCanConsolidate(String role) throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(brigadierZones.zonasDe(any(), any())).thenReturn(new ZonasAsignadas(NONE, NONE, NONE));
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());
        when(checkins.contarVigentes(EVENT, true, NONE, NONE, NONE)).thenReturn(List.of());

        mockMvc.perform(as(BASE + "/consolidado", role)).andExpect(status().isOk());
    }

    /**
     * The roles without ms04:dashboard:read get 403 and the database is not queried
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN", "ESTUDIANTE,PROFESOR" })
    void theRolesWithoutThePermissionCannotConsolidate(String role) throws Exception {
        mockMvc.perform(as(BASE + "/consolidado", role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(events, checkins, names, brigadierZones);
    }

    /**
     * Without identity headers it answers 401 NO_AUTENTICADO
     *
     * @throws Exception if the request fails
     */
    @Test
    void consolidationWithoutIdentityHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get(BASE + "/consolidado").header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(events, checkins);
    }

    /**
     * An unknown event answers 404 NO_ENCONTRADO
     *
     * @throws Exception if the request fails
     */
    @Test
    void consolidationOfAnUnknownEventAnswers404() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        mockMvc.perform(as(BASE + "/consolidado", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    /**
     * If MS05 does not deliver the zones of the brigadier it answers 503 with the ErrorResponse of MS04
     *
     * @throws Exception if the request fails
     */
    @Test
    void consolidationOfTheBrigadierWithoutZonesOfMs05Answers503() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(brigadierZones.zonasDe(any(), any())).thenThrow(ApiException.dependenciaNoDisponible());

        mockMvc.perform(as(BASE + "/consolidado", "BRIGADISTA"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(checkins);
    }

    /**
     * The brigadier consolidates with the zones that MS05 delivers and the user id comes from the header
     *
     * @throws Exception if the request fails
     */
    @Test
    void consolidationOfTheBrigadierUsesTheirZonesAndTheirIdentity() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(event(EstadoEvento.EN_EJECUCION)));
        when(brigadierZones.zonasDe(any(), any()))
                .thenReturn(new ZonasAsignadas(Set.of(BUILDING), NONE, Set.of(POINT_A)));
        when(checkins.contarVigentes(EVENT, true, Set.of(POINT_A), Set.of(BUILDING), NONE))
                .thenReturn(List.of(new Row("SAFE", POINT_A, 6), new Row("NEEDS_ASSISTANCE", null, 1)));

        mockMvc.perform(as(BASE + "/consolidado", "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totales_por_estado.SAFE").value(6))
                .andExpect(jsonPath("$.totales_por_estado.NEEDS_ASSISTANCE").value(1))
                .andExpect(jsonPath("$.totales_por_estado.EVACUATING").value(0))
                .andExpect(jsonPath("$.totales_por_estado.OFF_CAMPUS").value(0))
                .andExpect(jsonPath("$.total_reportados").value(7));

        verify(brigadierZones).zonasDe(TestIdentities.identity(UUID.fromString(USER), "BRIGADISTA"), EVENT);
    }

    /**
     * The list answers 200 with the page in the format of the standards and the fields of the contract in snake_case
     *
     * @throws Exception if the request fails
     */
    @Test
    void peopleAnswers200WithThePageAndTheContract() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentes(EVENT, EstadoCheckin.SAFE, POINT_A, false, NONE, NONE, NONE,
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(ANA, EstadoCheckin.SAFE, POINT_A)),
                        PageRequest.of(0, 20), 1));
        when(names.datosDe(List.of(ANA)))
                .thenReturn(Map.of(ANA, new DatosUsuario("Ana Pérez", "ana.perez@escuelaing.edu.co")));

        mockMvc.perform(as(BASE + "/personas?estado=SAFE&punto_encuentro_id=" + POINT_A, "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.contenido[0].correo").value("ana.perez@escuelaing.edu.co"))
                .andExpect(jsonPath("$.contenido[0].estado").value("SAFE"))
                .andExpect(jsonPath("$.contenido[0].punto_encuentro_id").value(POINT_A.toString()))
                .andExpect(jsonPath("$.contenido[0].fecha_hora").value("2026-11-20T15:04:12Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(1))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * Without filters nor pagination it uses page 0 with 20 elements, and if MS01 does not deliver the name it is
     * null
     *
     * @throws Exception if the request fails
     */
    @Test
    void peopleWithoutParametersUsesTheDefaultPageAndANullName() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentes(EVENT, null, null, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(LUIS, EstadoCheckin.EVACUATING, null)),
                        PageRequest.of(0, 20), 1));
        when(names.datosDe(List.of(LUIS))).thenReturn(Map.of());

        mockMvc.perform(as(BASE + "/personas", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].correo").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.size").value(20));
    }

    /**
     * The roles without ms04:dashboard:read get 403 in the list
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN" })
    void theRolesWithoutThePermissionCannotListPeople(String role) throws Exception {
        mockMvc.perform(as(BASE + "/personas", role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(events, checkins, names, brigadierZones);
    }

    /**
     * Without identity headers the list answers 401
     *
     * @throws Exception if the request fails
     */
    @Test
    void peopleWithoutIdentityHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get(BASE + "/personas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * The meeting point with a state other than SAFE answers 400 with the message of form F-02
     *
     * @param state state other than SAFE
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "EVACUATING", "NEEDS_ASSISTANCE", "OFF_CAMPUS" })
    void peopleWithPointAndAStateOtherThanSafeAnswers400(String state) throws Exception {
        mockMvc.perform(as(BASE + "/personas?estado=" + state + "&punto_encuentro_id=" + POINT_A, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("El punto de encuentro solo aplica a Estoy a salvo."));

        verifyNoInteractions(events, checkins);
    }

    /**
     * An invalid pagination answers 400 with one error per parameter
     *
     * @param query invalid pagination parameters
     * @param field reported parameter
     * @param message message of form F-02
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100)." })
    void peopleWithInvalidPaginationAnswers400(String query, String field, String message) throws Exception {
        mockMvc.perform(as(BASE + "/personas?" + query, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(field))
                .andExpect(jsonPath("$.errores[0].mensaje").value(message));

        verifyNoInteractions(events, checkins);
    }

    /**
     * A state, a meeting point, a page number or a size with invalid format answer 400 with the message of form F-02
     *
     * @param query query parameters with invalid format
     * @param field reported parameter
     * @param message message of form F-02
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "estado=OTRO|estado|Estado no válido.",
            "punto_encuentro_id=3|punto_encuentro_id|Punto de encuentro no válido.",
            "page=abc|page|Página no válida.",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void peopleWithInvalidFormatAnswers400WithTheMessageOfF02(String query, String field, String message)
            throws Exception {
        mockMvc.perform(as(BASE + "/personas?" + query, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(field))
                .andExpect(jsonPath("$.errores[0].mensaje").value(message));

        verifyNoInteractions(events, checkins);
    }

    /**
     * An event id that is not a UUID answers 400 with the message of RG-03
     *
     * @throws Exception if the request fails
     */
    @Test
    void anInvalidEventIdAnswers400WithTheMessageOfRg03() throws Exception {
        mockMvc.perform(as("/api/v1/chi/eventos/no-es-un-uuid/consolidado", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Evento no válido."));
    }

    /**
     * An unknown event answers 404 in the list
     *
     * @throws Exception if the request fails
     */
    @Test
    void peopleOfAnUnknownEventAnswers404() throws Exception {
        when(events.existsById(EVENT)).thenReturn(false);

        mockMvc.perform(as(BASE + "/personas", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));
    }

    /**
     * If MS05 does not deliver the zones of the brigadier, the list answers 503
     *
     * @throws Exception if the request fails
     */
    @Test
    void peopleOfTheBrigadierWithoutZonesOfMs05Answers503() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(brigadierZones.zonasDe(any(), any())).thenThrow(ApiException.dependenciaNoDisponible());

        mockMvc.perform(as(BASE + "/personas", "BRIGADISTA"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"));

        verifyNoInteractions(checkins, names);
    }

    /**
     * The pending people answer 200 with the page of the standards and the fields of the contract in snake_case,
     * with the total of pending people in total_elementos
     *
     * @throws Exception if the request fails
     */
    @Test
    void pendingAnswers200WithThePageAndTheContract() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(any(), any(), any()))
                .thenReturn(Optional.of(Set.of(ANA, LUIS)));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(List.of(ANA, LUIS)))
                .thenReturn(Map.of(ANA, new DatosUsuario("Ana Pérez", "ana.perez@escuelaing.edu.co")));

        mockMvc.perform(as(BASE + "/pendientes", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.contenido[0].correo").value("ana.perez@escuelaing.edu.co"))
                .andExpect(jsonPath("$.contenido[1].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.contenido[1].nombre").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].correo").value(nullValue()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(2))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * Who already reported does not appear, and the total counts only the missing people
     *
     * @throws Exception if the request fails
     */
    @Test
    void whoAlreadyReportedIsNotPending() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(any(), any(), any()))
                .thenReturn(Optional.of(Set.of(ANA, LUIS)));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of(ANA));
        when(names.datosDe(List.of(LUIS)))
                .thenReturn(Map.of(LUIS, new DatosUsuario("Luis Gómez", "luis.gomez@escuelaing.edu.co")));

        mockMvc.perform(as(BASE + "/pendientes", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.total_elementos").value(1));
    }

    /**
     * It returns the requested page: the content is only that part and the total and the pages are those of the
     * whole list
     *
     * @throws Exception if the request fails
     */
    @Test
    void pendingKeepsTheRequestedPageAndSize() throws Exception {
        Set<UUID> population = new HashSet<>();
        for (int i = 1; i <= 25; i++) {
            population.add(UUID.fromString(String.format("00000000-0000-4000-8000-%012d", i)));
        }
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.of(population));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(any())).thenReturn(Map.of());

        mockMvc.perform(as(BASE + "/pendientes?page=2&size=10", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(5)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value("00000000-0000-4000-8000-000000000021"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total_elementos").value(25))
                .andExpect(jsonPath("$.total_paginas").value(3));
    }

    /**
     * The roles with the permission, alone or together with BRIGADISTA, can query the pending people
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA,COORDINADOR" })
    void theRolesWithThePermissionCanQueryPending(String role) throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.of(Set.of(ANA)));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(any())).thenReturn(Map.of());

        mockMvc.perform(as(BASE + "/pendientes", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(1));
    }

    /**
     * The brigadier gets 200 with total 0 and an empty list, without querying MS01, MS05 nor the check-ins (A6)
     *
     * @throws Exception if the request fails
     */
    @Test
    void theBrigadierGets200WithTotalZeroAndAnEmptyList() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));

        mockMvc.perform(as(BASE + "/pendientes", "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", empty()))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));

        verifyNoInteractions(targetPopulation, brigadierZones, checkins, names);
    }

    /**
     * A role without ms04:dashboard:read gets 403 without querying anything
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN", "ESTUDIANTE,PROFESOR" })
    void theRolesWithoutThePermissionCannotQueryPending(String role) throws Exception {
        mockMvc.perform(as(BASE + "/pendientes", role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(events, checkins, names, brigadierZones, targetPopulation);
    }

    /**
     * Without identity headers the pending people answer 401
     *
     * @throws Exception if the request fails
     */
    @Test
    void pendingWithoutIdentityHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get(BASE + "/pendientes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * An invalid pagination answers 400 with the message of form F-03 and queries nothing
     *
     * @param query invalid pagination parameters
     * @param field reported parameter
     * @param message message of form F-03
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "page=abc|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100).",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void pendingWithInvalidPaginationAnswers400(String query, String field, String message) throws Exception {
        mockMvc.perform(as(BASE + "/pendientes?" + query, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(field))
                .andExpect(jsonPath("$.errores[0].mensaje").value(message));

        verifyNoInteractions(events, checkins, names, targetPopulation);
    }

    /**
     * An unknown event answers 404
     *
     * @throws Exception if the request fails
     */
    @Test
    void pendingOfAnUnknownEventAnswers404() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.empty());

        mockMvc.perform(as(BASE + "/pendientes", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));

        verifyNoInteractions(targetPopulation, checkins, names);
    }

    /**
     * If MS01 does not deliver the target population, the pending people answer 503 with the message of F-03 and the
     * consolidation of the same event keeps working
     *
     * @throws Exception if a request fails
     */
    @Test
    void withoutPopulationPendingAnswers503AndTheConsolidationKeepsWorking() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.empty());
        when(checkins.contarVigentes(EVENT, false, NONE, NONE, NONE)).thenReturn(List.of());

        mockMvc.perform(as(BASE + "/pendientes", "COORDINADOR"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje").value("La lista de pendientes no está disponible en este momento."))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
        mockMvc.perform(as(BASE + "/consolidado", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_reportados").value(0));

        verifyNoInteractions(names);
    }

    /**
     * The query to MS01 carries the convened roles, the creation cut and the identity taken from the headers
     *
     * @throws Exception if the request fails
     */
    @Test
    void thePopulationIsRequestedWithRolesCutAndIdentity() throws Exception {
        when(events.findById(EVENT)).thenReturn(Optional.of(convenedEvent()));
        when(targetPopulation.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.of(Set.of()));
        when(checkins.buscarUsuariosConEstadoVigente(EVENT)).thenReturn(List.of());
        when(names.datosDe(any())).thenReturn(Map.of());

        mockMvc.perform(as(BASE + "/pendientes", "AUDITOR")).andExpect(status().isOk());

        verify(targetPopulation).usuariosActivosPorRoles(ROLES, START,
                TestIdentities.identity(UUID.fromString(USER), "AUDITOR"));
    }

    /**
     * The list answers 200 with the page of the standards and the fields of the contract in snake_case: the
     * location, the date of the current check-in and the request with the MS05 id and its sending state
     *
     * @throws Exception if the request fails
     */
    @Test
    void assistanceAnswers200WithThePageAndTheContract() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(assistanceCheckin(ANA, TipoUbicacion.EDIFICIO, BUILDING),
                        assistanceCheckin(LUIS, TipoUbicacion.ESPACIO, SPACE)), PageRequest.of(0, 20), 2));
        when(names.datosDe(List.of(ANA, LUIS)))
                .thenReturn(Map.of(ANA, new DatosUsuario("Ana Pérez", "ana.perez@escuelaing.edu.co")));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA, LUIS))).thenReturn(List.of(
                request(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA),
                request(LUIS, null, EstadoEnvio.PENDIENTE_ENVIO)));

        mockMvc.perform(as(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.contenido[0].correo").value("ana.perez@escuelaing.edu.co"))
                .andExpect(jsonPath("$.contenido[0].ubicacion.tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.contenido[0].ubicacion.id").value(BUILDING.toString()))
                .andExpect(jsonPath("$.contenido[0].fecha_reporte").value("2026-11-20T15:04:12Z"))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.id").value("SOL-2026-0042"))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.estado_envio").value("ENVIADA"))
                .andExpect(jsonPath("$.contenido[1].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.contenido[1].nombre").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].correo").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].ubicacion.tipo").value("ESPACIO"))
                .andExpect(jsonPath("$.contenido[1].ubicacion.id").value(SPACE.toString()))
                .andExpect(jsonPath("$.contenido[1].solicitud_asistencia.id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].solicitud_asistencia.estado_envio").value("PENDIENTE_ENVIO"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(2))
                .andExpect(jsonPath("$.total_paginas").value(1));
    }

    /**
     * A rejected or pending request is shown as such, so the coordinator can register it by hand
     *
     * @param sendingState sending state of the request
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "PENDIENTE_ENVIO", "RECHAZADA" })
    void assistanceShowsTheRequestsThatDidNotReachMs05(String sendingState) throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(assistanceCheckin(ANA, TipoUbicacion.PUNTO_ENCUENTRO, POINT_A)),
                        PageRequest.of(0, 20), 1));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA)))
                .thenReturn(List.of(request(ANA, null, EstadoEnvio.valueOf(sendingState))));

        mockMvc.perform(as(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].ubicacion.tipo").value("PUNTO_ENCUENTRO"))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].solicitud_asistencia.estado_envio").value(sendingState));
    }

    /**
     * A1: if nobody needs assistance it answers 200 with the empty list and the totals in 0
     *
     * @throws Exception if the request fails
     */
    @Test
    void ifNobodyNeedsAssistanceItAnswers200WithTheEmptyList() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(as(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", empty()))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));
    }

    /**
     * It requests the given page and passes it to the query
     *
     * @throws Exception if the request fails
     */
    @Test
    void assistanceKeepsTheRequestedPageAndSize() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(1, 1)))
                .thenReturn(new PageImpl<>(List.of(assistanceCheckin(LUIS, TipoUbicacion.ESPACIO, SPACE)),
                        PageRequest.of(1, 1), 3));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(LUIS)))
                .thenReturn(List.of(request(LUIS, "SOL-2026-0043", EstadoEnvio.ENVIADA)));

        mockMvc.perform(as(BASE + "/asistencia?page=1&size=1", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(LUIS.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total_elementos").value(3))
                .andExpect(jsonPath("$.total_paginas").value(3));
    }

    /**
     * The roles with ms04:dashboard:read can query the assistance list
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA,COORDINADOR" })
    void theRolesWithThePermissionCanQueryAssistance(String role) throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigentesConAsistencia(EVENT, false, NONE, NONE, NONE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(as(BASE + "/asistencia", role)).andExpect(status().isOk());

        verifyNoInteractions(brigadierZones);
    }

    /**
     * A2: the brigadier queries with the zones that MS05 delivers and the user id comes from the header
     *
     * @throws Exception if the request fails
     */
    @Test
    void assistanceOfTheBrigadierUsesTheirZonesAndTheirIdentity() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(brigadierZones.zonasDe(any(), any()))
                .thenReturn(new ZonasAsignadas(Set.of(BUILDING), Set.of(SPACE), Set.of(POINT_A)));
        when(checkins.buscarVigentesConAsistencia(EVENT, true, Set.of(POINT_A), Set.of(BUILDING), Set.of(SPACE),
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(assistanceCheckin(ANA, TipoUbicacion.EDIFICIO, BUILDING)),
                        PageRequest.of(0, 20), 1));
        when(requests.findByEventoIdAndUsuarioIdIn(EVENT, List.of(ANA)))
                .thenReturn(List.of(request(ANA, "SOL-2026-0042", EstadoEnvio.ENVIADA)));

        mockMvc.perform(as(BASE + "/asistencia", "BRIGADISTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.total_elementos").value(1));

        verify(brigadierZones).zonasDe(TestIdentities.identity(UUID.fromString(USER), "BRIGADISTA"), EVENT);
    }

    /**
     * If MS05 does not deliver the zones of the brigadier, the list answers 503
     *
     * @throws Exception if the request fails
     */
    @Test
    void assistanceOfTheBrigadierWithoutZonesOfMs05Answers503() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(brigadierZones.zonasDe(any(), any())).thenThrow(ApiException.dependenciaNoDisponible());

        mockMvc.perform(as(BASE + "/asistencia", "BRIGADISTA"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.codigo").value("DEPENDENCIA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(checkins, requests, names);
    }

    /**
     * A4: a role without ms04:dashboard:read gets 403 without querying anything
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "ADMIN", "ESTUDIANTE,PROFESOR" })
    void theRolesWithoutThePermissionCannotQueryAssistance(String role) throws Exception {
        mockMvc.perform(as(BASE + "/asistencia", role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(events, checkins, requests, names, brigadierZones);
    }

    /**
     * Without identity headers the list answers 401
     *
     * @throws Exception if the request fails
     */
    @Test
    void assistanceWithoutIdentityHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get(BASE + "/asistencia"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /**
     * An invalid pagination answers 400 with the message of form F-03 and queries nothing
     *
     * @param query invalid pagination parameters
     * @param field reported parameter
     * @param message message of form F-03
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "page=abc|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100).",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void assistanceWithInvalidPaginationAnswers400(String query, String field, String message) throws Exception {
        mockMvc.perform(as(BASE + "/asistencia?" + query, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(field))
                .andExpect(jsonPath("$.errores[0].mensaje").value(message));

        verifyNoInteractions(events, checkins, requests, names);
    }

    /**
     * An event id that is not a UUID answers 400 with the message of RG-03
     *
     * @throws Exception if the request fails
     */
    @Test
    void assistanceWithAnInvalidEventIdAnswers400() throws Exception {
        mockMvc.perform(as("/api/v1/chi/eventos/no-es-un-uuid/asistencia", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Evento no válido."));
    }

    /**
     * A5: an unknown event answers 404
     *
     * @throws Exception if the request fails
     */
    @Test
    void assistanceOfAnUnknownEventAnswers404() throws Exception {
        when(events.existsById(EVENT)).thenReturn(false);

        mockMvc.perform(as(BASE + "/asistencia", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));

        verifyNoInteractions(checkins, requests, names);
    }
}
