package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.EstadoPropioService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * HTTP contract of RF-MS04-05: GET /api/v1/chi/eventos/{eventoId}/checkins/propio with the real service and the
 * mocked repositories. It checks the shape of the answer, the standard errors and that the identity comes only from
 * the headers
 */
@WebMvcTest(controllers = EstadoPropioController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        EstadoPropioService.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class EstadoPropioHttpTest {

    private static final String USER = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String OTHER_USER = "5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68";
    private static final UUID USER_ID = UUID.fromString(USER);
    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID POINT = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID CHECKIN = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant REGISTERED_AT = Instant.parse("2026-11-20T15:04:12Z");
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String ROUTE = "/api/v1/chi/eventos/" + EVENT + "/checkins/propio";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoRepository events;

    @MockitoBean
    private CheckinRepository checkins;

    @MockitoBean
    private SolicitudAsistenciaRepository requests;

    /**
     * Creates a check-in as the database returns it, with the id and the date that it assigns
     *
     * @param state reported state
     * @param point meeting point, only with SAFE
     * @param type location type, only with NEEDS_ASSISTANCE
     * @param location location id, only with NEEDS_ASSISTANCE
     * @return the check-in with its id and registration date
     */
    private Checkin checkin(EstadoCheckin state, UUID point, TipoUbicacion type, UUID location) {
        Checkin checkin = new Checkin(EVENT, USER_ID, state, point, type, location);
        ReflectionTestUtils.setField(checkin, "id", CHECKIN);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTERED_AT);
        return checkin;
    }

    /**
     * Request of a user with the given role
     *
     * @param role role or roles of the X-User-Role header
     * @return the request with the identity and correlation headers
     */
    private MockHttpServletRequestBuilder as(String role) {
        return get(ROUTE).header(IdentityHeaders.USER_ID, USER)
                .header(IdentityHeaders.USER_ROLE, role)
                .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions(role))
                .header(CorrelationIds.HEADER, CORRELATION);
    }

    /**
     * With SAFE it answers 200 with the fields of the contract in snake_case
     *
     * @throws Exception if the request fails
     */
    @Test
    void withSafeAnswers200WithTheContractInSnakeCase() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, POINT, null, null)));

        mockMvc.perform(as("ESTUDIANTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CHECKIN.toString()))
                .andExpect(jsonPath("$.evento_id").value(EVENT.toString()))
                .andExpect(jsonPath("$.estado").value("SAFE"))
                .andExpect(jsonPath("$.punto_encuentro_id").value(POINT.toString()))
                .andExpect(jsonPath("$.ubicacion").value(nullValue()))
                .andExpect(jsonPath("$.solicitud_asistencia").value(nullValue()))
                .andExpect(jsonPath("$.fecha_hora").value("2026-11-20T15:04:12Z"));
    }

    /**
     * With NEEDS_ASSISTANCE it answers the location and the sending state of the request
     *
     * @throws Exception if the request fails
     */
    @Test
    void withNeedsAssistanceBringsTheLocationAndTheRequestState() throws Exception {
        SolicitudAsistencia pending = new SolicitudAsistencia(CHECKIN, EVENT, USER_ID, TipoUbicacion.EDIFICIO,
                BUILDING, REGISTERED_AT);
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER_ID)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, BUILDING)));
        when(requests.findByEventoIdAndUsuarioId(EVENT, USER_ID)).thenReturn(Optional.of(pending));

        mockMvc.perform(as("PROFESOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NEEDS_ASSISTANCE"))
                .andExpect(jsonPath("$.punto_encuentro_id").value(nullValue()))
                .andExpect(jsonPath("$.ubicacion.tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.ubicacion.id").value(BUILDING.toString()))
                .andExpect(jsonPath("$.solicitud_asistencia.id").value(nullValue()))
                .andExpect(jsonPath("$.solicitud_asistencia.estado_envio")
                        .value(EstadoEnvio.PENDIENTE_ENVIO.name()));
    }

    /**
     * The six roles of the community and the union of roles can query their status
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA", "COORDINADOR", "ORGANIZADOR",
            "ESTUDIANTE,BRIGADISTA" })
    void theRolesOfTheCommunityCanQueryTheirStatus(String role) throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.EVACUATING, null, null, null)));

        mockMvc.perform(as(role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EVACUATING"));
    }

    /**
     * The roles without ms04:checkin:read_own get 403 and the database is not queried
     *
     * @param role value of the X-User-Role header
     * @throws Exception if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = { "AUDITOR", "ADMIN" })
    void theRolesWithoutThePermissionAreForbidden(String role) throws Exception {
        mockMvc.perform(as(role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(events, checkins, requests);
    }

    /**
     * Without identity headers it answers 401 NO_AUTENTICADO
     *
     * @throws Exception if the request fails
     */
    @Test
    void withoutIdentityHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get(ROUTE).header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_MESSAGE))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));

        verifyNoInteractions(events, checkins, requests);
    }

    /**
     * Without check-in it answers 404 with the ErrorResponse of five fields
     *
     * @throws Exception if the request fails
     */
    @Test
    void withoutCheckinAnswers404WithTheStandardError() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(as("ESTUDIANTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No tiene check-in en este evento."))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    /**
     * An unknown event answers 404 and the check-ins are not queried
     *
     * @throws Exception if the request fails
     */
    @Test
    void anUnknownEventAnswers404() throws Exception {
        when(events.existsById(EVENT)).thenReturn(false);

        mockMvc.perform(as("ESTUDIANTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("Evento no encontrado."));

        verifyNoInteractions(checkins, requests);
    }

    /**
     * An event id that is not a UUID answers 400 VALIDACION
     *
     * @throws Exception if the request fails
     */
    @Test
    void anEventIdThatIsNotAUuidIsAValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/no-es-uuid/checkins/propio")
                        .header(IdentityHeaders.USER_ID, USER)
                        .header(IdentityHeaders.USER_ROLE, "ESTUDIANTE")
                        .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions("ESTUDIANTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"));

        verifyNoInteractions(events, checkins, requests);
    }

    /**
     * A user id in the query is ignored: the identity comes only from the headers
     *
     * @throws Exception if the request fails
     */
    @Test
    void theIdentityComesOnlyFromTheHeaders() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.OFF_CAMPUS, null, null, null)));

        mockMvc.perform(as("ESTUDIANTE").queryParam("usuario_id", OTHER_USER).queryParam("userId", OTHER_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OFF_CAMPUS"));

        verify(checkins).buscarVigente(EVENT, USER_ID);
        verify(checkins, never()).buscarVigente(EVENT, UUID.fromString(OTHER_USER));
    }

    /**
     * Querying adds and modifies neither check-ins nor requests
     *
     * @throws Exception if the request fails
     */
    @Test
    void queryingAddsNorModifiesCheckins() throws Exception {
        when(events.existsById(EVENT)).thenReturn(true);
        when(checkins.buscarVigente(EVENT, USER_ID))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, POINT, null, null)));

        mockMvc.perform(as("ESTUDIANTE")).andExpect(status().isOk());

        verify(checkins, never()).save(any());
        verify(requests, never()).save(any());
    }
}
