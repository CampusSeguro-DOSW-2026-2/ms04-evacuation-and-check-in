package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import com.sun.net.httpserver.HttpServer;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import tools.jackson.databind.JsonNode;

/**
 * The Gateway client calls with the service key, the correlation and the identity of the user (standards, section 5)
 * and turns any failure into 503 DEPENDENCIA_NO_DISPONIBLE without forwarding the error body of the other service.
 * It is tested against a real HTTP server
 */
class GatewayClientTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String KEY = "clave-de-servicio-de-ms04-con-mas-de-32-caracteres";
    private static final UUID USER = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final String PERMISSIONS = "ms04:checkin:write,ms04:dashboard:read";

    /**
     * What the test server received
     *
     * @param method HTTP method
     * @param path path of the request
     * @param headers headers of the request
     * @param body body of the request
     */
    private record Received(String method, String path, Map<String, List<String>> headers, String body) {
    }

    private HttpServer server;
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();
    private volatile int status = 200;
    private volatile String answer = "{\"ok\":true}";
    private volatile long delayMs = 0;

    /**
     * Starts the server that plays the role of the Gateway and sets the correlation of the request in progress
     *
     * @throws IOException if the port cannot be opened
     */
    @BeforeEach
    void startGateway() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(), body));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        MDC.put(CorrelationIds.MDC_KEY, CORRELATION);
    }

    /**
     * Stops the server and clears the correlation
     */
    @AfterEach
    void stopGateway() {
        server.stop(0);
        MDC.remove(CorrelationIds.MDC_KEY);
    }

    /**
     * Client pointing to the test server
     *
     * @param key service key, may be null
     * @param timeoutMs timeout of each call
     * @return the Gateway client
     */
    private GatewayClient client(String key, long timeoutMs) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        return new GatewayClient(http, "http://127.0.0.1:" + server.getAddress().getPort(), key,
                Duration.ofMillis(timeoutMs));
    }

    /**
     * GET sends the service key, the correlation and the identity of the user without changing it
     *
     * @throws InterruptedException if the wait for the request is interrupted
     */
    @Test
    void getSendsTheKeyTheCorrelationAndTheIdentity() throws InterruptedException {
        JsonNode body = client(KEY, 2000).get("/api/v1/panel/zones", USER, "BRIGADISTA,ESTUDIANTE", PERMISSIONS);

        Received request = received.poll(2, TimeUnit.SECONDS);
        assertEquals("GET", request.method());
        assertEquals("/api/v1/panel/zones", request.path());
        assertEquals(KEY, request.headers().get("X-service-key").get(0));
        assertEquals(CORRELATION, request.headers().get("X-correlation-id").get(0));
        assertEquals(USER.toString(), request.headers().get("X-user-id").get(0));
        assertEquals("BRIGADISTA,ESTUDIANTE", request.headers().get("X-user-role").get(0));
        assertEquals(PERMISSIONS, request.headers().get("X-user-permission").get(0));
        assertTrue(body.path("ok").asBoolean());
    }

    /**
     * GET without user sends no identity header
     *
     * @throws InterruptedException if the wait for the request is interrupted
     */
    @Test
    void getWithoutUserSendsNoIdentityHeaders() throws InterruptedException {
        client(KEY, 2000).get("/api/v1/panel/zones", null, null, null);

        Received request = received.poll(2, TimeUnit.SECONDS);
        assertNull(request.headers().get("X-user-id"));
        assertNull(request.headers().get("X-user-role"));
        assertNull(request.headers().get("X-user-permission"));
    }

    /**
     * Without the permissions no identity header is propagated: the Gateway requires the three together (RF-GW-08)
     *
     * @throws InterruptedException if the wait for the request is interrupted
     */
    @Test
    void getWithoutPermissionsPropagatesNoIncompleteIdentity() throws InterruptedException {
        client(KEY, 2000).get("/api/v1/panel/zones", USER, "BRIGADISTA", null);

        Received request = received.poll(2, TimeUnit.SECONDS);
        assertNull(request.headers().get("X-user-id"));
        assertNull(request.headers().get("X-user-role"));
        assertNull(request.headers().get("X-user-permission"));
    }

    /**
     * POST sends the JSON body as an internal route: with service key and without user identity
     *
     * @throws InterruptedException if the wait for the request is interrupted
     */
    @Test
    void postSendsTheJsonBodyAsAnInternalRoute() throws InterruptedException {
        answer = "{\"usuarios\":[],\"no_encontrados\":[]}";

        JsonNode body = client(KEY, 2000).post("/api/v1/users/batch", "{\"ids\":[\"a\"]}");

        Received request = received.poll(2, TimeUnit.SECONDS);
        assertEquals("POST", request.method());
        assertEquals("/api/v1/users/batch", request.path());
        assertEquals("{\"ids\":[\"a\"]}", request.body());
        assertEquals("application/json", request.headers().get("Content-type").get(0));
        assertEquals(KEY, request.headers().get("X-service-key").get(0));
        assertNull(request.headers().get("X-user-id"));
        assertTrue(body.path("usuarios").isArray());
    }

    /**
     * Without service key nor correlation in the log, those headers are not sent
     *
     * @throws InterruptedException if the wait for the request is interrupted
     */
    @Test
    void withoutKeyNorCorrelationThoseHeadersAreNotSent() throws InterruptedException {
        MDC.remove(CorrelationIds.MDC_KEY);

        client(null, 2000).get("/api/v1/panel/zones", null, null, null);

        Received request = received.poll(2, TimeUnit.SECONDS);
        assertNull(request.headers().get("X-service-key"));
        assertNull(request.headers().get("X-correlation-id"));
    }

    /**
     * An answer that is not 2xx is reported as 503 with the standard message, without forwarding the body of the
     * other service
     *
     * @param code HTTP status that the other service answers
     */
    @ParameterizedTest
    @ValueSource(ints = { 400, 401, 403, 404, 500, 503 })
    void anAnswerThatIsNot2xxIsReportedAs503(int code) {
        status = code;
        answer = "{\"mensaje\":\"detalle interno del otro servicio\"}";

        ApiException error = assertThrows(ApiException.class,
                () -> client(KEY, 2000).get("/api/v1/panel/zones", USER, "BRIGADISTA", PERMISSIONS));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE.getMessage(), error.getMessage());
    }

    /**
     * A body that is not JSON is reported as 503
     */
    @Test
    void aBodyThatIsNotJsonIsReportedAs503() {
        answer = "esto no es json {";

        ApiException error = assertThrows(ApiException.class,
                () -> client(KEY, 2000).post("/api/v1/users/batch", "{}"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Exceeding the client timeout is reported as 503
     */
    @Test
    void exceedingTheTimeoutIsReportedAs503() {
        delayMs = 1500;

        ApiException error = assertThrows(ApiException.class,
                () -> client(KEY, 300).get("/api/v1/panel/zones", USER, "BRIGADISTA", PERMISSIONS));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * A Gateway that is down is reported as 503
     */
    @Test
    void aGatewayDownIsReportedAs503() {
        GatewayClient client = client(KEY, 500);
        server.stop(0);

        ApiException error = assertThrows(ApiException.class,
                () -> client.get("/api/v1/panel/zones", USER, "BRIGADISTA", PERMISSIONS));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Without URL of the Gateway nobody is called and 503 is reported
     *
     * @param url empty or blank URL
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void withoutGatewayUrlNobodyIsCalled(String url) {
        HttpClient http = HttpClient.newHttpClient();
        GatewayClient client = new GatewayClient(http, url, KEY, Duration.ofMillis(500));

        ApiException error = assertThrows(ApiException.class, () -> client.post("/api/v1/users/batch", "{}"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertTrue(received.isEmpty());
    }

    /**
     * An invalid URL of the Gateway is reported as 503
     */
    @Test
    void anInvalidUrlIsReportedAs503() {
        GatewayClient client = new GatewayClient(HttpClient.newHttpClient(), "http://host con espacios", KEY,
                Duration.ofMillis(500));

        ApiException error = assertThrows(ApiException.class,
                () -> client.get("/api/v1/panel/zones", null, null, null));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * An interrupted thread is reported as 503 and keeps the interruption flag
     */
    @Test
    void anInterruptedThreadIsReportedAs503AndKeepsTheFlag() {
        Thread.currentThread().interrupt();
        try {
            ApiException error = assertThrows(ApiException.class,
                    () -> client(KEY, 2000).get("/api/v1/panel/zones", null, null, null));

            assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted());
        }
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
