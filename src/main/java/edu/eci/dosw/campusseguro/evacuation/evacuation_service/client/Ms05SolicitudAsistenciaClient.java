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
 * Usa el endpoint que MS05 ya publica para registrar incidentes, {@code POST /api/v1/incidentes/eventos/{eventoId}}
 * (RF-BEI-04): la solicitud de asistencia es un incidente que MS05 guarda a nombre del usuario del check-in, con la
 * ubicación del check-in y la prioridad configurada. Por eso MS05 autoriza con la identidad del usuario
 * (X-User-Id, X-User-Role y X-User-Permission, permiso ms05:incident:report) y no con X-Service-Name. La ruta se
 * configura con MS05_SOLICITUDES_PATH ({eventoId} se reemplaza por el evento de la solicitud) y la prioridad con
 * MS05_PRIORIDAD_ID, el id del catálogo de prioridades de MS05 (1 = EMERGENCIA). El id de la solicitud viaja en
 * Idempotency-Key: si MS04 repite el envío (timeout del cliente o reenvío periódico), MS05 devuelve el incidente ya
 * creado en vez de duplicarlo (RN-MS04-12). Un 409 de MS05 (llave usada por otra solicitud) deja la solicitud
 * RECHAZADA porque repetirla nunca va a funcionar
 */
@Component
public class Ms05SolicitudAsistenciaClient implements SolicitudAsistenciaClient {

    static final String SERVICE_KEY_HEADER = "X-Service-Key";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final Logger log = LoggerFactory.getLogger(Ms05SolicitudAsistenciaClient.class);

    private final RestClient restClient;
    private final String gatewayUrl;
    private final String serviceKey;
    private final String path;
    private final long prioridadId;

    /**
     * @param gatewayUrl URL base del API Gateway; si está vacía, la solicitud queda pendiente de envío
     * @param serviceKey clave de servicio de MS04 (GATEWAY_SERVICE_KEY)
     * @param timeoutMs timeout de conexión y de lectura, en milisegundos
     * @param path ruta de MS05 para registrar el incidente; {eventoId} se reemplaza por el evento de la solicitud
     * @param prioridadId id de la prioridad de MS05 con la que se registra la solicitud (1 = EMERGENCIA)
     */
    public Ms05SolicitudAsistenciaClient(
            @Value("${gateway.url:}") String gatewayUrl,
            @Value("${gateway.service-key:}") String serviceKey,
            @Value("${gateway.timeout-ms:2000}") long timeoutMs,
            @Value("${clients.ms05.solicitudes-path:/api/v1/incidentes/eventos/{eventoId}}") String path,
            @Value("${clients.ms05.prioridad-id:1}") long prioridadId) {
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
        this.prioridadId = prioridadId;
    }

    @Override
    public ResultadoEnvioMs05 enviar(SolicitudAsistencia solicitud, String rolesUsuario, String permisosUsuario,
            String correlationId) {
        if (gatewayUrl.isEmpty()) {
            log.error("GATEWAY_URL no está configurada: la solicitud de asistencia {} queda pendiente de envío",
                    solicitud.getId());
            return ResultadoEnvioMs05.noDisponible("GATEWAY_URL no configurada");
        }
        try {
            return restClient.post()
                    .uri(gatewayUrl + path.replace("{eventoId}", solicitud.getEventoId().toString()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (serviceKey != null && !serviceKey.isBlank()) {
                            headers.set(SERVICE_KEY_HEADER, serviceKey);
                        }
                        if (correlationId != null) {
                            headers.set(CorrelationIds.HEADER, correlationId);
                        }
                        // La llave es el id de la solicitud: es el mismo en el envío del check-in y en cada reenvío
                        // periódico, así MS05 no crea un incidente nuevo al repetir el envío
                        if (solicitud.getId() != null) {
                            headers.set(IDEMPOTENCY_KEY_HEADER, solicitud.getId().toString());
                        }
                        // MS05 registra el incidente a nombre de X-User-Id y lo autoriza con X-User-Permission. El
                        // Gateway exige las tres cabeceras juntas o ninguna; en el reenvío periódico llegan las que
                        // se guardaron con la solicitud. Sin ellas MS05 responde 401 y la solicitud sigue pendiente
                        if (noVacio(rolesUsuario) && noVacio(permisosUsuario)) {
                            headers.set(IdentityHeaders.USER_ID, solicitud.getUsuarioId().toString());
                            headers.set(IdentityHeaders.USER_ROLE, rolesUsuario);
                            headers.set(IdentityHeaders.USER_PERMISSION, permisosUsuario);
                        }
                    })
                    // Ya serializado: así viaja con Content-Length y no en bloques (chunked), como la auditoría
                    .body(Json.write(cuerpo(solicitud, prioridadId)).getBytes(StandardCharsets.UTF_8))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 400 || status == 403 || status == 409 || status == 422) {
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
                        if (creada == null || creada.idIncidente() == null || creada.idIncidente().isBlank()) {
                            log.warn("MS05 respondió sin id a la solicitud de asistencia {}", solicitud.getId());
                            return ResultadoEnvioMs05.noDisponible("respuesta sin id");
                        }
                        return ResultadoEnvioMs05.creada(creada.idIncidente());
                    });
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("MS05 no respondió a la solicitud de asistencia {}: {}", solicitud.getId(),
                    e.getClass().getSimpleName());
            return ResultadoEnvioMs05.noDisponible(e.getClass().getSimpleName());
        }
    }

    private static boolean noVacio(String valor) {
        return valor != null && !valor.isBlank();
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
     * Cuerpo del incidente que MS04 envía a MS05 (CrearIncidenteRequest de MS05):
     * <ul>
     * <li>{@code prioridadId}: prioridad configurada (MS05_PRIORIDAD_ID)</li>
     * <li>{@code ubicacionId}: id del elemento en MS02 donde se requiere la asistencia (RN-MS04-09)</li>
     * <li>{@code tipoUbicacion}: EDIFICIO o PUNTO_ENCUENTRO; MS05 no tiene ESPACIO y lo rechaza con 400, así que la
     * solicitud queda RECHAZADA</li>
     * <li>{@code descripcion}: texto con el check-in de origen, para la trazabilidad entre MS04 y MS05</li>
     * </ul>
     * El nombre del afectado no se envía: MS05 usa el nombre del usuario que reporta, que consulta a MS01
     */
    static Map<String, Object> cuerpo(SolicitudAsistencia s, long prioridadId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("prioridadId", prioridadId);
        cuerpo.put("ubicacionId", texto(s.getUbicacionId()));
        cuerpo.put("tipoUbicacion", s.getUbicacionTipo() == null ? null : s.getUbicacionTipo().name());
        cuerpo.put("descripcion", "Solicitud de asistencia generada por el check-in " + texto(s.getCheckinId()));
        return cuerpo;
    }

    private static String texto(UUID id) {
        return id == null ? null : id.toString();
    }

    /**
     * Respuesta de MS05 (IncidenteResponse): solo interesa el id del incidente creado. Se lee como texto (columna
     * solicitud_ms05_id VARCHAR(64))
     *
     * @param idIncidente id del incidente en MS05
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaMs05(String idIncidente) {
    }
}
