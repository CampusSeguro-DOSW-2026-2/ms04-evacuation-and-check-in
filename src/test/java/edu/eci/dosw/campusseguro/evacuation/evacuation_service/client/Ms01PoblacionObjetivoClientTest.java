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
import java.util.function.BiFunction;

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
 * Cliente de MS01 para la población objetivo, contra un servidor HTTP local que hace de Gateway con el contrato de
 * RF-MS01-09 (un rol por consulta, usuarios activos e inactivos)
 */
class Ms01PoblacionObjetivoClientTest {

    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID BETO = UUID.fromString("5a2b7c9d-1e3f-4a5b-8c7d-9e0f1a2b3c4d");
    private static final UUID CARLA = UUID.fromString("6b3c8d0e-2f4a-4b6c-9d8e-0f1a2b3c4d5e");
    private static final Instant CORTE = Instant.parse("2026-11-20T15:00:00Z");
    private static final RequesterIdentity AUDITOR = new RequesterIdentity(ANA, EnumSet.of(Rol.AUDITOR));

    private HttpServer servidor;
    private final List<HttpExchangeCapturado> peticiones = new CopyOnWriteArrayList<>();
    private BiFunction<String, Integer, Respuesta> respuestas;

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
        String rol = query.replaceAll("^rol=([A-Z_]+).*", "$1");
        int page = Integer.parseInt(query.replaceAll(".*[?&]page=(\\d+).*", "$1"));
        Respuesta respuesta = respuestas.apply(rol, page);
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

    private static String usuario(UUID id, String estado) {
        return "{\"id\":\"" + id + "\",\"primer_nombre\":\"Ana\",\"estado\":\"" + estado + "\"}";
    }

    private static String pagina(int page, int totalPaginas, String... usuarios) {
        return "{\"contenido\":[" + String.join(",", usuarios) + "],\"page\":" + page
                + ",\"size\":100,\"total_elementos\":" + usuarios.length + ",\"total_paginas\":"
                + totalPaginas + "}";
    }

