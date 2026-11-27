package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * The two security chains with the real filters: protected routes by identity headers and internal routes of MS06
 * by X-Service-Name (RNF-MS04-10, RN-MS04-23)
 */
@WebMvcTest(SecurityHttpTest.TestController.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        SecurityHttpTest.TestController.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class SecurityHttpTest {

    private static final String USER = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String EVENT = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    /**
     * Adds the identity headers that the Gateway sends for a user with an initial role
     *
     * @param request request to send
     * @param role initial role of the user
     * @return the request with the identity headers
     */
    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) {
        return request.header(IdentityHeaders.USER_ID, USER)
                .header(IdentityHeaders.USER_ROLE, role)
                .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions(role));
    }

    /**
     * A protected route without headers is unauthenticated with the standard error body
     *
     * @throws Exception if the request fails
     */
    @Test
    void aProtectedRouteWithoutHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/activos").header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    /**
     * A protected route with valid headers passes
     *
     * @throws Exception if the request fails
     */
    @Test
    void aProtectedRouteWithValidHeadersPasses() throws Exception {
        mockMvc.perform(as(get("/api/v1/chi/eventos/activos"), "ESTUDIANTE"))
                .andExpect(status().isOk());
    }

    /**
     * A role with invalid format in the header is unauthenticated
     *
     * @throws Exception if the request fails
     */
    @Test
    void aRoleWithInvalidFormatIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/activos")
                        .header(IdentityHeaders.USER_ID, USER)
                        .header(IdentityHeaders.USER_ROLE, "superusuario")
                        .header(IdentityHeaders.USER_PERMISSION, TestIdentities.permissions("ESTUDIANTE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.INVALID_ROLE_MESSAGE));
    }

    /**
     * Without X-User-Permission the request is unauthenticated
     *
     * @throws Exception if the request fails
     */
    @Test
    void withoutXUserPermissionIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/activos")
                        .header(IdentityHeaders.USER_ID, USER)
                        .header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_MESSAGE));
    }

    /**
     * The roles are not a fixed set: a new role authorizes only with the permissions it brings
     *
     * @throws Exception if the request fails
     */
    @Test
    void aNewRoleAuthorizesOnlyWithItsPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/solo-dashboard")
                        .header(IdentityHeaders.USER_ID, USER)
                        .header(IdentityHeaders.USER_ROLE, "JEFE_DE_PISO")
                        .header(IdentityHeaders.USER_PERMISSION, "ms04:dashboard:read"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chi/eventos/solo-dashboard")
                        .header(IdentityHeaders.USER_ID, USER)
                        .header(IdentityHeaders.USER_ROLE, "COORDINADOR")
                        .header(IdentityHeaders.USER_PERMISSION, "ms04:checkin:write"))
                .andExpect(status().isForbidden());
    }

    /**
     * Without the permission the controller answers 403
     *
     * @throws Exception if the request fails
     */
    @Test
    void withoutThePermissionTheControllerAnswers403() throws Exception {
        mockMvc.perform(as(get("/api/v1/chi/eventos/solo-dashboard"), "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(as(get("/api/v1/chi/eventos/solo-dashboard"), "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/v1/chi/eventos/solo-dashboard"), "AUDITOR"))
                .andExpect(status().isOk());
    }

    /**
     * The internal route of the event state without service is unauthenticated
     *
     * @throws Exception if the request fails
     */
    @Test
    void theInternalRouteWithoutServiceIsUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/v1/chi/eventos/" + EVENT + "/estado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_SERVICE_MESSAGE));
    }

    /**
     * The internal route ignores the identity of a user
     *
     * @throws Exception if the request fails
     */
    @Test
    void theInternalRouteIgnoresTheUserIdentity() throws Exception {
        mockMvc.perform(as(put("/api/v1/chi/eventos/" + EVENT + "/estado"), "COORDINADOR"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The allowed service passes through both internal routes
     *
     * @throws Exception if the request fails
     */
    @Test
    void theAllowedServicePassesThroughBothInternalRoutes() throws Exception {
        mockMvc.perform(put("/api/v1/chi/eventos/" + EVENT + "/estado")
                        .header(IdentityHeaders.SERVICE_NAME, "ms06"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chi/eventos/" + EVENT + "/checkins")
                        .header(IdentityHeaders.SERVICE_NAME, " MS06 "))
                .andExpect(status().isOk());
    }

    /**
     * Another service is forbidden on both internal routes
     *
     * @throws Exception if the request fails
     */
    @Test
    void anotherServiceIsForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/chi/eventos/" + EVENT + "/estado")
                        .header(IdentityHeaders.SERVICE_NAME, "ms03"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(get("/api/v1/chi/eventos/" + EVENT + "/checkins")
                        .header(IdentityHeaders.SERVICE_NAME, "ms03"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    /**
     * The POST of check-ins stays protected although the GET is internal
     *
     * @throws Exception if the request fails
     */
    @Test
    void thePostOfCheckinsStaysProtected() throws Exception {
        mockMvc.perform(post("/api/v1/chi/eventos/" + EVENT + "/checkins")
                        .header(IdentityHeaders.SERVICE_NAME, "ms06"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_MESSAGE));
        mockMvc.perform(as(post("/api/v1/chi/eventos/" + EVENT + "/checkins"), "ESTUDIANTE"))
                .andExpect(status().isOk());
    }

    /**
     * Test controller with the real routes of MS04 that separate the two chains
     */
    @RestController
    static class TestController {

        /**
         * Protected route of the active events
         *
         * @param requester identity of the requester
         */
        @GetMapping("/api/v1/chi/eventos/activos")
        void active(RequesterIdentity requester) {
            PermissionChecker.require(requester, Permiso.CHECKIN_READ_OWN);
        }

        /**
         * Protected route that requires the dashboard permission
         *
         * @param requester identity of the requester
         */
        @GetMapping("/api/v1/chi/eventos/solo-dashboard")
        void dashboardOnly(RequesterIdentity requester) {
            PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        }

        /**
         * Internal route of MS06 that receives the state of the event
         */
        @PutMapping("/api/v1/chi/eventos/{eventoId}/estado")
        void state() {
            // internal route of MS06
        }

        /**
         * Internal route of MS06 with the detail of the check-ins
         */
        @GetMapping("/api/v1/chi/eventos/{eventoId}/checkins")
        void detail() {
            // internal route of MS06
        }

        /**
         * Protected route of the check-in of a user
         *
         * @param requester identity of the requester
         */
        @PostMapping("/api/v1/chi/eventos/{eventoId}/checkins")
        void checkin(RequesterIdentity requester) {
            PermissionChecker.require(requester, Permiso.CHECKIN_WRITE);
        }
    }
}
