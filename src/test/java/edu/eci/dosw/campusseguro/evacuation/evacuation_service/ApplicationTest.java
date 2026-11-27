package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * The whole application with its real configuration (application.yaml), without database: the data source and JPA
 * are left out and the repositories and the transaction manager are mocks. It starts with every bean of MS04 and
 * fulfils the integration standards in the cross-cutting concerns: public health without details, only the health
 * exposed, Swagger off by default, the standard error body and the rejection of fields that the contract does not
 * define
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "solicitudes-asistencia.reenvio-ms=3600000" })
@AutoConfigureMockMvc
class ApplicationTest {

    private static final String USER = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String BASE = "/api/v1/chi/eventos";
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventoRepository events;

    @MockitoBean
    private CheckinRepository checkins;

    @MockitoBean
    private SolicitudAsistenciaRepository requests;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    /**
     * Adds the identity headers that the Gateway sends for a user with a role
     *
     * @param request request to send
     * @param role role of the user
     * @return the request with the identity headers
     */
    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) {
        return request.header(IdentityHeaders.USER_ID, USER)
                .header(IdentityHeaders.USER_ROLE, role)
                .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions(role));
    }

    /**
     * The health is public and answers only the status, with the correlation id
     *
     * @throws Exception if the request fails
     */
    @Test
    void theHealthIsPublicAndHasNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(header().exists(CorrelationIds.HEADER));
    }

    /**
     * Only the health of Actuator is exposed
     *
     * @throws Exception if the request fails
     */
    @Test
    void onlyTheHealthOfActuatorIsExposed() throws Exception {
        mockMvc.perform(as(get("/actuator/metrics"), "COORDINADOR"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/actuator/env"), "COORDINADOR"))
                .andExpect(status().isNotFound());
    }

    /**
     * Swagger is not exposed by default
     *
     * @throws Exception if the request fails
     */
    @Test
    void swaggerIsNotExposedByDefault() throws Exception {
        mockMvc.perform(as(get("/v3/api-docs"), "COORDINADOR"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/swagger-ui/index.html"), "COORDINADOR"))
                .andExpect(status().isNotFound());
    }

    /**
     * Without identity a route of the contract is unauthenticated, with the standard error body and the
     * correlation id of the request
     *
     * @throws Exception if the request fails
     */
    @Test
    void withoutIdentityARouteIsUnauthenticated() throws Exception {
        mockMvc.perform(get(BASE + "/activos").header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    /**
     * A route that does not exist is not found, with the standard error body
     *
     * @throws Exception if the request fails
     */
    @Test
    void aMissingRouteIsNotFound() throws Exception {
        mockMvc.perform(as(get("/api/v1/chi/no-existe"), "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    /**
     * No user modifies nor deletes an event through the API
     *
     * @throws Exception if the request fails
     */
    @Test
    void nobodyDeletesAnEventThroughTheApi() throws Exception {
        mockMvc.perform(as(delete(BASE + "/" + UUID.randomUUID()), "COORDINADOR"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    /**
     * A content type that is not JSON is not supported
     *
     * @throws Exception if the request fails
     */
    @Test
    void anUnsupportedContentTypeIsRejected() throws Exception {
        mockMvc.perform(as(post(BASE + "/" + UUID.randomUUID() + "/checkins"), "ESTUDIANTE")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
    }

    /**
     * The real configuration rejects a user id in the body of the check-in: the user comes only from the headers
     *
     * @throws Exception if the request fails
     */
    @Test
    void theRealConfigurationRejectsAUserIdInTheBody() throws Exception {
        mockMvc.perform(as(post(BASE + "/" + UUID.randomUUID() + "/checkins"), "ESTUDIANTE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EVACUATING\",\"usuario_id\":\"" + USER + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El campo no está permitido."));
    }

    /**
     * The internal route of MS06 requires the calling service even with a user identity, and only admits the
     * configured services
     *
     * @throws Exception if the request fails
     */
    @Test
    void theInternalRouteRequiresTheAllowedService() throws Exception {
        mockMvc.perform(as(get(BASE + "/" + UUID.randomUUID() + "/checkins"), "COORDINADOR"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID() + "/checkins")
                        .header(IdentityHeaders.SERVICE_NAME, "ms03"))
                .andExpect(status().isForbidden());
    }
}
