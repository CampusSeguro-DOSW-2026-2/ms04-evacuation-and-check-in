package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * MS02 client against a local HTTP server that plays the role of the Gateway
 */
class Ms02PuntoEncuentroClientTest {

    private static final UUID POINT = UUID.fromString("0b6f1d2e-3c4a-4b5d-8e9f-a1b2c3d4e5f6");
    private static final RequesterIdentity USER = TestIdentities.identity(
            UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42"), "PROFESOR", "BRIGADISTA");

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<Headers> headers = new AtomicReference<>();
    private volatile int status;
    private volatile String body;
    private volatile long delayMs;

    /**
     * Starts the local server that answers with the configured status, body and delay
     *
     * @throws IOException if the server cannot start
     */
    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            headers.set(exchange.getRequestHeaders());
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = new byte[0];
            if (body != null) {
                bytes = body.getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (bytes.length == 0) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
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
     * Client pointing to the local server
     *
     * @param timeoutMs client timeout
     * @return the client
     */
    private Ms02PuntoEncuentroClient client(long timeoutMs) {
        return new Ms02PuntoEncuentroClient("http://127.0.0.1:" + server.getAddress().getPort() + "/",
                "clave-ms04", timeoutMs, "/api/v1/safe-zones/{id}");
    }

    /**
     * Sets the answer of the local server
     *
     * @param status status of the answer
     * @param body body of the answer, or null
     */
    private void answer(int status, String body) {
        this.status = status;
        this.body = body;
    }

    /**
     * Safe zone of MS02 in a state
     *
     * @param state state of the zone
     * @return the zone in JSON
     */
    private static String zone(String state) {
        return "{\"id\":\"" + POINT + "\",\"codigo\":\"MP-H-01\",\"nombre\":\"Plazoleta H\","
                + "\"ubicacion\":\"Bloque H\",\"descripcion\":\"Frente al bloque\",\"capacidad\":200,"
                + "\"estado\":\"" + state + "\"}";
    }

    /**
     * An active point is returned and the request carries the headers of the standard
     */
    @Test
    void anActivePointIsReturnedWithTheHeadersOfTheStandard() {
        answer(200, zone("ACTIVO"));

        Optional<PuntoEncuentro> point = client(2000).buscar(POINT, USER, "corr-123");

        assertEquals(Optional.of(new PuntoEncuentro(POINT, "MP-H-01", "Plazoleta H", true)), point);
        assertEquals("/api/v1/safe-zones/" + POINT, path.get());
        Headers received = headers.get();
        assertEquals("clave-ms04", received.getFirst("X-Service-Key"));
        assertEquals("corr-123", received.getFirst("X-Correlation-ID"));
        assertEquals(USER.userId().toString(), received.getFirst("X-User-Id"));
        assertEquals("BRIGADISTA,PROFESOR", received.getFirst("X-User-Role"));
        assertEquals(USER.permisosComoCabecera(), received.getFirst("X-User-Permission"));
    }

    /**
     * An inactive point is returned as not active
     */
    @Test
    void anInactivePointIsNotActive() {
        answer(200, zone("INACTIVO"));

        assertFalse(client(2000).buscar(POINT, USER, "c").orElseThrow().activo());
    }

    /**
     * A point that does not exist in MS02 returns empty
     */
    @Test
    void aPointThatDoesNotExistReturnsEmpty() {
        answer(404, "{\"status\":404,\"codigo\":\"NO_ENCONTRADO\"}");

        assertTrue(client(2000).buscar(POINT, USER, "c").isEmpty());
    }

    /**
     * Any other answer of MS02 is an unavailable dependency
     */
    @Test
    void anyOtherAnswerIsAnUnavailableDependency() {
        for (int code : new int[] { 500, 502, 503, 401, 403 }) {
            answer(code, "{\"mensaje\":\"detalle interno\"}");
            ApiException error = assertThrows(ApiException.class, () -> client(2000).buscar(POINT, USER, "c"));
            assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        }
    }

    /**
     * An answer without body is an unavailable dependency
     */
    @Test
    void anAnswerWithoutBodyIsAnUnavailableDependency() {
        answer(200, null);

        ApiException error = assertThrows(ApiException.class, () -> client(2000).buscar(POINT, USER, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * If MS02 does not answer in time it is an unavailable dependency
     */
    @Test
    void aLateAnswerIsAnUnavailableDependency() {
        answer(200, zone("ACTIVO"));
        delayMs = 1500;

        ApiException error = assertThrows(ApiException.class, () -> client(300).buscar(POINT, USER, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Without the URL of the Gateway it is an unavailable dependency
     */
    @Test
    void withoutGatewayUrlIsAnUnavailableDependency() {
        Ms02PuntoEncuentroClient withoutUrl = new Ms02PuntoEncuentroClient("", "clave", 2000,
                "/api/v1/safe-zones/{id}");

        ApiException error = assertThrows(ApiException.class, () -> withoutUrl.buscar(POINT, USER, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * If the Gateway is down it is an unavailable dependency
     */
    @Test
    void aGatewayDownIsAnUnavailableDependency() {
        Ms02PuntoEncuentroClient client = client(2000);
        server.stop(0);

        ApiException error = assertThrows(ApiException.class, () -> client.buscar(POINT, USER, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }
}
