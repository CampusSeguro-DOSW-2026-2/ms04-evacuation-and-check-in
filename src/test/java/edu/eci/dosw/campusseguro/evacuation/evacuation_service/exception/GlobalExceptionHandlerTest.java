package edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * El manejador global convierte cualquier error en el {@code ErrorResponse} de los Estándares: cinco campos,
 * código del catálogo y el correlation id de la petición
 */
class GlobalExceptionHandlerTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        // Conversor estricto, igual que la aplicación con fail-on-unknown-properties: true (application.yaml)
        JacksonJsonHttpMessageConverter estricto = new JacksonJsonHttpMessageConverter(
                JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
        mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setMessageConverters(estricto)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void cadaFabricaDeApiExceptionTraduceSuEstadoYCodigo() throws Exception {
        verificar("/falla/no-encontrado", 404, "NO_ENCONTRADO", "Evento no encontrado.");
        verificar("/falla/conflicto", 409, "CONFLICTO", "El check-in de este evento no está habilitado.");
        verificar("/falla/regla", 422, "REGLA_NEGOCIO", "Este evento no le aplica.");
        verificar("/falla/dependencia", 503, "DEPENDENCIA_NO_DISPONIBLE",
                "El servicio no está disponible en este momento. Intente de nuevo.");
        verificar("/falla/prohibido", 403, "PROHIBIDO",
                "No cuenta con los permisos suficientes para realizar esta operación.");
        verificar("/falla/no-implementado", 501, "NO_IMPLEMENTADO", "Operación pendiente de implementar: prueba.");
    }

    private void verificar(String ruta, int estado, String codigo, String mensaje) throws Exception {
        mockMvc.perform(get(ruta).header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().is(estado))
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.status").value(estado))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    @Test
    void laRespuestaTieneExactamenteLosCincoCampos() throws Exception {
        mockMvc.perform(get("/falla/conflicto"))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.codigo").exists())
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.correlation_id").exists());
    }

    @Test
    void sinCorrelationIdLoGeneraYLoDevuelve() throws Exception {
        String devuelto = mockMvc.perform(get("/falla/conflicto"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getHeader(CorrelationIds.HEADER);

        assertEquals(4, UUID.fromString(devuelto).version());
    }

    @Test
    void laValidacionUsaElNombreDelCampoDelContrato() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"));
    }

    @Test
    void rechazaUnCampoQueElContratoNoDefine() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"punto_encuentro_id\":\"" + UUID.randomUUID() + "\",\"usuario_id\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El campo no está permitido."));
    }

    @Test
    void unValorDeOtroTipoSeSeñalaEnSuCampo() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON)
                .content("{\"punto_encuentro_id\":\"no-es-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"));
    }

    @Test
    void unJsonMalFormadoEsValidacionSinDetalle() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    @Test
    void unIdConFormatoInvalidoEsValidacionDeEseParametro() throws Exception {
        mockMvc.perform(get("/eventos/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"));
    }

    @Test
    void unParametroObligatorioQueFaltaEsValidacion() throws Exception {
        mockMvc.perform(get("/con-parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El parámetro es obligatorio."));
    }

    @Test
    void laFaltaDeCabecerasDeIdentidadEsNoAutenticado() throws Exception {
        mockMvc.perform(get("/con-identidad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(get("/con-otra-cabecera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("X-Otra"));
    }

    @Test
    void unMetodoNoSoportadoEsMetodoNoPermitidoConAllow() throws Exception {
        mockMvc.perform(post("/eventos/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void unTipoDeContenidoNoSoportadoEsTipoNoSoportado() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
    }

    @Test
    void unaFallaNoControladaEsErrorInternoSinDetalles() throws Exception {
        mockMvc.perform(get("/falla/inesperada"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje").value("Error interno del servidor."))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    /**
     * Cuerpo de prueba con el nombre del contrato en snake_case
     */
    record CuerpoDePrueba(@JsonProperty("punto_encuentro_id") @NotNull UUID puntoEncuentroId) {
    }

    @RestController
    static class ControladorDePrueba {

        @GetMapping("/falla/no-encontrado")
        void noEncontrado() {
            throw ApiException.noEncontrado("Evento no encontrado.");
        }

        @GetMapping("/falla/conflicto")
        void conflicto() {
            throw ApiException.conflicto("El check-in de este evento no está habilitado.");
        }

        @GetMapping("/falla/regla")
        void regla() {
            throw ApiException.reglaNegocio("Este evento no le aplica.");
        }

        @GetMapping("/falla/dependencia")
        void dependencia() {
            throw ApiException.dependenciaNoDisponible();
        }

        @GetMapping("/falla/prohibido")
        void prohibido() {
            throw ApiException.prohibido();
        }

        @GetMapping("/falla/no-implementado")
        void noImplementado() {
            throw ApiException.noImplementado("prueba");
        }

        @GetMapping("/falla/inesperada")
        void inesperada() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }

        @PostMapping("/cuerpo")
        void cuerpo(@Valid @RequestBody CuerpoDePrueba cuerpo) {
            // solo valida
        }

        @GetMapping("/eventos/{eventoId}")
        void evento(@PathVariable UUID eventoId) {
            // solo valida el formato
        }

        @GetMapping("/con-parametro")
        void conParametro(@RequestParam String estado) {
            // solo valida
        }

        @GetMapping("/con-identidad")
        void conIdentidad(@RequestHeader("X-User-Id") String userId) {
            // solo valida
        }

        @GetMapping("/con-otra-cabecera")
        void conOtraCabecera(@RequestHeader("X-Otra") String otra) {
            // solo valida
        }
    }

    @Test
    void apiExceptionConservaElDetalleDeCampos() {
        ApiException ex = ApiException.validacion("estado", "Seleccione un estado válido.");

        assertEquals(400, ex.getCode().getStatus());
        assertEquals(List.of(new ErrorDetail("estado", "Seleccione un estado válido.")), ex.getErrors());
    }
}
