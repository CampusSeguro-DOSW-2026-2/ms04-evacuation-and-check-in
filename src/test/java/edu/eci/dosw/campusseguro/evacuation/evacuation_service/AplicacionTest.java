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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * La aplicación completa con su configuración real (application.yaml) sobre PostgreSQL con el esquema de database/:
 * arranca, valida las entidades contra las tablas y cumple los Estándares de Integración en lo transversal
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
class AplicacionTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String BASE = "/api/v1/chi/eventos";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void arrancaYValidaLasEntidadesContraElEsquema() {
        // Si las entidades no coinciden con las tablas, ddl-auto: validate impide que el contexto arranque
    }

    @Test
    void elHealthEsPublicoYNoDaDetalles() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.groups").doesNotExist())
                .andExpect(header().exists(CorrelationIds.HEADER));
    }

    @Test
    void soloElHealthDeActuatorEstaExpuesto() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerNoSeExponePorDefecto() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinIdentidadUnaRutaDelContratoEsNoAutenticadaConElErrorEstandar() throws Exception {
        String correlation = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

        mockMvc.perform(get(BASE + "/activos").header(CorrelationIds.HEADER, correlation))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CorrelationIds.HEADER, correlation))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.correlation_id").value(correlation));
    }

    @Test
    void unaRutaInexistenteEsNoEncontradaConElErrorEstandar() throws Exception {
        mockMvc.perform(get("/api/v1/chi/no-existe")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    @Test
    void nadieModificaOEliminaEventosPorLaApiDeUsuarios() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void unTipoDeContenidoNoSoportadoEsTipoNoSoportado() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/checkins").contentType(MediaType.TEXT_PLAIN)
                .content("hola")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
    }

    @Test
    void laConfiguracionRealRechazaUnIdDeUsuarioEnElCuerpoDelCheckin() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/checkins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EVACUATING\",\"usuario_id\":\"" + USUARIO + "\"}")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "ESTUDIANTE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El campo no está permitido."));
    }

    @Test
    void laRutaInternaDeMs06ExigeElServicioAunConIdentidadDeUsuario() throws Exception {
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID() + "/checkins")
                .header(IdentityHeaders.USER_ID, USUARIO).header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID() + "/checkins")
                .header(IdentityHeaders.SERVICE_NAME, "ms03"))
                .andExpect(status().isForbidden());
    }
}
