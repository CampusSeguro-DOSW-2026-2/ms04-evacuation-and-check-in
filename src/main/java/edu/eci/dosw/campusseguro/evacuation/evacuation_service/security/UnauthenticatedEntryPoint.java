package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Responde 401 NO_AUTENTICADO con el {@code ErrorResponse} cuando faltan o no son válidas las cabeceras de
 * identidad o de servicio. El mensaje es el motivo que dejó el filtro de autenticación
 */
public class UnauthenticatedEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        String message = IdentityHeaders.MISSING_MESSAGE;
        Object reason = request.getAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE);
        if (reason instanceof String text) {
            message = text;
        }
        ErrorResponseWriter.write(request, response, ErrorCode.UNAUTHENTICATED, message);
    }
}
