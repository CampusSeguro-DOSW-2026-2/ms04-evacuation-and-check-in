package edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Catálogo de errores de MS04: los códigos de los Estándares de Integración (sección 7.2) más
 * DEPENDENCIA_NO_DISPONIBLE, que MS04 declara en su OpenAPI (RNF-MS04-11)
 */
public enum ErrorCode {

    VALIDATION(400, "VALIDACION", "Datos inválidos."),
    UNAUTHENTICATED(401, "NO_AUTENTICADO", "No autenticado."),
    FORBIDDEN(403, "PROHIBIDO", "No cuenta con los permisos suficientes para realizar esta operación."),
    NOT_FOUND(404, "NO_ENCONTRADO", "Recurso no encontrado."),
    METHOD_NOT_ALLOWED(405, "METODO_NO_PERMITIDO", "Método HTTP no permitido."),
    CONFLICT(409, "CONFLICTO", "Conflicto con el estado actual del recurso."),
    UNSUPPORTED_MEDIA_TYPE(415, "TIPO_NO_SOPORTADO", "Tipo de contenido no soportado."),
    BUSINESS_RULE(422, "REGLA_NEGOCIO", "La operación no cumple una regla de negocio."),
    INTERNAL_ERROR(500, "ERROR_INTERNO", "Error interno del servidor."),
    /**
     * Temporal: lo usan las operaciones del contrato que todavía no tienen su lógica. No está en el catálogo de
     * los Estándares y debe desaparecer cuando todas estén implementadas
     */
    NOT_IMPLEMENTED(501, "NO_IMPLEMENTADO", "Operación pendiente de implementar."),
    /** Código propio de MS04: MS01, MS02 o MS05 no responden (RNF-MS04-11) */
    DEPENDENCY_UNAVAILABLE(503, "DEPENDENCIA_NO_DISPONIBLE",
            "El servicio no está disponible en este momento. Intente de nuevo.");

    private static final Map<Integer, ErrorCode> BY_STATUS = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(ErrorCode::getStatus, Function.identity(), (first, other) -> first));

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Código general que corresponde a un estado HTTP
     *
     * @param status estado HTTP
     * @return el código del catálogo; ERROR_INTERNO para los 5xx desconocidos y VALIDACION para los 4xx
     *         desconocidos
     */
    public static ErrorCode forStatus(int status) {
        ErrorCode general = BY_STATUS.get(status);
        if (general != null) {
            return general;
        }
        return status >= 500 ? INTERNAL_ERROR : VALIDATION;
    }
}
