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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * El publicador envía a MS08, a través del Gateway, el evento con el cuerpo, la ruta y las cabeceras de los
 * Estándares (secciones 5 y 12), de forma asíncrona y sin lanzar excepciones aunque MS08 falle
 */
class AuditEventPublisherTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID CHECKIN = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant FECHA = Instant.parse("2026-11-20T15:04:12Z");

    /** Lo que recibió el servidor de prueba */
    private record Recibido(String metodo, String ruta, java.util.Map<String, java.util.List<String>> cabeceras,
            String cuerpo) {
    }

    private HttpServer servidor;
    private final BlockingQueue<Recibido> recibidos = new LinkedBlockingQueue<>();
    private volatile int respuesta = 202;
    private final HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).build();

    @BeforeEach
    void arrancarMs08Simulado() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", exchange -> {
            String cuerpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recibidos.add(new Recibido(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders(), cuerpo));
            exchange.sendResponseHeaders(respuesta, -1);
            exchange.close();
        });
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private AuditEventPublisherImpl publicador(String gatewayUrl, String clave) {
        return new AuditEventPublisherImpl(cliente, gatewayUrl, clave, Duration.ofSeconds(2));
    }

    private AuditEvent eventoDeUsuario() {
        return new AuditEvent(AuditAction.REGISTRO_CHECKIN, AuditResult.EXITO, USUARIO, "BRIGADISTA,ESTUDIANTE", null,
                CORRELATION, FECHA, "checkin:" + CHECKIN);
    }

    private Recibido esperar() throws InterruptedException {
        Recibido recibido = recibidos.poll(5, TimeUnit.SECONDS);
        assertNotNull(recibido, "MS08 no recibió el evento");
        return recibido;
    }

    @Test
    void enviaElEventoALaRutaInternaDeAuditoriaConLasCabecerasDelGateway() throws Exception {
        publicador(url() + "/", "clave-de-servicio-de-ms04-0123456789").publish(eventoDeUsuario());

        Recibido recibido = esperar();
        assertEquals("POST", recibido.metodo());
        assertEquals("/api/v1/audit/events", recibido.ruta());
        assertEquals("clave-de-servicio-de-ms04-0123456789", recibido.cabeceras().get("X-service-key").get(0));
        assertEquals(CORRELATION, recibido.cabeceras().get("X-correlation-id").get(0));
        assertEquals(USUARIO.toString(), recibido.cabeceras().get("X-user-id").get(0));
        assertEquals("BRIGADISTA,ESTUDIANTE", recibido.cabeceras().get("X-user-role").get(0));
        assertTrue(recibido.cabeceras().get("Content-type").get(0).startsWith("application/json"));
    }

    @Test
    void elCuerpoTieneLosOchoCamposDeLosEstandares() throws Exception {
        publicador(url(), "clave").publish(eventoDeUsuario());

        assertEquals("{\"usuario_id\":\"" + USUARIO + "\",\"modulo\":\"MS04\",\"accion\":\"REGISTRO_CHECKIN\","
                + "\"recurso_afectado\":\"checkin:" + CHECKIN + "\",\"resultado\":\"EXITO\","
                + "\"fecha_hora\":\"2026-11-20T15:04:12Z\",\"correlation_id\":\"" + CORRELATION + "\","
                + "\"detalle\":null}", esperar().cuerpo());
    }

    @Test
    void unEventoSinUsuarioNoPropagaIdentidadYLlevaUsuarioNulo() throws Exception {
        AuditEvent evento = new AuditEvent(AuditAction.CAMBIO_ESTADO_EVENTO, AuditResult.EXITO, null, null,
                "origen=ms06 anterior=PROGRAMADO nuevo=EN_EJECUCION", CORRELATION, FECHA, "evento:" + CHECKIN);

        publicador(url(), "clave").publish(evento);

        Recibido recibido = esperar();
        assertNull(recibido.cabeceras().get("X-user-id"));
        assertNull(recibido.cabeceras().get("X-user-role"));
        assertTrue(recibido.cuerpo().startsWith("{\"usuario_id\":null,\"modulo\":\"MS04\","
                + "\"accion\":\"CAMBIO_ESTADO_EVENTO\""));
        assertTrue(recibido.cuerpo().contains("\"detalle\":\"origen=ms06 anterior=PROGRAMADO nuevo=EN_EJECUCION\""));
    }

    @Test
    void sinClaveDeServicioNoEnviaLaCabecera() throws Exception {
        publicador(url(), "").publish(eventoDeUsuario());

        assertNull(esperar().cabeceras().get("X-service-key"));
    }

    @Test
    void sinGatewayConfiguradoSoloRegistraEnElLogYNoLlamaANadie() throws Exception {
        assertDoesNotThrow(() -> publicador("", "clave").publish(eventoDeUsuario()));
        assertDoesNotThrow(() -> publicador(null, "clave").publish(eventoDeUsuario()));

        assertNull(recibidos.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void siMs08RechazaOEstaCaidoNoLanzaExcepciones() throws Exception {
        respuesta = 500;
        assertDoesNotThrow(() -> publicador(url(), "clave").publish(eventoDeUsuario()));
        esperar();

        int puertoCerrado = servidor.getAddress().getPort();
        servidor.stop(0);
        assertDoesNotThrow(() -> publicador("http://127.0.0.1:" + puertoCerrado, "clave")
                .publish(eventoDeUsuario()));
        assertFalse(recibidos.iterator().hasNext());
    }

    @Test
    void laRutaDeAuditoriaSeArmaConOSinBarraFinal() throws Exception {
        publicador(url() + "///", "clave").publish(eventoDeUsuario());

        assertEquals("/api/v1/audit/events", esperar().ruta());
    }

    @Test
    void elJsonEscapaElDetalle() {
        AuditEvent evento = new AuditEvent(AuditAction.SOLICITUD_ASISTENCIA, AuditResult.FALLO, USUARIO, null,
                "dijo \"hola\"\n", CORRELATION, FECHA, null);

        String json = AuditEventPublisherImpl.toJson(evento);

        assertTrue(json.contains("\"recurso_afectado\":null"));
        assertTrue(json.contains("\"detalle\":\"dijo \\\"hola\\\"\\n\""));
        assertTrue(json.contains("\"resultado\":\"FALLO\""));
    }
}
