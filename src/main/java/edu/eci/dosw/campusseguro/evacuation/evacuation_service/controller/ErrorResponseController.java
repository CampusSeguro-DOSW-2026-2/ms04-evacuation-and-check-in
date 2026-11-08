package edu.eci.dosw.campusseguro.evacuation.evacuation_service.controller;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Último recurso para los errores que Spring envía a {@code /error} sin pasar por el manejador global (por ejemplo
 * una ruta inexistente que atiende el contenedor): también salen como {@code ErrorResponse}
 */
@RestController
public class ErrorResponseController implements ErrorController {

    /**
     * Responde el error del contenedor con el cuerpo estándar
     *
     * @param request petición que originó el error
     * @return el {@code ErrorResponse} con el estado HTTP original (404 si no se conoce)
     */
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        int value = HttpStatus.NOT_FOUND.value();
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (attribute instanceof Integer code) {
            value = code;
        }
        HttpStatus status = HttpStatus.resolve(value);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorCode code = ErrorCode.forStatus(status.value());
        ErrorResponse body = ErrorResponse.of(status.value(), code.getCode(), code.getMessage(),
                CorrelationIds.current(request));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
