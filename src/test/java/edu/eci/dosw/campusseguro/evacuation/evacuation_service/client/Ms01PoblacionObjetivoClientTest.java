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
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import tools.jackson.databind.json.JsonMapper;

/**
 * MS01 client for the target population, against a local HTTP server that plays the role of the Gateway with the
 * contract of RF-MS01-09 (one role per query, active and inactive users)
 */
class Ms01PoblacionObjetivoClientTest {

    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID BETO = UUID.fromString("5a2b7c9d-1e3f-4a5b-8c7d-9e0f1a2b3c4d");
    private static final UUID CARLA = UUID.fromString("6b3c8d0e-2f4a-4b6c-9d8e-0f1a2b3c4d5e");
    private static final Instant CUT = Instant.parse("2026-11-20T15:00:00Z");
    private static final RequesterIdentity AUDITOR = TestIdentities.identity(ANA, "AUDITOR");

    private HttpServer server;
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
    private BiFunction<String, Integer, Answer> answers;

    /**
     * Answer of the local server
     *
     * @param status status of the answer
     * @param body body of the answer
     */
    private record Answer(int status, String body) {
    }

    /**
     * Request received by the local server
     *
     * @param query query string of the request
     * @param headers headers of the request
     */
    private record CapturedRequest(String query, Headers headers) {
    }

