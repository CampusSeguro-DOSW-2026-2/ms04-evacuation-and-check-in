package edu.eci.dosw.campusseguro.evacuation.evacuation_service.util;

import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Normaliza el X-Correlation-ID de la petición (RNF-MS04-12, Estándares de Integración, sección 8): se conserva
 * si es un UUID v4 válido y si falta o no es válido se genera uno nuevo. La correlación nunca causa el rechazo de
 * una petición
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";
    public static final String ATTRIBUTE = CorrelationIds.class.getName() + ".VALUE";
    public static final String MDC_KEY = "correlation_id";

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private CorrelationIds() {
    }

    /**
     * Devuelve el correlation id que se usa en logs, auditoría y respuestas
     *
     * @param received valor recibido en la cabecera X-Correlation-ID, puede ser null
     * @return el valor recibido sin espacios laterales si es un UUID v4 válido, o un UUID v4 aleatorio nuevo
     */
    public static String resolve(String received) {
        if (received != null) {
            String trimmed = received.trim();
            if (UUID_V4.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Devuelve el correlation id ya resuelto para la petición en curso; si ningún filtro lo resolvió antes lo
     * resuelve a partir de la cabecera recibida
     *
     * @param request petición en curso
     * @return el correlation id de la petición
     */
    public static String current(HttpServletRequest request) {
        Object resolved = request.getAttribute(ATTRIBUTE);
        if (resolved instanceof String value) {
            return value;
        }
        return resolve(request.getHeader(HEADER));
    }
}
