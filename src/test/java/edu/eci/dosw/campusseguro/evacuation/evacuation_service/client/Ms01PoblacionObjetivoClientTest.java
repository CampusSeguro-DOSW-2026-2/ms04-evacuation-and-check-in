package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de MS01 para la población objetivo, contra un servidor HTTP local que hace de Gateway
 */
class Ms01PoblacionObjetivoClientTest {

    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID BETO = UUID.fromString("5a2b7c9d-1e3f-4a5b-8c7d-9e0f1a2b3c4d");
    private static final UUID CARLA = UUID.fromString("6b3c8d0e-2f4a-4b6c-9d8e-0f1a2b3c4d5e");
    private static final Instant CORTE = Instant.parse("2026-11-20T15:00:00Z");
    private static final RequesterIdentity AUDITOR = new RequesterIdentity(ANA, EnumSet.of(Rol.AUDITOR));

    private HttpServer servidor;
    private final List<HttpExchangeCapturado> peticiones = new CopyOnWriteArrayList<>();
    private Function<Integer, Respuesta> respuestas;

    private record Respuesta(int status, String cuerpo) {
    }

    private record HttpExchangeCapturado(String query, Headers headers) {
    }

    @BeforeEach
    void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext(Ms01PoblacionObjetivoClient.PATH, this::atender);
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
        MDC.clear();
    }

    private void atender(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        peticiones.add(new HttpExchangeCapturado(query, exchange.getRequestHeaders()));
        int page = Integer.parseInt(query.replaceAll(".*[?&]?page=(\\d+).*", "$1"));
        Respuesta respuesta = respuestas.apply(page);
        byte[] cuerpo = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(respuesta.status(), cuerpo.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(cuerpo);
        }
    }

    private Ms01PoblacionObjetivoClient cliente(String gatewayUrl) {
        return new Ms01PoblacionObjetivoClient(HttpClient.newHttpClient(), JsonMapper.builder().build(),
                gatewayUrl, "clave-de-servicio-ms04", Duration.ofMillis(2000));
    }

    private Ms01PoblacionObjetivoClient cliente() {
        return cliente("http://127.0.0.1:" + servidor.getAddress().getPort() + "/");
    }

    private static String pagina(int page, int totalPaginas, UUID... ids) {
        StringBuilder contenido = new StringBuilder();
        for (UUID id : ids) {
            if (!contenido.isEmpty()) {
                contenido.append(',');
            }
            contenido.append("{\"id\":\"").append(id).append("\",\"primer_nombre\":\"Ana\",\"estado\":\"ACTIVO\"}");
        }
        return "{\"contenido\":[" + contenido + "],\"page\":" + page + ",\"size\":100,\"total_elementos\":"
                + ids.length + ",\"total_paginas\":" + totalPaginas + "}";
    }

    @Test
    void recorreTodasLasPaginasYCuentaUnaVezACadaPersona() {
        Map<Integer, String> paginas = Map.of(
                0, pagina(0, 2, ANA, BETO),
                1, pagina(1, 2, BETO, CARLA));
        respuestas = page -> new Respuesta(200, paginas.get(page));

        Optional<Set<UUID>> poblacion = cliente().usuariosActivosPorRoles(List.of("PROFESOR", "ESTUDIANTE"),
                CORTE, AUDITOR);

        assertEquals(Optional.of(Set.of(ANA, BETO, CARLA)), poblacion);
        assertEquals(2, peticiones.size());
    }

    @Test
    void enviaLosFiltrosLaClaveDeServicioLaCorrelacionYLaIdentidad() {
        respuestas = page -> new Respuesta(200, pagina(0, 1, ANA));
        MDC.put(CorrelationIds.MDC_KEY, "c19b472d-7a2a-4d3b-a12b-abcdef123456");

        cliente().usuariosActivosPorRoles(List.of("PROFESOR", "ESTUDIANTE"), CORTE, AUDITOR);

        HttpExchangeCapturado peticion = peticiones.get(0);
        assertEquals("roles=ESTUDIANTE%2CPROFESOR&estado=ACTIVO&creados_antes_de=2026-11-20T15%3A00%3A00Z"
                + "&page=0&size=100", peticion.query());
        assertEquals("clave-de-servicio-ms04", peticion.headers().getFirst("X-Service-Key"));
        assertEquals("c19b472d-7a2a-4d3b-a12b-abcdef123456", peticion.headers().getFirst("X-Correlation-ID"));
        assertEquals(ANA.toString(), peticion.headers().getFirst("X-User-Id"));
        assertEquals("AUDITOR", peticion.headers().getFirst("X-User-Role"));
    }

    @Test
    void unErrorDeMs01DejaLaPoblacionComoNoDisponible() {
        respuestas = page -> new Respuesta(503, "{\"codigo\":\"DEPENDENCIA_NO_DISPONIBLE\"}");

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    @Test
    void unCuerpoInvalidoDejaLaPoblacionComoNoDisponible() {
        respuestas = page -> new Respuesta(200, "no es json");

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    @Test
    void siUnaPaginaFallaNoSeEntregaUnaPoblacionIncompleta() {
        respuestas = page -> page == 0 ? new Respuesta(200, pagina(0, 2, ANA)) : new Respuesta(500, "{}");

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    @Test
    void sinGatewayConfiguradoLaPoblacionNoEstaDisponible() {
        assertTrue(cliente("").usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
        assertTrue(peticiones.isEmpty());
    }

    @Test
    void unGatewayQueNoRespondeDejaLaPoblacionComoNoDisponible() {
        servidor.stop(0);

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    @Test
    void sinRolesLaPoblacionEstaVaciaSinLlamarAMs01() {
        assertEquals(Optional.of(Set.of()), cliente().usuariosActivosPorRoles(List.of(), CORTE, AUDITOR));
        assertTrue(peticiones.isEmpty());
    }
}
