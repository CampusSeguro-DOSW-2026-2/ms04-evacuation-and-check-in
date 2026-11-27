package edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * The publisher sends the event to MS08 through the Gateway, with the body, the route and the headers of the
 * standards (sections 5 and 12), asynchronously and without throwing exceptions even if MS08 fails
 */
class AuditEventPublisherTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID CHECKIN = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant DATE = Instant.parse("2026-11-20T15:04:12Z");
    private static final UUID ID = UUID.fromString("2b7e4c90-1d3a-4f65-8e2b-9c0a1d4e7f36");
    private static final UUID EVENT = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID POINT = UUID.fromString("1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b");

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
    private volatile int answer = 202;
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /**
     * Starts a local server that plays the role of MS08 behind the Gateway
     *
     * @throws IOException if the server cannot start
     */
    @BeforeEach
    void startMs08() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(), body));
            exchange.sendResponseHeaders(answer, -1);
            exchange.close();
        });
        server.start();
    }

    /**
     * Stops the local server
     */
    @AfterEach
    void stop() {
        server.stop(0);
    }

    /**
     * URL of the local server
     *
     * @return the URL
     */
    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Publisher pointing to a Gateway
     *
     * @param gatewayUrl URL of the Gateway
     * @param key service key
     * @return the publisher
     */
    private AuditEventPublisherImpl publisher(String gatewayUrl, String key) {
        return new AuditEventPublisherImpl(client, gatewayUrl, key, Duration.ofSeconds(2));
    }

    /**
     * Audit event of a user with the event and its attributes
     *
     * @return the audit event
     */
    private AuditEvent userEvent() {
        return new AuditEvent(ID, AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, USER, "BRIGADISTA,ESTUDIANTE",
                "ms04:checkin:write,ms04:dashboard:read", null, CORRELATION, DATE, "checkin:" + CHECKIN,
                new AuditContext(EVENT, true, Map.of()).con("estado", "SAFE").con("punto_encuentro_id", POINT));
    }

    /**
     * Waits for the request that the local server receives
     *
     * @return the received request
     * @throws InterruptedException if the wait is interrupted
     */
    private Received awaitRequest() throws InterruptedException {
        Received request = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(request, "MS08 did not receive the event");
        return request;
    }

    /**
     * Sends the event to the internal audit route with the headers of the Gateway
     *
     * @throws Exception if the wait fails
     */
    @Test
    void sendsTheEventToTheAuditRouteWithTheGatewayHeaders() throws Exception {
        publisher(url() + "/", "clave-de-servicio-de-ms04-0123456789").publish(userEvent());

        Received request = awaitRequest();
        assertEquals("POST", request.method());
        assertEquals("/api/v1/audit/events", request.path());
        assertEquals("clave-de-servicio-de-ms04-0123456789", request.headers().get("X-service-key").get(0));
        assertEquals(CORRELATION, request.headers().get("X-correlation-id").get(0));
        assertEquals(USER.toString(), request.headers().get("X-user-id").get(0));
        assertEquals("BRIGADISTA,ESTUDIANTE", request.headers().get("X-user-role").get(0));
        assertEquals("ms04:checkin:write,ms04:dashboard:read", request.headers().get("X-user-permission").get(0));
        assertTrue(request.headers().get("Content-type").get(0).startsWith("application/json"));
    }

    /**
     * The body has the fields of the standards with the event and its attributes
     *
     * @throws Exception if the wait fails
     */
    @Test
    void theBodyHasTheFieldsOfTheStandards() throws Exception {
        publisher(url(), "clave").publish(userEvent());

        assertEquals("{\"id\":\"" + ID + "\",\"usuario_id\":\"" + USER + "\",\"modulo\":\"MS04\","
                + "\"accion\":\"REGISTRO_CHECKIN\",\"recurso_afectado\":\"checkin:" + CHECKIN + "\","
                + "\"resultado\":\"EXITO\",\"fecha_hora\":\"2026-11-20T15:04:12Z\",\"correlation_id\":\""
                + CORRELATION + "\",\"detalle\":null,\"evento_id\":\"" + EVENT + "\",\"tipo_evento\":\"SIMULACRO\","
                + "\"atributos\":{\"estado\":\"SAFE\",\"punto_encuentro_id\":\"" + POINT + "\"}}",
                awaitRequest().body());
    }

    /**
     * An event without user propagates no identity and carries a null user
     *
     * @throws Exception if the wait fails
     */
    @Test
    void anEventWithoutUserPropagatesNoIdentity() throws Exception {
        AuditEvent event = new AuditEvent(ID, AuditAction.CAMBIO_ESTADO_EVENTO, AuditResult.EXITO, null, null, null,
                "origen=ms06 anterior=PROGRAMADO nuevo=EN_EJECUCION", CORRELATION, DATE, "evento:" + CHECKIN, null);

        publisher(url(), "clave").publish(event);

        Received request = awaitRequest();
        assertNull(request.headers().get("X-user-id"));
        assertNull(request.headers().get("X-user-role"));
        assertNull(request.headers().get("X-user-permission"));
        assertTrue(request.body().startsWith("{\"id\":\"" + ID + "\",\"usuario_id\":null,\"modulo\":\"MS04\","
                + "\"accion\":\"CAMBIO_ESTADO_EVENTO\""));
        assertTrue(request.body().endsWith("\"evento_id\":null,\"tipo_evento\":null,\"atributos\":null}"));
        assertTrue(request.body().contains("\"detalle\":\"origen=ms06 anterior=PROGRAMADO nuevo=EN_EJECUCION\""));
    }

    /**
     * Without service key the header is not sent
     *
     * @throws Exception if the wait fails
     */
    @Test
    void withoutServiceKeyTheHeaderIsNotSent() throws Exception {
        publisher(url(), "").publish(userEvent());

        assertNull(awaitRequest().headers().get("X-service-key"));
    }

    /**
     * Without Gateway configured the event is only logged and nobody is called
     *
     * @throws Exception if the wait fails
     */
    @Test
    void withoutGatewayNobodyIsCalled() throws Exception {
        assertDoesNotThrow(() -> publisher("", "clave").publish(userEvent()));
        assertDoesNotThrow(() -> publisher(null, "clave").publish(userEvent()));

        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

    /**
     * If MS08 rejects the event or is down no exception is thrown
     *
     * @throws Exception if the wait fails
     */
    @Test
    void aRejectionOrAnMs08DownThrowsNoException() throws Exception {
        answer = 500;
        assertDoesNotThrow(() -> publisher(url(), "clave").publish(userEvent()));
        awaitRequest();

        int closedPort = server.getAddress().getPort();
        server.stop(0);
        assertDoesNotThrow(() -> publisher("http://127.0.0.1:" + closedPort, "clave").publish(userEvent()));
        assertFalse(received.iterator().hasNext());
    }

    /**
     * The audit route is built with or without a final slash
     *
     * @throws Exception if the wait fails
     */
    @Test
    void theAuditRouteIsBuiltWithOrWithoutFinalSlash() throws Exception {
        publisher(url() + "///", "clave").publish(userEvent());

        assertEquals("/api/v1/audit/events", awaitRequest().path());
    }

    /**
     * The JSON escapes the detail
     */
    @Test
    void theJsonEscapesTheDetail() {
        AuditEvent event = new AuditEvent(ID, AuditAction.SOLICITUD_ASISTENCIA, AuditResult.FALLO, USER, null,
                null, "dijo \"hola\"\n", CORRELATION, DATE, null, null);

        String json = AuditEventPublisherImpl.toJson(event);

        assertTrue(json.contains("\"recurso_afectado\":null"));
        assertTrue(json.contains("\"detalle\":\"dijo \\\"hola\\\"\\n\""));
        assertTrue(json.contains("\"resultado\":\"FALLO\""));
    }
}
