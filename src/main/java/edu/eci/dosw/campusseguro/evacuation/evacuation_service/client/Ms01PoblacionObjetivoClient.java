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
 * RNF-MS04-10). Recorre todas las páginas de 100 usuarios de cada rol
 *
 * El contrato es el de RF-MS01-09: la ruta recibe un solo rol ({@code rol}), el corte de creación
 * ({@code creado_antes}, incluido) y la paginación de los Estándares, y entrega a los usuarios activos e inactivos
 * con su {@code estado}. Por eso MS04 hace una consulta por cada rol convocado, se queda solo con los ACTIVOS
 * (RN-MS04-15) y cuenta una vez a quien tiene varios roles
 *
 * Nunca lanza excepción: si MS01 no responde, responde con error o el Gateway no está configurado, devuelve vacío
 * y deja el fallo en el log, para que quien llama entregue el resto de los datos (RF-MS04-10, A2)
 */
public class Ms01PoblacionObjetivoClient implements PoblacionObjetivoClient {

    static final String PATH = "/api/v1/users/by-role";
    static final String SERVICE_KEY_HEADER = "X-Service-Key";
    static final String ESTADO_ACTIVO = "ACTIVO";
    static final int PAGE_SIZE = 100;
    /** Límite de seguridad por rol: 1000 páginas de 100 son 100 000 usuarios, muy por encima de los ~5000 del campus */
    static final int MAX_PAGES = 1000;

    private static final Logger log = LoggerFactory.getLogger(Ms01PoblacionObjetivoClient.class);

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private final String serviceKey;
    private final Duration requestTimeout;

    /**
     * Crea el cliente de la población objetivo
     *
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

    /**
     * Usuarios ACTIVOS de MS01 con al menos uno de los roles, creados hasta el corte
     *
     * @param roles roles convocados del evento
     * @param creadosAntesDe corte de creación de los usuarios
     * @param requester identidad del usuario que consulta, que se propaga a MS01; puede ser null
     * @return los ids de la población objetivo, o vacío si MS01 no la entregó
     */
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
            for (String rol : roles.stream().distinct().sorted().toList()) {
                int page = 0;
                int totalPaginas;
                do {
                    PaginaUsuarios pagina = pedirPagina(rol, creadosAntesDe, page, requester);
                    if (pagina.contenido() != null) {
                        pagina.contenido().stream()
                                .filter(usuario -> usuario.id() != null && ESTADO_ACTIVO.equals(usuario.estado()))
                                .forEach(usuario -> usuarios.add(usuario.id()));
                    }
                    totalPaginas = pagina.totalPaginas();
                    page++;
                } while (page < totalPaginas && page < MAX_PAGES);
            }
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

    /**
     * Pide una página de los usuarios de un rol
     *
     * @param rol rol que deben tener los usuarios
     * @param creadosAntesDe corte de creación de los usuarios; null si no se limita
     * @param page número de página, desde 0
     * @param requester identidad que se propaga a MS01; puede ser null
     * @return la página de usuarios
     * @throws java.io.IOException si falla la conexión o no se puede leer la respuesta
     * @throws InterruptedException si se interrumpe la espera de la respuesta
     */
    private PaginaUsuarios pedirPagina(String rol, Instant creadosAntesDe, int page, RequesterIdentity requester)
            throws java.io.IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(rol, creadosAntesDe, page))
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

    /**
     * Arma la dirección de la consulta de una página
     *
     * @param rol rol que deben tener los usuarios
     * @param creadosAntesDe corte de creación de los usuarios; null si no se limita
     * @param page número de página, desde 0
     * @return la dirección completa de la consulta
     */
    private URI uri(String rol, Instant creadosAntesDe, int page) {
        StringBuilder query = new StringBuilder().append("rol=").append(codificar(rol));
        if (creadosAntesDe != null) {
            query.append("&creado_antes=").append(codificar(DateTimeFormatter.ISO_INSTANT.format(creadosAntesDe)));
        }
        query.append("&page=").append(page).append("&size=").append(PAGE_SIZE);
        return URI.create(baseUrl + PATH + "?" + query);
    }

    /**
     * Agrega las cabeceras de las llamadas internas
     *
     * @param request petición a la que se agregan
     * @param requester identidad que se propaga a MS01; puede ser null
     */
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

    /**
     * Codifica un valor para la query
     *
     * @param valor texto a codificar
     * @return el texto codificado
     */
    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    /**
     * Quita los espacios y las barras finales de la URL del Gateway
     *
     * @param gatewayUrl URL configurada
     * @return la URL normalizada, o null si no está configurada
     */
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
     * Usuario de MS01. De él MS04 solo usa su id y su estado (RN-MS04-02)
     *
     * @param id id del usuario
     * @param estado estado de la cuenta (ACTIVO o INACTIVO)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsuarioMs01(UUID id, String estado) {
    }
}
