package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import tools.jackson.databind.json.JsonMapper;

/**
 * Obtiene la población objetivo de MS01 con {@code GET /api/v1/users/by-role} a través del API Gateway, con la
 * clave de servicio de MS04, el Correlation ID y la identidad del usuario propagada (Estándares, sección 5;
 * RNF-MS04-10). Recorre todas las páginas de 100 usuarios
 *
 * El contrato de la ruta está pendiente con MS01 (decisión abierta n.º 2 de los requerimientos de MS04). Se
 * asume la paginación de los Estándares ({@code contenido}, {@code total_paginas}) con el {@code id} de cada
 * usuario, como el {@code UserPage} de MS01
 *
 * Nunca lanza excepción: si MS01 no responde, responde con error o el Gateway no está configurado, devuelve vacío
 * y deja el fallo en el log, para que quien llama entregue el resto de los datos (RF-MS04-10, A2)
 */
public class Ms01PoblacionObjetivoClient implements PoblacionObjetivoClient {

    static final String PATH = "/api/v1/users/by-role";
    static final String SERVICE_KEY_HEADER = "X-Service-Key";
    static final int PAGE_SIZE = 100;
    /** Límite de seguridad: 1000 páginas de 100 son 100 000 usuarios, muy por encima de los ~5000 del campus */
    static final int MAX_PAGES = 1000;

    private static final Logger log = LoggerFactory.getLogger(Ms01PoblacionObjetivoClient.class);

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private final String serviceKey;
    private final Duration requestTimeout;

    /**
     * @param httpClient cliente HTTP con el timeout de las llamadas internas (2000 ms)
     * @param jsonMapper lector de JSON
     * @param gatewayUrl URL base del API Gateway; si está vacía, MS01 se considera no disponible
     * @param serviceKey clave de servicio de MS04 (GATEWAY_SERVICE_KEY)
     * @param requestTimeout timeout de cada petición
     */
    public Ms01PoblacionObjetivoClient(HttpClient httpClient, JsonMapper jsonMapper, String gatewayUrl,
            String serviceKey, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.baseUrl = normalizar(gatewayUrl);
        this.serviceKey = serviceKey;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Optional<Set<UUID>> usuariosActivosPorRoles(Collection<String> roles, Instant creadosAntesDe,
            RequesterIdentity requester) {
        if (baseUrl == null) {
            log.warn("MS01 no disponible: el Gateway no está configurado (GATEWAY_URL)");
            return Optional.empty();
        }
        if (roles == null || roles.isEmpty()) {
            return Optional.of(Set.of());
        }
        try {
            Set<UUID> usuarios = new LinkedHashSet<>();
            int page = 0;
            int totalPaginas;
            do {
                PaginaUsuarios pagina = pedirPagina(roles, creadosAntesDe, page, requester);
                if (pagina.contenido() != null) {
                    pagina.contenido().stream().map(UsuarioMs01::id).filter(id -> id != null)
                            .forEach(usuarios::add);
                }
                totalPaginas = pagina.totalPaginas();
                page++;
            } while (page < totalPaginas && page < MAX_PAGES);
            return Optional.of(Set.copyOf(usuarios));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Se interrumpió la consulta de la población objetivo a MS01");
            return Optional.empty();
        } catch (Exception e) {
            // Nunca se reenvía el cuerpo de error de MS01 (RNF-MS04-11): solo queda en el log el motivo
            log.error("MS01 no entregó la población objetivo: {}", e.toString());
            return Optional.empty();
        }
    }

    private PaginaUsuarios pedirPagina(Collection<String> roles, Instant creadosAntesDe, int page,
            RequesterIdentity requester) throws java.io.IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(roles, creadosAntesDe, page))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET();
        agregarCabeceras(request, requester);
        HttpResponse<String> response = httpClient.send(request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("MS01 respondió HTTP " + response.statusCode());
        }
        return jsonMapper.readValue(response.body(), PaginaUsuarios.class);
    }

    private URI uri(Collection<String> roles, Instant creadosAntesDe, int page) {
        StringBuilder query = new StringBuilder()
                .append("roles=").append(codificar(String.join(",", roles.stream().sorted().toList())))
                .append("&estado=ACTIVO");
        if (creadosAntesDe != null) {
            query.append("&creados_antes_de=").append(codificar(DateTimeFormatter.ISO_INSTANT.format(creadosAntesDe)));
        }
        query.append("&page=").append(page).append("&size=").append(PAGE_SIZE);
        return URI.create(baseUrl + PATH + "?" + query);
    }

    private void agregarCabeceras(HttpRequest.Builder request, RequesterIdentity requester) {
        if (serviceKey != null && !serviceKey.isBlank()) {
            request.header(SERVICE_KEY_HEADER, serviceKey);
        }
        String correlationId = MDC.get(CorrelationIds.MDC_KEY);
        if (correlationId != null) {
            request.header(CorrelationIds.HEADER, correlationId);
        }
        if (requester != null) {
            request.header(IdentityHeaders.USER_ID, requester.userId().toString());
            request.header(IdentityHeaders.USER_ROLE, requester.rolesComoCabecera());
        }
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static String normalizar(String gatewayUrl) {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            return null;
        }
        String base = gatewayUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * Página de usuarios de MS01 con la paginación de los Estándares. Solo se leen los campos que MS04 usa
     *
     * @param contenido usuarios de la página
     * @param totalPaginas total de páginas
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PaginaUsuarios(
            List<UsuarioMs01> contenido,
            @JsonProperty("total_paginas") int totalPaginas) {
    }

    /**
     * Usuario de MS01. Del usuario MS04 solo usa su id (RN-MS04-02)
     *
     * @param id id del usuario
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsuarioMs01(UUID id) {
    }
}
