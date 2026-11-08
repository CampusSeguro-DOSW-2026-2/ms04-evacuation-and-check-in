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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.SecurityConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.config.WebMvcConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * Las dos cadenas de seguridad con los filtros reales: rutas protegidas por cabeceras de identidad y rutas
 * internas de MS06 por X-Service-Name (RNF-MS04-10, RN-MS04-23)
 */
@WebMvcTest(SeguridadHttpTest.ControladorDePrueba.class)
@Import({ SecurityConfig.class, WebMvcConfig.class, GlobalExceptionHandler.class, CorrelationIdFilter.class,
        SeguridadHttpTest.ControladorDePrueba.class })
@TestPropertySource(properties = "internal.allowed-services=ms06")
class SeguridadHttpTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String EVENTO = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unaRutaProtegidaSinCabecerasEsNoAutenticadaConElErrorEstandar() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/activos").header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_MESSAGE))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    @Test
    void unaRutaProtegidaConCabecerasValidasPasa() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/activos")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isOk());
    }

    @Test
    void unRolInvalidoEnLaCabeceraEsNoAutenticado() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/activos")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "SUPERUSUARIO"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.INVALID_ROLE_MESSAGE));
    }

    @Test
    void sinElPermisoElControllerResponde403() throws Exception {
        mockMvc.perform(get("/api/v1/chi/eventos/solo-dashboard")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mockMvc.perform(get("/api/v1/chi/eventos/solo-dashboard")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ADMIN"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/chi/eventos/solo-dashboard")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "AUDITOR"))
                .andExpect(status().isOk());
    }

    @Test
    void laRutaInternaDeEstadoSinServicioEsNoAutenticada() throws Exception {
        mockMvc.perform(put("/api/v1/chi/eventos/" + EVENTO + "/estado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_SERVICE_MESSAGE));
    }

    @Test
    void laRutaInternaIgnoraLaIdentidadDeUsuario() throws Exception {
        mockMvc.perform(put("/api/v1/chi/eventos/" + EVENTO + "/estado")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elServicioAutorizadoPasaPorLasDosRutasInternas() throws Exception {
        mockMvc.perform(put("/api/v1/chi/eventos/" + EVENTO + "/estado")
                .header(IdentityHeaders.SERVICE_NAME, "ms06"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chi/eventos/" + EVENTO + "/checkins")
                .header(IdentityHeaders.SERVICE_NAME, " MS06 "))
                .andExpect(status().isOk());
    }

    @Test
    void otroServicioEsProhibido() throws Exception {
        for (String ruta : new String[] { "/estado", "/checkins" }) {
            var peticion = ruta.equals("/estado")
                    ? put("/api/v1/chi/eventos/" + EVENTO + ruta)
                    : get("/api/v1/chi/eventos/" + EVENTO + ruta);
            mockMvc.perform(peticion.header(IdentityHeaders.SERVICE_NAME, "ms03"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        }
    }

    @Test
    void elPostDeCheckinsSigueSiendoProtegidoAunqueElGetSeaInterno() throws Exception {
        mockMvc.perform(post("/api/v1/chi/eventos/" + EVENTO + "/checkins")
                .header(IdentityHeaders.SERVICE_NAME, "ms06"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(IdentityHeaders.MISSING_MESSAGE));
        mockMvc.perform(post("/api/v1/chi/eventos/" + EVENTO + "/checkins")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isOk());
    }

    /**
     * Controller de prueba con las rutas reales de MS04 que distinguen las dos cadenas
     */
    @RestController
    static class ControladorDePrueba {

        @GetMapping("/api/v1/chi/eventos/activos")
        void activos(RequesterIdentity requester) {
            PermissionChecker.require(requester, Permiso.CHECKIN_READ_OWN);
        }

        @GetMapping("/api/v1/chi/eventos/solo-dashboard")
        void soloDashboard(RequesterIdentity requester) {
            PermissionChecker.require(requester, Permiso.DASHBOARD_READ);
        }

        @PutMapping("/api/v1/chi/eventos/{eventoId}/estado")
        void estado() {
            // ruta interna de MS06
        }

        @GetMapping("/api/v1/chi/eventos/{eventoId}/checkins")
        void detalle() {
            // ruta interna de MS06
        }

        @PostMapping("/api/v1/chi/eventos/{eventoId}/checkins")
        void checkin(RequesterIdentity requester) {
            PermissionChecker.require(requester, Permiso.CHECKIN_WRITE);
        }
    }
}
