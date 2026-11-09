package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Llamadas síncronas de MS04 a otros servicios a través del API Gateway, con la clave de servicio de MS04, el
 * mismo X-Correlation-ID de la petición en curso, el timeout de cliente de 2000 ms y sin reintentos (Estándares,
 * sección 5). Cualquier fallo (Gateway sin configurar, error de red, timeout, respuesta que no es 2xx o cuerpo que no
 * es JSON) se registra sin datos personales y se informa como 503 DEPENDENCIA_NO_DISPONIBLE, sin reenviar el cuerpo
 * de error del otro servicio
 */
public class GatewayClient {

    static final String SERVICE_KEY_HEADER = "X-Service-Key";

    private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient httpClient;
    private final String gatewayUrl;
    private final String serviceKey;
    private final Duration timeout;

    /**
     * Crea el cliente del Gateway
     *
     * @param httpClient cliente HTTP con el timeout de las llamadas internas
     * @param gatewayUrl URL base del Gateway; si está vacía, toda llamada falla como dependencia no disponible
     * @param serviceKey clave de servicio de MS04 (GATEWAY_SERVICE_KEY)
     * @param timeout timeout de cada llamada
     */
    public GatewayClient(HttpClient httpClient, String gatewayUrl, String serviceKey, Duration timeout) {
        this.httpClient = httpClient;
        this.gatewayUrl = gatewayUrl == null ? "" : gatewayUrl.strip();
        this.serviceKey = serviceKey;
        this.timeout = timeout;
    }

    /**
     * GET a una ruta del Gateway
     *
     * @param ruta ruta que empieza por /api/v1/, sin datos personales porque se escribe en el log
     * @param usuarioId usuario en cuyo nombre se llama, null si es una ruta interna sin usuario
     * @param roles roles del usuario con el formato de X-User-Role, sin modificarlos; se ignora si no hay usuario
     * @return el cuerpo JSON de la respuesta 2xx
     * @throws ApiException 503 DEPENDENCIA_NO_DISPONIBLE si la llamada falla
     */
    public JsonNode get(String ruta, UUID usuarioId, String roles) {
        HttpRequest.Builder solicitud = nuevaSolicitud(ruta, usuarioId, roles);
        return enviar("GET", ruta, solicitud.GET().build());
    }

    /**
     * POST con cuerpo JSON a una ruta interna del Gateway, sin identidad de usuario
     *
     * @param ruta ruta que empieza por /api/v1/, sin datos personales porque se escribe en el log
     * @param cuerpoJson cuerpo de la petición en JSON
     * @return el cuerpo JSON de la respuesta 2xx
     * @throws ApiException 503 DEPENDENCIA_NO_DISPONIBLE si la llamada falla
     */
    public JsonNode post(String ruta, String cuerpoJson) {
        HttpRequest.Builder solicitud = nuevaSolicitud(ruta, null, null);
        return enviar("POST", ruta, solicitud
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoJson, StandardCharsets.UTF_8))
                .build());
    }

    private HttpRequest.Builder nuevaSolicitud(String ruta, UUID usuarioId, String roles) {
        if (gatewayUrl.isEmpty()) {
            log.warn("Gateway no configurado: no se puede llamar a {}", ruta);
            throw ApiException.dependenciaNoDisponible();
        }
        HttpRequest.Builder solicitud;
        try {
            solicitud = HttpRequest.newBuilder(URI.create(gatewayUrl + ruta))
                    .timeout(timeout)
                    .header("Accept", "application/json");
        } catch (IllegalArgumentException e) {
            log.warn("La URL del Gateway no es válida: no se puede llamar a {}", ruta);
            throw ApiException.dependenciaNoDisponible();
        }
        if (serviceKey != null && !serviceKey.isBlank()) {
            solicitud.header(SERVICE_KEY_HEADER, serviceKey);
        }
        String correlationId = MDC.get(CorrelationIds.MDC_KEY);
        if (correlationId != null) {
            solicitud.header(CorrelationIds.HEADER, correlationId);
        }
        // Si actúa en nombre de un usuario, se propaga su identidad sin modificarla (Estándares, sección 5)
        if (usuarioId != null && roles != null) {
            solicitud.header(IdentityHeaders.USER_ID, usuarioId.toString());
            solicitud.header(IdentityHeaders.USER_ROLE, roles);
        }
        return solicitud;
    }

    private JsonNode enviar(String metodo, String ruta, HttpRequest solicitud) {
        try {
            HttpResponse<String> respuesta = httpClient.send(solicitud,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respuesta.statusCode() < 200 || respuesta.statusCode() > 299) {
                log.warn("{} {} respondió HTTP {}", metodo, ruta, respuesta.statusCode());
                throw ApiException.dependenciaNoDisponible();
            }
            return MAPPER.readTree(respuesta.body());
        } catch (IOException e) {
            log.warn("{} {} no se pudo completar: {}", metodo, ruta, e.toString());
            throw ApiException.dependenciaNoDisponible();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("{} {} se interrumpió antes de completarse", metodo, ruta);
            throw ApiException.dependenciaNoDisponible();
        } catch (JacksonException e) {
            log.warn("{} {} respondió un cuerpo que no es JSON válido", metodo, ruta);
            throw ApiException.dependenciaNoDisponible();
        }
    }
}
