package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.HistorialService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * HTTP contract of RF-MS04-09: GET /api/v1/chi/eventos/{eventoId}/historial with the real service and the mocked
 * repositories. It checks the shape of the answer, the filters, the standard errors, the permission ms04:history:read
 * and that the history does not depend on who queries it
 */
@WebMvcTest(controllers = HistorialController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        HistorialService.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class HistorialHttpTest {

    private static final String USER = "6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13";
    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID POINT_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID NO_USER = null;
    private static final EstadoCheckin NO_STATE = null;
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String BASE = "/api/v1/chi/eventos/" + EVENT;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoRepository events;

    @MockitoBean
    private CheckinRepository checkins;

    @MockitoBean
    private UsuarioNombresClient names;

    /**
     * Creates a check-in as the database returns it, with the id and the date that it assigns
     *
     * @param user user that reports
     * @param state reported state
     * @param point meeting point, only with SAFE
     * @param type location type, only with NEEDS_ASSISTANCE
     * @param location location id, only with NEEDS_ASSISTANCE
     * @param registeredAt date of the report
     * @return the check-in with its id and registration date
     */
    private Checkin checkin(UUID user, EstadoCheckin state, UUID point, TipoUbicacion type, UUID location,
            String registeredAt) {
        Checkin checkin = new Checkin(EVENT, user, state, point, type, location);
        ReflectionTestUtils.setField(checkin, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(checkin, "registradoEn", Instant.parse(registeredAt));
        return checkin;
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
     * The history answers 200 with the fields of the contract in snake_case: each report with its id, user, state,
     * meeting point or location and date, and the pagination of the standards
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyAnswers200WithTheContractInSnakeCase() throws Exception {
        Checkin evacuating = checkin(ANA, EstadoCheckin.EVACUATING, null, null, null, "2026-11-20T15:02:05Z");
        Checkin assistance = checkin(ANA, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING,
                "2026-11-20T15:04:12Z");
        Checkin safe = checkin(ANA, EstadoCheckin.SAFE, POINT_A, null, null, "2026-11-20T15:09:40Z");
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(List.of(evacuating, assistance, safe), PageRequest.of(0, 20), 3));

        mockMvc.perform(as(BASE + "/historial", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elementos").value(3))
                .andExpect(jsonPath("$.total_paginas").value(1))
                .andExpect(jsonPath("$.contenido", hasSize(3)))
                .andExpect(jsonPath("$.contenido[0].checkin_id").value(evacuating.getId().toString()))
                .andExpect(jsonPath("$.contenido[0].usuario_id").value(ANA.toString()))
                .andExpect(jsonPath("$.contenido[0].estado").value("EVACUATING"))
                .andExpect(jsonPath("$.contenido[0].punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].ubicacion").value(nullValue()))
                .andExpect(jsonPath("$.contenido[0].registrado_en").value("2026-11-20T15:02:05Z"))
                .andExpect(jsonPath("$.contenido[1].estado").value("NEEDS_ASSISTANCE"))
                .andExpect(jsonPath("$.contenido[1].punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.contenido[1].ubicacion.tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.contenido[1].ubicacion.id").value(BUILDING.toString()))
                .andExpect(jsonPath("$.contenido[1].registrado_en").value("2026-11-20T15:04:12Z"))
                .andExpect(jsonPath("$.contenido[2].estado").value("SAFE"))
                .andExpect(jsonPath("$.contenido[2].punto_encuentro_id").value(POINT_A.toString()))
                .andExpect(jsonPath("$.contenido[2].ubicacion").value(nullValue()))
                .andExpect(jsonPath("$.contenido[2].registrado_en").value("2026-11-20T15:09:40Z"));
    }

    /**
     * A4: an event without check-ins answers 200 with the empty list, total 0 and without pages
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyWithoutCheckinsAnswers200WithTheEmptyList() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(as(BASE + "/historial", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));
    }

    /**
     * The requested user, state, page and size reach the query, and the history does not depend on who queries it:
     * the filtered user may be another one than the requester
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyUsesTheRequestedFiltersAndNotTheIdentityOfTheRequester() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarHistorial(EVENT, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 12));

        mockMvc.perform(as(BASE + "/historial?usuario_id=" + ANA + "&estado=SAFE&page=2&size=5", "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total_elementos").value(12))
                .andExpect(jsonPath("$.total_paginas").value(3));

        verify(checkins).buscarHistorial(EVENT, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5));
    }

    /**
     * Without filters the history brings the whole event, with 20 per page
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyWithoutFiltersRequestsTheWholeEventWithTwentyPerPage() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(as(BASE + "/historial", "COORDINADOR")).andExpect(status().isOk());

        verify(checkins).buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20));
    }

    /**
     * Each state of the catalog can be used as filter
     *
     * @param state state of the catalog
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "SAFE", "EVACUATING", "NEEDS_ASSISTANCE", "OFF_CAMPUS" })
    void historyAcceptsEachStateOfTheCatalogAsFilter(String state) throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarHistorial(EVENT, NO_USER, EstadoCheckin.valueOf(state), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(as(BASE + "/historial?estado=" + state, "COORDINADOR")).andExpect(status().isOk());

        verify(checkins).buscarHistorial(EVENT, NO_USER, EstadoCheckin.valueOf(state), PageRequest.of(0, 20));
    }

    /**
     * The roles with ms04:history:read can query the history, also together with other roles
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "COORDINADOR", "ORGANIZADOR", "AUDITOR", "BRIGADISTA,COORDINADOR", "AUDITOR,PROFESOR" })
    void theRolesWithThePermissionCanQueryTheHistory(String role) throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarHistorial(EVENT, NO_USER, NO_STATE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(as(BASE + "/historial", role)).andExpect(status().isOk());
    }

    /**
     * The roles without ms04:history:read get 403, the brigadier included, and the database is not queried
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA", "ADMIN", "ESTUDIANTE,PROFESOR",
            "BRIGADISTA,PROFESOR" })
    void theRolesWithoutThePermissionCannotQueryTheHistory(String role) throws Exception {
        mockMvc.perform(as(BASE + "/historial", role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(events, checkins);
    }

    /**
     * A role without permission gets 403 before the error of the pagination
     *
     * @throws Exception if the request fails
     */
    @Test
    void thePermissionIsCheckedBeforeThePagination() throws Exception {
        mockMvc.perform(as(BASE + "/historial?page=-1&size=101", "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));

        verifyNoInteractions(events, checkins);
    }

    /**
     * Without identity headers it answers 401 NO_AUTENTICADO
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyWithoutIdentityHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get(BASE + "/historial").header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(events, checkins);
    }

    /**
     * An invalid pagination answers 400 with the message of form F-04 and queries nothing
     *
     * @param query invalid pagination parameters
     * @param field reported parameter
     * @param message message of form F-04
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "page=-1|page|Página no válida.",
            "page=abc|page|Página no válida.",
            "size=0|size|Tamaño de página no válido (1 a 100).",
            "size=101|size|Tamaño de página no válido (1 a 100).",
            "size=abc|size|Tamaño de página no válido (1 a 100)." })
    void historyWithInvalidPaginationAnswers400(String query, String field, String message) throws Exception {
        mockMvc.perform(as(BASE + "/historial?" + query, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value(field))
                .andExpect(jsonPath("$.errores[0].mensaje").value(message));

        verifyNoInteractions(events, checkins);
    }

    /**
     * Invalid page and size at the same time return one error per parameter
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyWithInvalidPageAndSizeReturnsOneErrorPerParameter() throws Exception {
        mockMvc.perform(as(BASE + "/historial?page=-1&size=101", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[0].campo").value("page"))
                .andExpect(jsonPath("$.errores[1].campo").value("size"));
    }

    /**
     * A user that is not a UUID answers 400 with the message of form F-04
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyWithAnInvalidUserAnswers400() throws Exception {
        mockMvc.perform(as(BASE + "/historial?usuario_id=no-es-un-uuid", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Usuario no válido."));

        verifyNoInteractions(events, checkins);
    }

    /**
     * A state outside the catalog answers 400 with the message of form F-04
     *
     * @param state value outside the catalog
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "TODOS", "safe", "FINALIZADO", "123" })
    void historyWithAnInvalidStateAnswers400(String state) throws Exception {
        mockMvc.perform(as(BASE + "/historial?estado=" + state, "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("estado"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Estado no válido."));

        verifyNoInteractions(events, checkins);
    }

    /**
     * An event id that is not a UUID answers 400 with the message of RG-03
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyWithAnInvalidEventIdAnswers400() throws Exception {
        mockMvc.perform(as("/api/v1/chi/eventos/no-es-un-uuid/historial", "COORDINADOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Evento no válido."));

        verifyNoInteractions(events, checkins);
    }

    /**
     * A3: an unknown event answers 404
     *
     * @throws Exception if the request fails
     */
    @Test
    void historyOfAnUnknownEventAnswers404() throws Exception {
        when(events.existsById(EVENT)).thenReturn(false);

        mockMvc.perform(as(BASE + "/historial", "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(checkins);
    }
}
