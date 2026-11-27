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
 * The global handler turns any error into the ErrorResponse of the standards: five fields, a code of the catalog and
 * the correlation id of the request
 */
class GlobalExceptionHandlerTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private MockMvc mockMvc;

    /**
     * Builds a standalone MockMvc with the strict converter of the application, the handler and the correlation filter
     */
    @BeforeEach
    void setUp() {
        // Strict converter, like the application with fail-on-unknown-properties: true (application.yaml)
        JacksonJsonHttpMessageConverter strict = new JacksonJsonHttpMessageConverter(
                JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setMessageConverters(strict)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    /**
     * Each factory of ApiException translates its status and code
     *
     * @throws Exception if a request fails
     */
    @Test
    void eachApiExceptionFactoryTranslatesItsStatusAndCode() throws Exception {
        check("/falla/no-encontrado", 404, "NO_ENCONTRADO", "Evento no encontrado.");
        check("/falla/conflicto", 409, "CONFLICTO", "El check-in de este evento no está habilitado.");
        check("/falla/regla", 422, "REGLA_NEGOCIO", "Este evento no le aplica.");
        check("/falla/dependencia", 503, "DEPENDENCIA_NO_DISPONIBLE",
                "El servicio no está disponible en este momento. Intente de nuevo.");
        check("/falla/prohibido", 403, "PROHIBIDO",
                "No cuenta con los permisos suficientes para realizar esta operación.");
        check("/falla/no-implementado", 501, "NO_IMPLEMENTADO", "Operación pendiente de implementar: prueba.");
    }

    /**
     * Checks the standard error body of a route
     *
     * @param route route that fails
     * @param status expected status
     * @param code expected code
     * @param message expected message
     * @throws Exception if the request fails
     */
    private void check(String route, int status, String code, String message) throws Exception {
        mockMvc.perform(get(route).header(CorrelationIds.HEADER, CORRELATION))
                .andExpect(status().is(status))
                .andExpect(header().string(CorrelationIds.HEADER, CORRELATION))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.codigo").value(code))
                .andExpect(jsonPath("$.mensaje").value(message))
                .andExpect(jsonPath("$.errores", hasSize(0)))
                .andExpect(jsonPath("$.correlation_id").value(CORRELATION));
    }

    /**
     * The answer has exactly the five fields
     *
     * @throws Exception if the request fails
     */
    @Test
    void theAnswerHasExactlyTheFiveFields() throws Exception {
        mockMvc.perform(get("/falla/conflicto"))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.codigo").exists())
                .andExpect(jsonPath("$.mensaje").exists())
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.correlation_id").exists());
    }

    /**
     * Without correlation id one is generated and returned
     *
     * @throws Exception if the request fails
     */
    @Test
    void withoutCorrelationIdOneIsGeneratedAndReturned() throws Exception {
        String returned = mockMvc.perform(get("/falla/conflicto"))
                .andExpect(status().isConflict())
                .andReturn()
                .getResponse()
                .getHeader(CorrelationIds.HEADER);

        assertEquals(4, UUID.fromString(returned).version());
    }

    /**
     * The validation uses the name of the field of the contract
     *
     * @throws Exception if the request fails
     */
    @Test
    void theValidationUsesTheFieldNameOfTheContract() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"));
    }

    /**
     * A field that the contract does not define is rejected
     *
     * @throws Exception if the request fails
     */
    @Test
    void aFieldThatTheContractDoesNotDefineIsRejected() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"punto_encuentro_id\":\"" + UUID.randomUUID() + "\",\"usuario_id\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("usuario_id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El campo no está permitido."));
    }

    /**
     * A value of another type is reported on its field
     *
     * @throws Exception if the request fails
     */
    @Test
    void aValueOfAnotherTypeIsReportedOnItsField() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"punto_encuentro_id\":\"no-es-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("punto_encuentro_id"));
    }

    /**
     * A malformed JSON is a validation error without detail
     *
     * @throws Exception if the request fails
     */
    @Test
    void aMalformedJsonIsAValidationErrorWithoutDetail() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    /**
     * An id with invalid format is a validation error of that parameter
     *
     * @throws Exception if the request fails
     */
    @Test
    void anIdWithInvalidFormatIsAValidationErrorOfTheParameter() throws Exception {
        mockMvc.perform(get("/eventos/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("eventoId"));
    }

    /**
     * A missing required parameter is a validation error
     *
     * @throws Exception if the request fails
     */
    @Test
    void aMissingRequiredParameterIsAValidationError() throws Exception {
        mockMvc.perform(get("/con-parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("estado"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El parámetro es obligatorio."));
    }

    /**
     * The lack of identity headers is unauthenticated, and any other missing header is a validation error
     *
     * @throws Exception if a request fails
     */
    @Test
    void theLackOfIdentityHeadersIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/con-identidad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mockMvc.perform(get("/con-otra-cabecera"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("X-Otra"));
    }

    /**
     * An unsupported method is not allowed and the answer carries Allow
     *
     * @throws Exception if the request fails
     */
    @Test
    void anUnsupportedMethodIsNotAllowedWithAllow() throws Exception {
        mockMvc.perform(post("/eventos/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    /**
     * An unsupported content type is rejected
     *
     * @throws Exception if the request fails
     */
    @Test
    void anUnsupportedContentTypeIsRejected() throws Exception {
        mockMvc.perform(post("/cuerpo").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
    }

    /**
     * An uncontrolled failure is an internal error without details
     *
     * @throws Exception if the request fails
     */
    @Test
    void anUncontrolledFailureIsAnInternalErrorWithoutDetails() throws Exception {
        mockMvc.perform(get("/falla/inesperada"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje").value("Error interno del servidor."))
                .andExpect(jsonPath("$.errores", hasSize(0)));
    }

    /**
     * An ApiException keeps the detail of the fields
     */
    @Test
    void anApiExceptionKeepsTheDetailOfTheFields() {
        ApiException error = ApiException.validacion("estado", "Seleccione un estado válido.");

        assertEquals(400, error.getCode().getStatus());
        assertEquals(List.of(new ErrorDetail("estado", "Seleccione un estado válido.")), error.getErrors());
    }

    /**
     * Test body with the name of the contract in snake_case
     *
     * @param puntoEncuentroId required meeting point
     */
    record TestBody(@JsonProperty("punto_encuentro_id") @NotNull UUID puntoEncuentroId) {
    }

    /**
     * Test controller whose routes fail in each of the ways that the handler translates
     */
    @RestController
    static class TestController {

        /**
         * Fails with a resource not found
         */
        @GetMapping("/falla/no-encontrado")
        void notFound() {
            throw ApiException.noEncontrado("Evento no encontrado.");
        }

        /**
         * Fails with a conflict
         */
        @GetMapping("/falla/conflicto")
        void conflict() {
            throw ApiException.conflicto("El check-in de este evento no está habilitado.");
        }

        /**
         * Fails with a business rule
         */
        @GetMapping("/falla/regla")
        void businessRule() {
            throw ApiException.reglaNegocio("Este evento no le aplica.");
        }

        /**
         * Fails with an unavailable dependency
         */
        @GetMapping("/falla/dependencia")
        void dependency() {
            throw ApiException.dependenciaNoDisponible();
        }

        /**
         * Fails with a forbidden operation
         */
        @GetMapping("/falla/prohibido")
        void forbidden() {
            throw ApiException.prohibido();
        }

        /**
         * Fails with an operation that is not implemented
         */
        @GetMapping("/falla/no-implementado")
        void notImplemented() {
            throw ApiException.noImplementado("prueba");
        }

        /**
         * Fails with an unexpected error
         */
        @GetMapping("/falla/inesperada")
        void unexpected() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }

        /**
         * Only validates the body
         *
         * @param body body of the request
         */
        @PostMapping("/cuerpo")
        void body(@Valid @RequestBody TestBody body) {
            // only validates
        }

        /**
         * Only validates the format of the id
         *
         * @param eventoId id of the event
         */
        @GetMapping("/eventos/{eventoId}")
        void event(@PathVariable UUID eventoId) {
            // only validates the format
        }

        /**
         * Only validates the required parameter
         *
         * @param estado required parameter
         */
        @GetMapping("/con-parametro")
        void withParameter(@RequestParam String estado) {
            // only validates
        }

        /**
         * Only validates the identity header
         *
         * @param userId identity header
         */
        @GetMapping("/con-identidad")
        void withIdentity(@RequestHeader("X-User-Id") String userId) {
            // only validates
        }

        /**
         * Only validates another required header
         *
         * @param other required header
         */
        @GetMapping("/con-otra-cabecera")
        void withOtherHeader(@RequestHeader("X-Otra") String other) {
            // only validates
        }
    }
}
