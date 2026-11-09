package edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception;

import java.util.List;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;

/**
 * Error de negocio o de integración que MS04 responde con el {@code ErrorResponse} de los Estándares. Los services
 * lanzan esta excepción con las fábricas de abajo y {@link GlobalExceptionHandler} la convierte en la respuesta,
 * con el código HTTP y el código del catálogo que corresponden
 */
public class ApiException extends RuntimeException {

    private final transient ErrorCode code;
    private final transient List<ErrorDetail> errors;

    /**
     * @param code código del catálogo, que fija el estado HTTP
     * @param message texto seguro en español para el cliente
     * @param errors detalle por campo, vacío si no aplica
     */
    public ApiException(ErrorCode code, String message, List<ErrorDetail> errors) {
        super(message);
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<ErrorDetail> getErrors() {
        return errors;
    }

    /** 400 VALIDACION con el detalle de los campos inválidos */
    public static ApiException validacion(List<ErrorDetail> errors) {
        return new ApiException(ErrorCode.VALIDATION, ErrorCode.VALIDATION.getMessage(), errors);
    }

    /** 400 VALIDACION de un solo campo */
    public static ApiException validacion(String campo, String mensaje) {
        return validacion(List.of(new ErrorDetail(campo, mensaje)));
    }

    /** 401 NO_AUTENTICADO */
    public static ApiException noAutenticado(String mensaje) {
        return new ApiException(ErrorCode.UNAUTHENTICATED, mensaje, List.of());
    }

    /** 403 PROHIBIDO */
    public static ApiException prohibido() {
        return new ApiException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage(), List.of());
    }

    /** 404 NO_ENCONTRADO, p. ej. "Evento no encontrado." */
    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(ErrorCode.NOT_FOUND, mensaje, List.of());
    }

    /** 409 CONFLICTO, p. ej. "El check-in de este evento no está habilitado." */
    public static ApiException conflicto(String mensaje) {
        return new ApiException(ErrorCode.CONFLICT, mensaje, List.of());
    }

    /** 422 REGLA_NEGOCIO, p. ej. "Este evento no le aplica." */
    public static ApiException reglaNegocio(String mensaje) {
        return new ApiException(ErrorCode.BUSINESS_RULE, mensaje, List.of());
    }

    /** 503 DEPENDENCIA_NO_DISPONIBLE: MS01, MS02 o MS05 no responden; nunca se reenvía su cuerpo de error */
    public static ApiException dependenciaNoDisponible() {
        return new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE.getMessage(),
                List.of());
    }

    /** 503 DEPENDENCIA_NO_DISPONIBLE con el texto propio de la operación; nunca se reenvía el cuerpo de error */
    public static ApiException dependenciaNoDisponible(String mensaje) {
        return new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, mensaje, List.of());
    }

    /** 501 NO_IMPLEMENTADO: operación del contrato que todavía no tiene lógica */
    public static ApiException noImplementado(String operacion) {
        return new ApiException(ErrorCode.NOT_IMPLEMENTED, "Operación pendiente de implementar: " + operacion + ".",
                List.of());
    }
}