    /**
     * Starts the local server on the route of MS01
     *
     * @throws IOException if the server cannot start
     */
    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(Ms01PoblacionObjetivoClient.PATH, this::serve);
        server.start();
    }

    /**
     * Stops the local server and clears the correlation id
     */
    @AfterEach
    void stop() {
        server.stop(0);
        MDC.clear();
    }

    /**
     * Records the request and answers the page configured for its role
     *
     * @param exchange received request
     * @throws IOException if the answer cannot be written
     */
    private void serve(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        requests.add(new CapturedRequest(query, exchange.getRequestHeaders()));
        String role = query.replaceAll("^rol=([A-Z_]+).*", "$1");
        int page = Integer.parseInt(query.replaceAll(".*[?&]page=(\\d+).*", "$1"));
        Answer answer = answers.apply(role, page);
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(answer.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    /**
     * Client pointing to a Gateway
     *
     * @param gatewayUrl URL of the Gateway
     * @return the client
     */
    private Ms01PoblacionObjetivoClient client(String gatewayUrl) {
        return new Ms01PoblacionObjetivoClient(HttpClient.newHttpClient(), JsonMapper.builder().build(),
                gatewayUrl, "clave-de-servicio-ms04", Duration.ofMillis(2000));
    }

    /**
     * Client pointing to the local server
     *
     * @return the client
     */
    private Ms01PoblacionObjetivoClient client() {
        return client("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    /**
     * User of MS01 in a state
     *
     * @param id id of the user
     * @param state state of the user
     * @return the user in JSON
     */
    private static String user(UUID id, String state) {
        return "{\"id\":\"" + id + "\",\"primer_nombre\":\"Ana\",\"estado\":\"" + state + "\"}";
    }

    /**
     * Page of users of MS01
     *
     * @param page number of the page
     * @param totalPages total of pages
     * @param users users in JSON
     * @return the page in JSON
     */
    private static String page(int page, int totalPages, String... users) {
        return "{\"contenido\":[" + String.join(",", users) + "],\"page\":" + page
                + ",\"size\":100,\"total_elementos\":" + users.length + ",\"total_paginas\":"
                + totalPages + "}";
    }

    /**
     * Page of active users of MS01
     *
     * @param page number of the page
     * @param totalPages total of pages
     * @param ids ids of the users
     * @return the page in JSON
     */
    private static String active(int page, int totalPages, UUID... ids) {
        String[] users = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            users[i] = user(ids[i], "ACTIVO");
        }
        return page(page, totalPages, users);
    }

    /**
     * Asks for each role separately, goes through every page and counts once a person with several roles
     */
    @Test
    void goesThroughEveryPageOfEachRoleAndCountsEachPersonOnce() {
        Map<String, String> pages = Map.of(
                "ESTUDIANTE0", active(0, 2, ANA, BETO),
                "ESTUDIANTE1", active(1, 2, CARLA),
                "PROFESOR0", active(0, 1, BETO));
        answers = (role, page) -> new Answer(200, pages.get(role + page));

        Optional<Set<UUID>> population = client().usuariosActivosPorRoles(List.of("PROFESOR", "ESTUDIANTE"),
                CUT, AUDITOR);

        assertEquals(Optional.of(Set.of(ANA, BETO, CARLA)), population);
        assertEquals(3, requests.size());
    }

    /**
     * Sends role, cut and pagination of the MS01 contract, the service key, the correlation and the identity
     */
    @Test
    void sendsRoleCutServiceKeyCorrelationAndIdentity() {
        answers = (role, page) -> new Answer(200, active(0, 1, ANA));
        MDC.put(CorrelationIds.MDC_KEY, "c19b472d-7a2a-4d3b-a12b-abcdef123456");

        client().usuariosActivosPorRoles(List.of("PROFESOR", "ESTUDIANTE"), CUT, AUDITOR);

        CapturedRequest request = requests.get(0);
        assertEquals("rol=ESTUDIANTE&creado_antes=2026-11-20T15%3A00%3A00Z&page=0&size=100", request.query());
        assertEquals("rol=PROFESOR&creado_antes=2026-11-20T15%3A00%3A00Z&page=0&size=100",
                requests.get(1).query());
        assertEquals("clave-de-servicio-ms04", request.headers().getFirst("X-Service-Key"));
        assertEquals("c19b472d-7a2a-4d3b-a12b-abcdef123456", request.headers().getFirst("X-Correlation-ID"));
        assertEquals(ANA.toString(), request.headers().getFirst("X-User-Id"));
        assertEquals("AUDITOR", request.headers().getFirst("X-User-Role"));
        assertEquals(AUDITOR.permisosComoCabecera(), request.headers().getFirst("X-User-Permission"));
    }

    /**
     * Without cut the creation parameter is not sent
     */
    @Test
    void withoutCutTheCreationParameterIsNotSent() {
        answers = (role, page) -> new Answer(200, active(0, 1, ANA));

        client().usuariosActivosPorRoles(List.of("ESTUDIANTE"), null, AUDITOR);

        assertEquals("rol=ESTUDIANTE&page=0&size=100", requests.get(0).query());
    }

    /**
     * MS01 also delivers the inactive users and MS04 only counts the active ones
     */
    @Test
    void onlyTheActiveUsersAreCounted() {
        answers = (role, page) -> new Answer(200,
                page(0, 1, user(ANA, "ACTIVO"), user(BETO, "INACTIVO"), user(CARLA, "ACTIVO")));

        Optional<Set<UUID>> population = client().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CUT, AUDITOR);

        assertEquals(Optional.of(Set.of(ANA, CARLA)), population);
    }

    /**
     * A repeated role in the list is queried only once
     */
    @Test
    void aRepeatedRoleIsQueriedOnce() {
        answers = (role, page) -> new Answer(200, active(0, 1, ANA));

        client().usuariosActivosPorRoles(List.of("ESTUDIANTE", "ESTUDIANTE"), CUT, AUDITOR);

        assertEquals(1, requests.size());
    }

    /**
     * If MS01 answers with an error, the population is unavailable
     */
    @Test
    void anErrorOfMs01LeavesThePopulationUnavailable() {
        answers = (role, page) -> new Answer(503, "{\"codigo\":\"DEPENDENCIA_NO_DISPONIBLE\"}");

        assertTrue(client().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CUT, AUDITOR).isEmpty());
    }

    /**
     * If MS01 rejects the query with a 400, the population is unavailable
     */
    @Test
    void aQueryRejectedByMs01LeavesThePopulationUnavailable() {
        answers = (role, page) -> new Answer(400, "{\"codigo\":\"VALIDACION\"}");

        assertTrue(client().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CUT, AUDITOR).isEmpty());
    }

    /**
     * A body that is not JSON leaves the population unavailable
     */
    @Test
    void anInvalidBodyLeavesThePopulationUnavailable() {
        answers = (role, page) -> new Answer(200, "no es json");

        assertTrue(client().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CUT, AUDITOR).isEmpty());
    }

    /**
     * If a page fails, an incomplete population is not delivered
     */
    @Test
    void aFailedPageDeliversNoIncompletePopulation() {
        answers = (role, page) -> {
            if (page == 0) {
                return new Answer(200, active(0, 2, ANA));
            }
            return new Answer(500, "{}");
        };

        assertTrue(client().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CUT, AUDITOR).isEmpty());
    }

    /**
     * If the query of one of the roles fails, an incomplete population is not delivered
     */
    @Test
    void aFailedRoleDeliversNoIncompletePopulation() {
        answers = (role, page) -> {
            if ("ESTUDIANTE".equals(role)) {
                return new Answer(200, active(0, 1, ANA));
            }
            return new Answer(500, "{}");
        };

        assertTrue(client().usuariosActivosPorRoles(List.of("ESTUDIANTE", "PROFESOR"), CUT, AUDITOR).isEmpty());
    }

    /**
     * Without Gateway configured the population is unavailable and no call is made
     */
    @Test
    void withoutGatewayThePopulationIsUnavailable() {
        assertTrue(client("").usuariosActivosPorRoles(List.of("ESTUDIANTE"), CUT, AUDITOR).isEmpty());
        assertTrue(requests.isEmpty());
    }

    /**
     * A Gateway that does not answer leaves the population unavailable
     */
    @Test
    void aGatewayThatDoesNotAnswerLeavesThePopulationUnavailable() {
        server.stop(0);

        assertTrue(client().usuariosActivosPorRoles(List.of("ESTUDIANTE"), CUT, AUDITOR).isEmpty());
    }

    /**
     * Without convened roles the population is empty and MS01 is not called
     */
    @Test
    void withoutRolesThePopulationIsEmptyWithoutCallingMs01() {
        assertEquals(Optional.of(Set.of()), client().usuariosActivosPorRoles(List.of(), CUT, AUDITOR));
        assertTrue(requests.isEmpty());
    }
}
