package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Json;

/**
 * Envía los eventos de auditoría a MS08 con {@code POST /api/v1/audit/events} a través del API Gateway, como ruta
 * interna con la clave de servicio de MS04 (Estándares, secciones 5 y 12). El envío es asíncrono, sin esperar el
 * resultado y sin reintentos; MS08 responde 202. Si falla, solo se registra en el log
 */
public class AuditEventPublisherImpl implements AuditEventPublisher {

    static final String MODULE = "MS04";
    static final String EVENTS_PATH = "/api/v1/audit/events";
    static final String SERVICE_KEY_HEADER = "X-Service-Key";

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisherImpl.class);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String serviceKey;
    private final Duration requestTimeout;

    /**
     * @param httpClient cliente HTTP con el timeout de las llamadas internas (2000 ms)
     * @param gatewayUrl URL base del API Gateway; si está vacía, la auditoría solo se registra en el log
     * @param serviceKey clave de servicio de MS04 (GATEWAY_SERVICE_KEY)
     * @param requestTimeout timeout de cada envío
     */
    public AuditEventPublisherImpl(HttpClient httpClient, String gatewayUrl, String serviceKey,
            Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
        this.serviceKey = serviceKey;
        this.endpoint = buildEndpoint(gatewayUrl);
    }

    @Override
    public void publish(AuditEvent event) {
        try {
            if (endpoint == null) {
                log.info("Auditoría (Gateway no configurado): accion={} resultado={} usuario={} recurso={} detalle={}",
                        event.accion(), event.resultado(), event.usuarioId(), event.recursoAfectado(),
                        event.detalle());
                return;
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(event), StandardCharsets.UTF_8));
            addHeaders(request, event);
            httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                    .whenComplete((response, error) -> reportOutcome(event, response, error));
        } catch (RuntimeException e) {
            log.error("No se pudo preparar el evento de auditoría {}/{}: {}", event.accion(), event.resultado(),
                    e.toString());
        }
    }

    private void addHeaders(HttpRequest.Builder request, AuditEvent event) {
        if (serviceKey != null && !serviceKey.isBlank()) {
            request.header(SERVICE_KEY_HEADER, serviceKey);
        }
        if (event.correlationId() != null) {
            request.header(CorrelationIds.HEADER, event.correlationId());
        }
        // Si la acción la hizo un usuario, se propaga su identidad sin modificarla (Estándares, sección 5)
        if (event.usuarioId() != null && event.usuarioRoles() != null) {
            request.header(IdentityHeaders.USER_ID, event.usuarioId().toString());
            request.header(IdentityHeaders.USER_ROLE, event.usuarioRoles());
        }
    }

    private void reportOutcome(AuditEvent event, HttpResponse<Void> response, Throwable error) {
        if (event.correlationId() != null) {
            MDC.put(CorrelationIds.MDC_KEY, event.correlationId());
        }
        try {
            if (error != null) {
                log.error("MS08 no recibió el evento de auditoría {}/{}: {}", event.accion(), event.resultado(),
                        error.toString());
            } else if (response.statusCode() >= 300) {
                log.error("MS08 rechazó el evento de auditoría {}/{} con HTTP {}", event.accion(),
                        event.resultado(), response.statusCode());
            }
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    /**
     * Cuerpo del evento con los campos y nombres de los Estándares (sección 12)
     *
     * @param event evento de auditoría
     * @return el JSON que recibe MS08
     */
    static String toJson(AuditEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("usuario_id", event.usuarioId() == null ? null : event.usuarioId().toString());
        body.put("modulo", MODULE);
        body.put("accion", event.accion().name());
        body.put("recurso_afectado", event.recursoAfectado());
        body.put("resultado", event.resultado().name());
        body.put("fecha_hora", DateTimeFormatter.ISO_INSTANT.format(event.fecha()));
        body.put("correlation_id", event.correlationId());
        body.put("detalle", event.detalle());
        return Json.write(body);
    }

    private static URI buildEndpoint(String gatewayUrl) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            return null;
        }
        String base = gatewayUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + EVENTS_PATH);
    }
}
