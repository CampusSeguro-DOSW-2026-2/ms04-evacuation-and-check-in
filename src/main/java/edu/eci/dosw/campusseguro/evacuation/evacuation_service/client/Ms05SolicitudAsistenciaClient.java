package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Json;

/**
 * Cliente de MS05 para crear la solicitud de asistencia de un check-in Requiero asistencia (RF-MS04-04, paso 3).
 * Llama por el API Gateway con la clave de servicio de MS04, el Correlation ID y la identidad del usuario propagada
 * (Estándares, sección 5). Timeout de cliente de 2000 ms y sin reintentos: los reintentos los hace el reenvío
 * periódico.
 *
 * CONTRATO PROVISIONAL: MS05 todavía no publica el endpoint para las solicitudes que vienen de MS04 (decisión abierta
 * n.º 3 de los Estándares y n.º 4 de los requerimientos de MS04). La ruta se configura con MS05_SOLICITUDES_PATH; el
 * cuerpo y la respuesta son los que propone MS04 y deben acordarse con MS05
 */
@Component
public class Ms05SolicitudAsistenciaClient implements SolicitudAsistenciaClient {

    static final String SERVICE_KEY_HEADER = "X-Service-Key";

    private static final Logger log = LoggerFactory.getLogger(Ms05SolicitudAsistenciaClient.class);

    private final RestClient restClient;
    private final String gatewayUrl;
    private final String serviceKey;
    private final String path;

    /**
     * @param gatewayUrl URL base del API Gateway; si está vacía, la solicitud queda pendiente de envío
     * @param serviceKey clave de servicio de MS04 (GATEWAY_SERVICE_KEY)
     * @param timeoutMs timeout de conexión y de lectura, en milisegundos
     * @param path ruta interna de MS05 para crear la solicitud
     */
    public Ms05SolicitudAsistenciaClient(
            @Value("${gateway.url:}") String gatewayUrl,
            @Value("${gateway.service-key:}") String serviceKey,
            @Value("${gateway.timeout-ms:2000}") long timeoutMs,
            @Value("${clients.ms05.solicitudes-path:/api/v1/internal/solicitudes-asistencia}") String path) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.gatewayUrl = sinBarraFinal(gatewayUrl);
        this.serviceKey = serviceKey;
        this.path = path;
    }

    @Override
    public ResultadoEnvioMs05 enviar(SolicitudAsistencia solicitud, String rolesUsuario, String correlationId) {
        if (gatewayUrl.isEmpty()) {
            log.error("GATEWAY_URL no está configurada: la solicitud de asistencia {} queda pendiente de envío",
                    solicitud.getId());
            return ResultadoEnvioMs05.noDisponible("GATEWAY_URL no configurada");
        }
        try {
            return restClient.post()
                    .uri(gatewayUrl + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (serviceKey != null && !serviceKey.isBlank()) {
                            headers.set(SERVICE_KEY_HEADER, serviceKey);
                        }
                        if (correlationId != null) {
                            headers.set(CorrelationIds.HEADER, correlationId);
                        }
                        headers.set(IdentityHeaders.USER_ID, solicitud.getUsuarioId().toString());
                        if (rolesUsuario != null && !rolesUsuario.isBlank()) {
                            headers.set(IdentityHeaders.USER_ROLE, rolesUsuario);
                        }
                    })
                    // Ya serializado: así viaja con Content-Length y no en bloques (chunked), como la auditoría
                    .body(Json.write(cuerpo(solicitud)).getBytes(StandardCharsets.UTF_8))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 400 || status == 422) {
                            log.warn("MS05 rechazó la solicitud de asistencia {} con HTTP {}", solicitud.getId(),
                                    status);
                            return ResultadoEnvioMs05.rechazada("HTTP " + status);
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            log.warn("MS05 respondió HTTP {} a la solicitud de asistencia {}", status,
                                    solicitud.getId());
                            return ResultadoEnvioMs05.noDisponible("HTTP " + status);
                        }
                        RespuestaMs05 creada = response.bodyTo(RespuestaMs05.class);
                        if (creada == null || creada.id() == null || creada.id().isBlank()) {
                            log.warn("MS05 respondió sin id a la solicitud de asistencia {}", solicitud.getId());
                            return ResultadoEnvioMs05.noDisponible("respuesta sin id");
                        }
                        return ResultadoEnvioMs05.creada(creada.id());
                    });
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("MS05 no respondió a la solicitud de asistencia {}: {}", solicitud.getId(),
                    e.getClass().getSimpleName());
            return ResultadoEnvioMs05.noDisponible(e.getClass().getSimpleName());
        }
    }

    private static String sinBarraFinal(String url) {
        if (url == null) {
            return "";
        }
        String base = url.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * Cuerpo que MS04 envía a MS05 (provisional). Las rutas internas usan camelCase, como el resto de llamadas
     * entre servicios:
     * <ul>
     * <li>{@code solicitudId}: id de la solicitud en MS04, llave de idempotencia (RN-MS04-12)</li>
     * <li>{@code eventoId}: evento del check-in</li>
     * <li>{@code usuarioId}: usuario que requiere asistencia</li>
     * <li>{@code checkinId}: check-in Requiero asistencia que originó la solicitud</li>
     * <li>{@code ubicacion}: {@code tipo} (EDIFICIO, ESPACIO o PUNTO_ENCUENTRO) e {@code id} del elemento en MS02
     * donde se requiere la asistencia (RN-MS04-09)</li>
     * </ul>
     */
    static Map<String, Object> cuerpo(SolicitudAsistencia s) {
        Map<String, Object> ubicacion = new LinkedHashMap<>();
        ubicacion.put("tipo", s.getUbicacionTipo());
        ubicacion.put("id", texto(s.getUbicacionId()));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("solicitudId", texto(s.getId()));
        cuerpo.put("eventoId", texto(s.getEventoId()));
        cuerpo.put("usuarioId", texto(s.getUsuarioId()));
        cuerpo.put("checkinId", texto(s.getCheckinId()));
        cuerpo.put("ubicacion", ubicacion);
        return cuerpo;
    }

    private static String texto(UUID id) {
        return id == null ? null : id.toString();
    }

    /**
     * Respuesta de MS05: solo interesa el id de la solicitud creada. Se lee como texto porque MS05 aún no define su
     * tipo (columna solicitud_ms05_id VARCHAR(64))
     *
     * @param id id de la solicitud en MS05
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaMs05(String id) {
    }
}
