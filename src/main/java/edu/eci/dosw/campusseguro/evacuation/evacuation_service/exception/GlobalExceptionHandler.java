package edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse.ErrorDetail;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.exc.PropertyBindingException;

/**
 * Manejador global de errores: todo error de MS04, incluidos los del framework (404, 405, 415, cabecera faltante),
 * sale como {@code ErrorResponse} con el catálogo de los Estándares y sin trazas internas (RNF-MS04-11)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public static final String REQUIRED_PARAMETER_MESSAGE = "El parámetro es obligatorio.";
    public static final String INVALID_PARAMETER_MESSAGE = ValidationErrors.INVALID_VALUE_MESSAGE;
    public static final String INVALID_CHECKIN_STATE_MESSAGE = "Estado no válido.";
    /** Filtro de estado de la consulta de eventos (F-07) */
    public static final String INVALID_EVENT_STATE_MESSAGE = "Estado de evento no válido.";

    /** Mensajes de los formularios (RG-03 y F-02) para los parámetros de ruta y de consulta con formato inválido */
    private static final Map<String, String> PARAMETER_MESSAGES = Map.of(
            "eventoId", "Evento no válido.",
            "punto_encuentro_id", "Punto de encuentro no válido.",
            "page", "Página no válida.",
            "size", "Tamaño de página no válido (1 a 100).");

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getCode(), ex.getMessage(), ex.getErrors(), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Object target = ex.getBindingResult().getTarget();
        Class<?> type = target == null ? Object.class : target.getClass();
        List<ErrorDetail> errors = ValidationErrors.fromFieldErrors(type, ex.getBindingResult().getFieldErrors());
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request, HttpHeaders.EMPTY);
    }

    /**
     * Cuerpo ilegible: JSON mal formado, un campo que el contrato no define (RG-04) o un valor de otro tipo (p. ej.
     * un estado fuera del catálogo)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        List<ErrorDetail> errors = List.of();
        Throwable cause = ex.getCause();
        if (cause instanceof PropertyBindingException unknown && unknown.getPropertyName() != null) {
            errors = List.of(new ErrorDetail(unknown.getPropertyName(), ValidationErrors.UNKNOWN_FIELD_MESSAGE));
        } else if (cause instanceof tools.jackson.core.JacksonException jackson && jackson.getPath() != null
                && !jackson.getPath().isEmpty()) {
            String field = jackson.getPath().get(jackson.getPath().size() - 1).getPropertyName();
            if (field != null) {
                errors = List.of(new ErrorDetail(field, ValidationErrors.INVALID_VALUE_MESSAGE));
            }
        }
        log.warn("Cuerpo de la petición ilegible: {}", ex.getClass().getSimpleName());
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE, errors, request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
            HttpServletRequest request) {
        String header = ex.getHeaderName();
        if (IdentityHeaders.USER_ID.equalsIgnoreCase(header)
                || IdentityHeaders.USER_ROLE.equalsIgnoreCase(header)) {
            return build(ErrorCode.UNAUTHENTICATED, IdentityHeaders.MISSING_MESSAGE, List.of(), request,
                    HttpHeaders.EMPTY);
        }
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE,
                List.of(new ErrorDetail(header, REQUIRED_PARAMETER_MESSAGE)), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE,
                List.of(new ErrorDetail(ex.getParameterName(), REQUIRED_PARAMETER_MESSAGE)), request,
                HttpHeaders.EMPTY);
    }

    /**
     * Un parámetro de ruta o de consulta con formato inválido (p. ej. un eventoId que no es UUID). Usa el mensaje
     * del formulario cuando el parámetro lo define y, si no, el mensaje genérico
     *
     * @param ex error de conversión del parámetro
     * @param request petición en curso
     * @return 400 VALIDACION con el parámetro y su mensaje
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String mensaje = PARAMETER_MESSAGES.getOrDefault(ex.getName(), INVALID_PARAMETER_MESSAGE);
        if (ex.getRequiredType() == EstadoCheckin.class) {
            mensaje = INVALID_CHECKIN_STATE_MESSAGE;
        } else if (ex.getRequiredType() == EstadoEvento.class) {
            mensaje = INVALID_EVENT_STATE_MESSAGE;
        }
        return build(ErrorCode.VALIDATION, ValidationErrors.INVALID_DATA_MESSAGE,
                List.of(new ErrorDetail(ex.getName(), mensaje)), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), List.of(), request,
                ex.getHeaders());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), List.of(),
                request, ex.getHeaders());
    }

    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleRouteNotFound(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage(), List.of(), request, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            return fromFrameworkStatus(springError, request);
        }
        log.error("Error no controlado", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), List.of(), request,
                HttpHeaders.EMPTY);
    }

    /** Cualquier otro error del framework conserva su estado HTTP pero sale con el cuerpo estándar */
    private ResponseEntity<ErrorResponse> fromFrameworkStatus(org.springframework.web.ErrorResponse springError,
            HttpServletRequest request) {
        int value = springError.getStatusCode().value();
        if (value == HttpStatus.NOT_ACCEPTABLE.value()) {
            value = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value();
        }
        if (value >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Error del framework con estado {}", value, (Exception) springError);
            value = HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        ErrorCode code = ErrorCode.forStatus(value);
        return respond(HttpStatus.valueOf(code.getStatus() == value ? value : code.getStatus()), code,
                code.getMessage(), List.of(), request, springError.getHeaders());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, List<ErrorDetail> errors,
            HttpServletRequest request, HttpHeaders headers) {
        return respond(HttpStatus.valueOf(code.getStatus()), code, message, errors, request, headers);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorCode code, String message,
            List<ErrorDetail> errors, HttpServletRequest request, HttpHeaders headers) {
        ErrorResponse body = new ErrorResponse(status.value(), code.getCode(), message, errors,
                CorrelationIds.current(request));
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
