package edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cuerpo de toda respuesta 4xx y 5xx de MS04 (RNF-MS04-11, Estándares de Integración, sección 7). Tiene
 * exactamente estos cinco campos, todos obligatorios y nunca null
 *
 * @param status igual al código HTTP de la respuesta
 * @param codigo código del catálogo, en MAYUSCULAS_CON_GUION_BAJO
 * @param mensaje texto seguro en español
 * @param errores detalle por campo; lista vacía si no hay errores por campo
 * @param correlationId igual al X-Correlation-ID de la petición
 */
public record ErrorResponse(
        int status,
        String codigo,
        String mensaje,
        List<ErrorDetail> errores,
        @JsonProperty("correlation_id") String correlationId) {

    /**
     * Garantiza que ni la lista de errores ni el correlation id sean null
     */
    public ErrorResponse {
        errores = errores == null ? List.of() : List.copyOf(errores);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
    }

    /**
     * Error de un campo
     *
     * @param campo nombre del campo tal como aparece en el contrato
     * @param mensaje motivo del error
     */
    public record ErrorDetail(String campo, String mensaje) {
    }

    /**
     * Crea una respuesta sin errores por campo
     *
     * @param status código HTTP
     * @param codigo código del catálogo
     * @param mensaje texto seguro en español
     * @param correlationId correlation id de la petición
     * @return la respuesta de error
     */
    public static ErrorResponse of(int status, String codigo, String mensaje, String correlationId) {
        return new ErrorResponse(status, codigo, mensaje, List.of(), correlationId);
    }
}
