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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Cliente de MS02 contra un servidor HTTP local que hace de Gateway
 */
class Ms02PuntoEncuentroClientTest {

    private static final UUID PUNTO = UUID.fromString("0b6f1d2e-3c4a-4b5d-8e9f-a1b2c3d4e5f6");
    private static final RequesterIdentity USUARIO = new RequesterIdentity(
            UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42"), Set.of(Rol.PROFESOR, Rol.BRIGADISTA));

    private HttpServer servidor;
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<Headers> cabeceras = new AtomicReference<>();
    private volatile int status;
    private volatile String cuerpo;
    private volatile long demoraMs;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", exchange -> {
            ruta.set(exchange.getRequestURI().getPath());
            cabeceras.set(exchange.getRequestHeaders());
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

    private Ms02PuntoEncuentroClient cliente(long timeoutMs) {
        return new Ms02PuntoEncuentroClient("http://127.0.0.1:" + servidor.getAddress().getPort() + "/",
                "clave-ms04", timeoutMs, "/api/v1/safe-zones/{id}");
    }

    private void responder(int status, String cuerpo) {
        this.status = status;
        this.cuerpo = cuerpo;
    }

    private static String zona(String estado) {
        return "{\"id\":\"" + PUNTO + "\",\"codigo\":\"MP-H-01\",\"nombre\":\"Plazoleta H\","
                + "\"ubicacion\":\"Bloque H\",\"descripcion\":\"Frente al bloque\",\"capacidad\":200,"
                + "\"estado\":\"" + estado + "\"}";
    }

    @Test
    void unPuntoActivoSeDevuelveYLaPeticionLlevaLasCabecerasDelEstandar() {
        responder(200, zona("ACTIVO"));

        Optional<PuntoEncuentro> punto = cliente(2000).buscar(PUNTO, USUARIO, "corr-123");

        assertEquals(Optional.of(new PuntoEncuentro(PUNTO, "MP-H-01", "Plazoleta H", true)), punto);
        assertEquals("/api/v1/safe-zones/" + PUNTO, ruta.get());
        Headers h = cabeceras.get();
        assertEquals("clave-ms04", h.getFirst("X-Service-Key"));
        assertEquals("corr-123", h.getFirst("X-Correlation-ID"));
        assertEquals(USUARIO.userId().toString(), h.getFirst("X-User-Id"));
        assertEquals("BRIGADISTA,PROFESOR", h.getFirst("X-User-Role"));
    }

    @Test
    void unPuntoInactivoSeDevuelveComoNoActivo() {
        responder(200, zona("INACTIVO"));

        assertFalse(cliente(2000).buscar(PUNTO, USUARIO, "c").orElseThrow().activo());
    }

    @Test
    void unPuntoQueNoExisteEnMs02DevuelveVacio() {
        responder(404, "{\"status\":404,\"codigo\":\"NO_ENCONTRADO\"}");

        assertTrue(cliente(2000).buscar(PUNTO, USUARIO, "c").isEmpty());
    }

    @Test
    void cualquierOtraRespuestaDeMs02EsDependenciaNoDisponible() {
        for (int codigo : new int[] { 500, 502, 503, 401, 403 }) {
            responder(codigo, "{\"mensaje\":\"detalle interno\"}");
            ApiException e = assertThrows(ApiException.class, () -> cliente(2000).buscar(PUNTO, USUARIO, "c"));
            assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, e.getCode());
        }
    }

    @Test
    void unaRespuestaSinCuerpoEsDependenciaNoDisponible() {
        responder(200, null);

        ApiException e = assertThrows(ApiException.class, () -> cliente(2000).buscar(PUNTO, USUARIO, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, e.getCode());
    }

    @Test
    void siMs02NoRespondeATiempoEsDependenciaNoDisponible() {
        responder(200, zona("ACTIVO"));
        demoraMs = 1500;

        ApiException e = assertThrows(ApiException.class, () -> cliente(300).buscar(PUNTO, USUARIO, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, e.getCode());
    }

    @Test
    void sinLaUrlDelGatewayEsDependenciaNoDisponible() {
        Ms02PuntoEncuentroClient sinUrl = new Ms02PuntoEncuentroClient("", "clave", 2000,
                "/api/v1/safe-zones/{id}");

        ApiException e = assertThrows(ApiException.class, () -> sinUrl.buscar(PUNTO, USUARIO, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, e.getCode());
    }

    @Test
    void siElGatewayNoEstaArribaEsDependenciaNoDisponible() {
        Ms02PuntoEncuentroClient cliente = cliente(2000);
        servidor.stop(0);

        ApiException e = assertThrows(ApiException.class, () -> cliente.buscar(PUNTO, USUARIO, "c"));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, e.getCode());
    }
}
