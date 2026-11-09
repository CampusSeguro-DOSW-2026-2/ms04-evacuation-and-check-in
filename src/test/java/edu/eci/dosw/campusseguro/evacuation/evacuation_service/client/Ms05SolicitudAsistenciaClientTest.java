package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ResultadoEnvioMs05.Tipo;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de MS05 contra un servidor HTTP local que hace de Gateway. El contrato es provisional (decisión abierta
 * n.º 3 de los Estándares); estas pruebas fijan lo que MS04 envía y cómo interpreta cada respuesta
 */
class Ms05SolicitudAsistenciaClientTest {

    private static final String RUTA = "/api/v1/internal/solicitudes-asistencia";
    private static final UUID SOLICITUD = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final UUID CHECKIN = UUID.fromString("8b2c3d4e-5f6a-4b7c-9d8e-1f2a3b4c5d6e");
    private static final UUID EVENTO = UUID.fromString("7c3d4e5f-6a7b-4c8d-8e9f-2a3b4c5d6e7f");
    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID EDIFICIO = UUID.fromString("5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a");

    private HttpServer servidor;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<Headers> cabeceras = new AtomicReference<>();
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private volatile int status;
    private volatile String cuerpo;
    private volatile long demoraMs;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", exchange -> {
            metodo.set(exchange.getRequestMethod());
            ruta.set(exchange.getRequestURI().getPath());
            cabeceras.set(exchange.getRequestHeaders());
            cuerpoRecibido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = cuerpo == null ? new byte[0] : cuerpo.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    private Ms05SolicitudAsistenciaClient cliente(long timeoutMs) {
        return new Ms05SolicitudAsistenciaClient("http://127.0.0.1:" + servidor.getAddress().getPort() + "/",
                "clave-ms04", timeoutMs, RUTA);
    }

    private void responder(int status, String cuerpo) {
        this.status = status;
        this.cuerpo = cuerpo;
    }

    /** Solicitud como la deja la base de datos: el id lo genera Hibernate al guardarla */
    private static SolicitudAsistencia solicitud() throws ReflectiveOperationException {
        SolicitudAsistencia s = new SolicitudAsistencia(CHECKIN, EVENTO, USUARIO, TipoUbicacion.EDIFICIO, EDIFICIO,
                Instant.parse("2026-11-08T15:00:00Z"));
        Field id = SolicitudAsistencia.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(s, SOLICITUD);
        return s;
    }

    @Test
    void unaSolicitudCreadaDevuelveElIdDeMs05YLaPeticionSigueElEstandar() throws Exception {
        responder(201, "{\"id\":\"SOL-123\",\"estado\":\"REPORTADA\"}");

        ResultadoEnvioMs05 resultado = cliente(2000).enviar(solicitud(), "ESTUDIANTE", "corr-1");

        assertEquals(ResultadoEnvioMs05.creada("SOL-123"), resultado);
        assertEquals("POST", metodo.get());
        assertEquals(RUTA, ruta.get());
        Headers h = cabeceras.get();
        assertEquals("clave-ms04", h.getFirst("X-Service-Key"));
        assertEquals("corr-1", h.getFirst("X-Correlation-ID"));
        assertEquals(USUARIO.toString(), h.getFirst("X-User-Id"));
        assertEquals("ESTUDIANTE", h.getFirst("X-User-Role"));
    }

    @Test
    void elCuerpoLlevaLaLlaveDeIdempotenciaElEventoElUsuarioElCheckInYLaUbicacion() throws Exception {
        responder(201, "{\"id\":\"SOL-123\"}");

        cliente(2000).enviar(solicitud(), "ESTUDIANTE", "corr-1");

        JsonNode cuerpo = JsonMapper.builder().build().readTree(cuerpoRecibido.get());
        assertEquals(SOLICITUD.toString(), cuerpo.get("solicitudId").asString());
        assertEquals(EVENTO.toString(), cuerpo.get("eventoId").asString());
        assertEquals(USUARIO.toString(), cuerpo.get("usuarioId").asString());
        assertEquals(CHECKIN.toString(), cuerpo.get("checkinId").asString());
        assertEquals("EDIFICIO", cuerpo.get("ubicacion").get("tipo").asString());
        assertEquals(EDIFICIO.toString(), cuerpo.get("ubicacion").get("id").asString());
        assertEquals(5, cuerpo.size());
    }

