package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde 403 PROHIBIDO con el {@code ErrorResponse} cuando el solicitante está autenticado pero no tiene
 * permiso, por ejemplo un servicio que no está entre los autorizados para una ruta interna
 */
public class ForbiddenAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ErrorResponseWriter.write(request, response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
    }
}