    private static String activos(int page, int totalPaginas, UUID... ids) {
        String[] usuarios = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            usuarios[i] = usuario(ids[i], "ACTIVO");
        }
        return pagina(page, totalPaginas, usuarios);
    }

    /**
     * Pide cada rol por separado, recorre todas sus páginas y cuenta una sola vez a quien tiene varios roles
     */
    @Test
    void recorreTodasLasPaginasDeCadaRolYCuentaUnaVezACadaPersona() {
        Map<String, String> paginas = Map.of(
                "ESTUDIANTE0", activos(0, 2, ANA, BETO),
                "ESTUDIANTE1", activos(1, 2, CARLA),
                "PROFESOR0", activos(0, 1, BETO));
        respuestas = (rol, page) -> new Respuesta(200, paginas.get(rol + page));

        Optional<Set<UUID>> poblacion = cliente().usuariosActivosPorRoles(List.of("PROFESOR", "ESTUDIANTE"),
                CORTE, AUDITOR);

        assertEquals(Optional.of(Set.of(ANA, BETO, CARLA)), poblacion);
        assertEquals(3, peticiones.size());
    }

    /**
     * Envía rol, corte y paginación del contrato de MS01, la clave de servicio, la correlación y la identidad
     */
    @Test
    void enviaElRolElCorteLaClaveDeServicioLaCorrelacionYLaIdentidad() {
        respuestas = (rol, page) -> new Respuesta(200, activos(0, 1, ANA));
        MDC.put(CorrelationIds.MDC_KEY, "c19b472d-7a2a-4d3b-a12b-abcdef123456");

        cliente().usuariosActivosPorRoles(List.of("PROFESOR", "ESTUDIANTE"), CORTE, AUDITOR);

        HttpExchangeCapturado peticion = peticiones.get(0);
        assertEquals("rol=ESTUDIANTE&creado_antes=2026-11-20T15%3A00%3A00Z&page=0&size=100", peticion.query());
        assertEquals("rol=PROFESOR&creado_antes=2026-11-20T15%3A00%3A00Z&page=0&size=100",
                peticiones.get(1).query());
        assertEquals("clave-de-servicio-ms04", peticion.headers().getFirst("X-Service-Key"));
        assertEquals("c19b472d-7a2a-4d3b-a12b-abcdef123456", peticion.headers().getFirst("X-Correlation-ID"));
        assertEquals(ANA.toString(), peticion.headers().getFirst("X-User-Id"));
        assertEquals("AUDITOR", peticion.headers().getFirst("X-User-Role"));
    }

    /**
     * Sin corte no envía el parámetro de creación
     */
    @Test
    void sinCorteNoEnviaElParametroDeCreacion() {
        respuestas = (rol, page) -> new Respuesta(200, activos(0, 1, ANA));

        cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), null, AUDITOR);

        assertEquals("rol=ESTUDIANTE&page=0&size=100", peticiones.get(0).query());
    }

    /**
     * MS01 entrega también a los usuarios inactivos y MS04 solo cuenta a los activos
     */
    @Test
    void soloCuentaALosUsuariosActivos() {
        respuestas = (rol, page) -> new Respuesta(200,
                pagina(0, 1, usuario(ANA, "ACTIVO"), usuario(BETO, "INACTIVO"), usuario(CARLA, "ACTIVO")));

        Optional<Set<UUID>> poblacion = cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR);

        assertEquals(Optional.of(Set.of(ANA, CARLA)), poblacion);
    }

    /**
     * Un rol repetido en la lista se consulta una sola vez
     */
    @Test
    void unRolRepetidoSePideUnaSolaVez() {
        respuestas = (rol, page) -> new Respuesta(200, activos(0, 1, ANA));

        cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE", "ESTUDIANTE"), CORTE, AUDITOR);

        assertEquals(1, peticiones.size());
    }

    /**
     * Si MS01 responde con error, la población queda como no disponible
     */
    @Test
    void unErrorDeMs01DejaLaPoblacionComoNoDisponible() {
        respuestas = (rol, page) -> new Respuesta(503, "{\"codigo\":\"DEPENDENCIA_NO_DISPONIBLE\"}");

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    /**
     * Si MS01 rechaza la consulta con un 400, la población queda como no disponible
     */
    @Test
    void unaConsultaRechazadaPorMs01DejaLaPoblacionComoNoDisponible() {
        respuestas = (rol, page) -> new Respuesta(400, "{\"codigo\":\"VALIDACION\"}");

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    /**
     * Un cuerpo que no es JSON deja la población como no disponible
     */
    @Test
    void unCuerpoInvalidoDejaLaPoblacionComoNoDisponible() {
        respuestas = (rol, page) -> new Respuesta(200, "no es json");

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    /**
     * Si una página falla, no se entrega una población incompleta
     */
    @Test
    void siUnaPaginaFallaNoSeEntregaUnaPoblacionIncompleta() {
        respuestas = (rol, page) -> {
            if (page == 0) {
                return new Respuesta(200, activos(0, 2, ANA));
            }
            return new Respuesta(500, "{}");
        };

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    /**
     * Si falla la consulta de uno de los roles, no se entrega una población incompleta
     */
    @Test
    void siFallaUnRolNoSeEntregaUnaPoblacionIncompleta() {
        respuestas = (rol, page) -> {
            if ("ESTUDIANTE".equals(rol)) {
                return new Respuesta(200, activos(0, 1, ANA));
            }
            return new Respuesta(500, "{}");
        };

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"), CORTE, AUDITOR).isEmpty());
    }

    /**
     * Sin Gateway configurado la población no está disponible y no se hace ninguna llamada
     */
    @Test
    void sinGatewayConfiguradoLaPoblacionNoEstaDisponible() {
        assertTrue(cliente("").usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
        assertTrue(peticiones.isEmpty());
    }

    /**
     * Un Gateway que no responde deja la población como no disponible
     */
    @Test
    void unGatewayQueNoRespondeDejaLaPoblacionComoNoDisponible() {
        servidor.stop(0);

        assertTrue(cliente().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CORTE, AUDITOR).isEmpty());
    }

    /**
     * Sin roles convocados la población es vacía y no se llama a MS01
     */
    @Test
    void sinRolesLaPoblacionEstaVaciaSinLlamarAMs01() {
        assertEquals(Optional.of(Set.of()), cliente().usuariosActivosPorRoles(List.of(), CORTE, AUDITOR));
        assertTrue(peticiones.isEmpty());
    }
}
