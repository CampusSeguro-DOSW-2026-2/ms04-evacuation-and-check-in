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
 * El cliente del Gateway llama con la clave de servicio, la correlación y la identidad del usuario (Estándares,
 * sección 5) y convierte cualquier fallo en 503 DEPENDENCIA_NO_DISPONIBLE sin reenviar el cuerpo de error del otro
 * servicio. Se prueba contra un servidor HTTP real
 */
class GatewayClientTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String CLAVE = "clave-de-servicio-de-ms04-con-mas-de-32-caracteres";
    private static final UUID USUARIO = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");

    /** Lo que recibió el servidor de prueba */
    private record Recibido(String metodo, String ruta, Map<String, List<String>> cabeceras, String cuerpo) {
    }

    private HttpServer servidor;
    private final BlockingQueue<Recibido> recibidos = new LinkedBlockingQueue<>();
    private volatile int estado = 200;
    private volatile String respuesta = "{\"ok\":true}";
    private volatile long retardoMs = 0;

    /**
     * Arranca el servidor que hace de Gateway y fija la correlación de la petición en curso
     *
     * @throws IOException si no se puede abrir el puerto
     */
    @BeforeEach
    void arrancarGatewaySimulado() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", exchange -> {
            String cuerpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recibidos.add(new Recibido(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(), cuerpo));
            if (retardoMs > 0) {
                try {
                    Thread.sleep(retardoMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(estado, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        servidor.start();
        MDC.put(CorrelationIds.MDC_KEY, CORRELATION);
    }

    /**
     * Detiene el servidor y limpia la correlación
     */
    @AfterEach
    void detenerGatewaySimulado() {
        servidor.stop(0);
        MDC.remove(CorrelationIds.MDC_KEY);
    }

    /**
     * Cliente apuntando al servidor de prueba
     *
     * @param clave clave de servicio, puede ser null
     * @param timeoutMs timeout de cada llamada
     * @return el cliente del Gateway
     */
    private GatewayClient cliente(String clave, long timeoutMs) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs)).build();
        return new GatewayClient(http, "http://127.0.0.1:" + servidor.getAddress().getPort(), clave,
                Duration.ofMillis(timeoutMs));
    }

    /**
     * GET envía la clave de servicio, la correlación y la identidad del usuario sin modificarla
     *
     * @throws InterruptedException si se interrumpe la espera de la petición
     */
    @Test
    void getEnviaLaClaveLaCorrelacionYLaIdentidadDelUsuario() throws InterruptedException {
        JsonNode cuerpo = cliente(CLAVE, 2000).get("/api/v1/panel/zones", USUARIO, "BRIGADISTA,ESTUDIANTE");

        Recibido recibido = recibidos.poll(2, TimeUnit.SECONDS);
        assertEquals("GET", recibido.metodo());
        assertEquals("/api/v1/panel/zones", recibido.ruta());
        assertEquals(CLAVE, recibido.cabeceras().get("X-service-key").get(0));
        assertEquals(CORRELATION, recibido.cabeceras().get("X-correlation-id").get(0));
        assertEquals(USUARIO.toString(), recibido.cabeceras().get("X-user-id").get(0));
        assertEquals("BRIGADISTA,ESTUDIANTE", recibido.cabeceras().get("X-user-role").get(0));
        assertTrue(cuerpo.path("ok").asBoolean());
    }

    /**
     * GET sin usuario no envía las cabeceras de identidad
     *
     * @throws InterruptedException si se interrumpe la espera de la petición
     */
    @Test
    void getSinUsuarioNoEnviaLasCabecerasDeIdentidad() throws InterruptedException {
        cliente(CLAVE, 2000).get("/api/v1/panel/zones", null, null);

        Recibido recibido = recibidos.poll(2, TimeUnit.SECONDS);
        assertNull(recibido.cabeceras().get("X-user-id"));
        assertNull(recibido.cabeceras().get("X-user-role"));
    }

    /**
     * POST envía el cuerpo JSON como ruta interna: con clave de servicio y sin identidad de usuario
     *
     * @throws InterruptedException si se interrumpe la espera de la petición
     */
    @Test
    void postEnviaElCuerpoJsonComoRutaInterna() throws InterruptedException {
        respuesta = "{\"usuarios\":[],\"no_encontrados\":[]}";

        JsonNode cuerpo = cliente(CLAVE, 2000).post("/api/v1/users/batch", "{\"ids\":[\"a\"]}");

        Recibido recibido = recibidos.poll(2, TimeUnit.SECONDS);
        assertEquals("POST", recibido.metodo());
        assertEquals("/api/v1/users/batch", recibido.ruta());
        assertEquals("{\"ids\":[\"a\"]}", recibido.cuerpo());
        assertEquals("application/json", recibido.cabeceras().get("Content-type").get(0));
        assertEquals(CLAVE, recibido.cabeceras().get("X-service-key").get(0));
        assertNull(recibido.cabeceras().get("X-user-id"));
        assertTrue(cuerpo.path("usuarios").isArray());
    }

    /**
     * Sin clave de servicio ni correlación en el log, esas cabeceras no se envían
     *
     * @throws InterruptedException si se interrumpe la espera de la petición
     */
    @Test
    void sinClaveNiCorrelacionNoEnviaEsasCabeceras() throws InterruptedException {
        MDC.remove(CorrelationIds.MDC_KEY);

        cliente(null, 2000).get("/api/v1/panel/zones", null, null);

        Recibido recibido = recibidos.poll(2, TimeUnit.SECONDS);
        assertNull(recibido.cabeceras().get("X-service-key"));
        assertNull(recibido.cabeceras().get("X-correlation-id"));
    }

    /**
     * Una respuesta que no es 2xx se informa como 503 con el mensaje estándar, sin reenviar el cuerpo del otro
     * servicio
     *
     * @param codigo estado HTTP que responde el otro servicio
     */
    @ParameterizedTest
    @ValueSource(ints = { 400, 401, 403, 404, 500, 503 })
    void unaRespuestaQueNoEs2xxSeInformaComo503SinReenviarElCuerpo(int codigo) {
        estado = codigo;
        respuesta = "{\"mensaje\":\"detalle interno del otro servicio\"}";

        ApiException error = assertThrows(ApiException.class,
                () -> cliente(CLAVE, 2000).get("/api/v1/panel/zones", USUARIO, "BRIGADISTA"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE.getMessage(), error.getMessage());
    }

    /**
     * Un cuerpo que no es JSON se informa como 503
     */
    @Test
    void unCuerpoQueNoEsJsonSeInformaComo503() {
        respuesta = "esto no es json {";

        ApiException error = assertThrows(ApiException.class,
                () -> cliente(CLAVE, 2000).post("/api/v1/users/batch", "{}"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Superar el timeout de cliente se informa como 503
     */
    @Test
    void superarElTimeoutSeInformaComo503() {
        retardoMs = 1500;

        ApiException error = assertThrows(ApiException.class,
                () -> cliente(CLAVE, 300).get("/api/v1/panel/zones", USUARIO, "BRIGADISTA"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Un Gateway caído se informa como 503
     */
    @Test
    void unGatewayCaidoSeInformaComo503() {
        GatewayClient cliente = cliente(CLAVE, 500);
        servidor.stop(0);

        ApiException error = assertThrows(ApiException.class,
                () -> cliente.get("/api/v1/panel/zones", USUARIO, "BRIGADISTA"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Sin URL del Gateway no se llama a nadie y se informa 503
     *
     * @param url URL vacía o en blanco
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   " })
    void sinUrlDelGatewayNoLlamaYResponde503(String url) {
        HttpClient http = HttpClient.newHttpClient();
        GatewayClient cliente = new GatewayClient(http, url, CLAVE, Duration.ofMillis(500));

        ApiException error = assertThrows(ApiException.class, () -> cliente.post("/api/v1/users/batch", "{}"));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertTrue(recibidos.isEmpty());
    }

    /**
     * Una URL del Gateway que no es válida se informa como 503
     */
    @Test
    void unaUrlInvalidaSeInformaComo503() {
        GatewayClient cliente = new GatewayClient(HttpClient.newHttpClient(), "http://host con espacios", CLAVE,
                Duration.ofMillis(500));

        ApiException error = assertThrows(ApiException.class, () -> cliente.get("/api/v1/panel/zones", null, null));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Un hilo interrumpido se informa como 503 y conserva la marca de interrupción
     */
    @Test
    void unHiloInterrumpidoSeInformaComo503YConservaLaInterrupcion() {
        Thread.currentThread().interrupt();
        try {
            ApiException error = assertThrows(ApiException.class,
                    () -> cliente(CLAVE, 2000).get("/api/v1/panel/zones", null, null));

            assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted());
        }
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
