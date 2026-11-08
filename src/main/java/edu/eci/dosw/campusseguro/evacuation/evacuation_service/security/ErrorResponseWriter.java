package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe el {@code ErrorResponse} cuando el error ocurre en la capa de seguridad, antes de llegar a Spring MVC
 * (401 y 403). Mantiene el orden y los nombres de los cinco campos de los Estándares
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    /**
     * Escribe la respuesta de error
     *
     * @param request petición en curso, de donde sale el correlation id
     * @param response respuesta en la que se escribe
     * @param code código del catálogo
     * @param message texto seguro en español
     * @throws IOException si falla la escritura
     */
    public static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code,
            String message) throws IOException {
        ErrorResponse error = ErrorResponse.of(code.getStatus(), code.getCode(), message,
                CorrelationIds.current(request));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", error.status());
        body.put("codigo", error.codigo());
        body.put("mensaje", error.mensaje());
        List<Map<String, Object>> errores = new ArrayList<>();
        for (ErrorResponse.ErrorDetail detail : error.errores()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("campo", detail.campo());
            item.put("mensaje", detail.mensaje());
            errores.add(item);
        }
        body.put("errores", errores);
        body.put("correlation_id", error.correlationId());

        response.setStatus(code.getStatus());
        response.setHeader(CorrelationIds.HEADER, error.correlationId());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(Json.write(body));
    }
}
