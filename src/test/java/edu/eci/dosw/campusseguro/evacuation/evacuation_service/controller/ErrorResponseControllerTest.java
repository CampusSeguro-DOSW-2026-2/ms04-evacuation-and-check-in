package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import jakarta.servlet.RequestDispatcher;

/**
 * Last resort for the errors that the container sends to /error without going through the global handler: they
 * also leave as the standard ErrorResponse with the correlation id of the request
 */
class ErrorResponseControllerTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private final ErrorResponseController controller = new ErrorResponseController();

    /**
     * Request forwarded to /error by the container
     *
     * @param status status set by the container, or null
     * @return the request with the correlation id
     */
    private static MockHttpServletRequest forwarded(Integer status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.addHeader(CorrelationIds.HEADER, CORRELATION);
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        return request;
    }

    /**
     * Without status of the container the error is a resource not found
     */
    @Test
    void withoutStatusTheErrorIsNotFound() {
        ResponseEntity<ErrorResponse> answer = controller.handleError(forwarded(null));

        assertEquals(404, answer.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, answer.getHeaders().getContentType());
        assertEquals("NO_ENCONTRADO", answer.getBody().codigo());
        assertEquals(CORRELATION, answer.getBody().correlationId());
    }

    /**
     * Each status of the container is answered with its code of the catalog, and an unknown status as an internal
     * error
     *
     * @param status status set by the container
     * @param expectedStatus status of the answer
     * @param code expected code
     */
    @ParameterizedTest
    @CsvSource({
            "405, 405, METODO_NO_PERMITIDO",
            "500, 500, ERROR_INTERNO",
            "418, 418, VALIDACION",
            "999, 500, ERROR_INTERNO" })
    void eachStatusIsAnsweredWithItsCode(int status, int expectedStatus, String code) {
        ResponseEntity<ErrorResponse> answer = controller.handleError(forwarded(status));

        assertEquals(expectedStatus, answer.getStatusCode().value());
        assertEquals(expectedStatus, answer.getBody().status());
        assertEquals(code, answer.getBody().codigo());
    }
}
