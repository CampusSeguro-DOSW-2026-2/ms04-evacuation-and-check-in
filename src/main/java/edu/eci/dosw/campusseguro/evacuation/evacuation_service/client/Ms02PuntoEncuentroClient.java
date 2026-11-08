package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
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

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;

/**
 * Cliente de MS02 para validar el punto de encuentro de un check-in Estoy a salvo (RF-MS04-02, paso 5). Llama a
 * {@code GET /api/v1/safe-zones/{id}} a través del API Gateway con la clave de servicio de MS04, el Correlation ID y la
 * identidad del usuario propagada (Estándares, sección 5). Timeout de cliente de 2000 ms y sin reintentos
 */
@Component
public class Ms02PuntoEncuentroClient implements PuntoEncuentroClient {

    static final String SERVICE_KEY_HEADER = "X-Service-Key";
    static final String ESTADO_ACTIVO = "ACTIVO";

    private static final Logger log = LoggerFactory.getLogger(Ms02PuntoEncuentroClient.class);

    private final RestClient restClient;
    private final String gatewayUrl;
    private final String serviceKey;
    private final String path;

    /**
     * @param gatewayUrl URL base del API Gateway; si está vacía, MS04 no puede validar el punto (503)
     * @param serviceKey clave de servicio de MS04 (GATEWAY_SERVICE_KEY)
     * @param timeoutMs timeout de conexión y de lectura, en milisegundos
     * @param path ruta de la zona segura en MS02, con {@code {id}}
     */
    public Ms02PuntoEncuentroClient(
            @Value("${gateway.url:}") String gatewayUrl,
            @Value("${gateway.service-key:}") String serviceKey,
            @Value("${gateway.timeout-ms:2000}") long timeoutMs,
            @Value("${clients.ms02.safe-zones-path:/api/v1/safe-zones/{id}}") String path) {
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
    public Optional<PuntoEncuentro> buscar(UUID puntoEncuentroId, RequesterIdentity requester, String correlationId) {
        if (gatewayUrl.isEmpty()) {
            log.error("GATEWAY_URL no está configurada: no se puede validar el punto de encuentro con MS02");
            throw ApiException.dependenciaNoDisponible();
        }
        try {
            return restClient.get()
                    .uri(gatewayUrl + path, puntoEncuentroId)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (serviceKey != null && !serviceKey.isBlank()) {
                            headers.set(SERVICE_KEY_HEADER, serviceKey);
                        }
                        if (correlationId != null) {
                            headers.set(CorrelationIds.HEADER, correlationId);
                        }
                        headers.set(IdentityHeaders.USER_ID, requester.userId().toString());
                        headers.set(IdentityHeaders.USER_ROLE, requester.rolesComoCabecera());
                    })
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404) {
                            return Optional.<PuntoEncuentro>empty();
                        }
                        if (status != 200) {
                            log.warn("MS02 respondió HTTP {} al consultar el punto de encuentro {}", status,
                                    puntoEncuentroId);
                            throw ApiException.dependenciaNoDisponible();
                        }
                        ZonaSeguraMs02 zona = response.bodyTo(ZonaSeguraMs02.class);
                        if (zona == null || zona.id() == null) {
                            log.warn("MS02 respondió sin datos al consultar el punto de encuentro {}",
                                    puntoEncuentroId);
                            throw ApiException.dependenciaNoDisponible();
                        }
                        return Optional.of(new PuntoEncuentro(zona.id(), zona.codigo(), zona.nombre(),
                                ESTADO_ACTIVO.equals(zona.estado())));
                    });
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("MS02 no respondió al consultar el punto de encuentro {}: {}", puntoEncuentroId,
                    e.getClass().getSimpleName());
            throw ApiException.dependenciaNoDisponible();
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
     * Respuesta de MS02 ({@code ZonaSeguraResponse}). Ignora los campos que MS04 no usa (ubicación, descripción,
     * capacidad), así un cambio en ellos no rompe la integración
     *
     * @param id id de la zona segura
     * @param codigo código legible, p. ej. MP-H-01
     * @param nombre nombre de la zona
     * @param estado ACTIVO o INACTIVO
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ZonaSeguraMs02(UUID id, String codigo, String nombre, String estado) {
    }
}