    @Test
    void elCuerpoViajaConContentLengthYNoEnBloques() throws Exception {
        responder(201, "{\"id\":\"SOL-123\"}");

        cliente(2000).enviar(solicitud(), "ESTUDIANTE", "corr-1");

        Headers h = cabeceras.get();
        assertNull(h.getFirst("Transfer-Encoding"));
        assertEquals(String.valueOf(cuerpoRecibido.get().getBytes(StandardCharsets.UTF_8).length),
                h.getFirst("Content-Length"));
        assertEquals("application/json", h.getFirst("Content-Type"));
    }

    @Test
    void sinRolesConocidosNoEnviaXUserRole() throws Exception {
        responder(200, "{\"id\":\"SOL-123\"}");

        cliente(2000).enviar(solicitud(), null, "corr-1");

        assertNull(cabeceras.get().getFirst("X-User-Role"));
        assertEquals(USUARIO.toString(), cabeceras.get().getFirst("X-User-Id"));
    }

    @Test
    void unIdNumericoDeMs05SeGuardaComoTexto() throws Exception {
        responder(201, "{\"id\":42}");

        assertEquals(ResultadoEnvioMs05.creada("42"), cliente(2000).enviar(solicitud(), "ESTUDIANTE", "c"));
    }

    @Test
    void datosInvalidosEnMs05SonRechazada() throws Exception {
        for (int codigo : new int[] { 400, 422 }) {
            responder(codigo, "{\"codigo\":\"VALIDACION\",\"mensaje\":\"detalle interno\"}");
            ResultadoEnvioMs05 resultado = cliente(2000).enviar(solicitud(), "ESTUDIANTE", "c");
            assertEquals(Tipo.RECHAZADA, resultado.tipo());
            assertNull(resultado.solicitudMs05Id());
        }
    }

    @Test
    void cualquierOtraRespuestaEsNoDisponible() throws Exception {
        for (int codigo : new int[] { 500, 502, 503, 404, 401, 403, 409 }) {
            responder(codigo, "{\"mensaje\":\"detalle interno\"}");
            assertEquals(Tipo.NO_DISPONIBLE, cliente(2000).enviar(solicitud(), "ESTUDIANTE", "c").tipo());
        }
    }

    @Test
    void unaRespuestaExitosaSinIdEsNoDisponible() throws Exception {
        responder(201, "{\"estado\":\"REPORTADA\"}");
        assertEquals(Tipo.NO_DISPONIBLE, cliente(2000).enviar(solicitud(), "ESTUDIANTE", "c").tipo());

        responder(201, null);
        assertEquals(Tipo.NO_DISPONIBLE, cliente(2000).enviar(solicitud(), "ESTUDIANTE", "c").tipo());
    }

    @Test
    void siMs05NoRespondeATiempoEsNoDisponible() throws Exception {
        responder(201, "{\"id\":\"SOL-123\"}");
        demoraMs = 1500;

        assertEquals(Tipo.NO_DISPONIBLE, cliente(300).enviar(solicitud(), "ESTUDIANTE", "c").tipo());
    }

    @Test
    void sinLaUrlDelGatewayEsNoDisponible() throws Exception {
        Ms05SolicitudAsistenciaClient sinUrl = new Ms05SolicitudAsistenciaClient("", "clave", 2000, RUTA);

        assertEquals(Tipo.NO_DISPONIBLE, sinUrl.enviar(solicitud(), "ESTUDIANTE", "c").tipo());
    }

    @Test
    void siElGatewayNoEstaArribaEsNoDisponible() throws Exception {
        Ms05SolicitudAsistenciaClient cliente = cliente(2000);
        servidor.stop(0);

        assertEquals(Tipo.NO_DISPONIBLE, cliente.enviar(solicitud(), "ESTUDIANTE", "c").tipo());
    }
}
