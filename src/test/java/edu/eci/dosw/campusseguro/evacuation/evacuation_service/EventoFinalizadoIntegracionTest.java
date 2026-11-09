package edu.eci.dosw.campusseguro.evacuation.evacuation_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeaders;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * RF-MS04-09 Conservar eventos finalizados, de punta a punta con PostgreSQL real: tras el cierre que informa MS06
 * el histórico se entrega completo e idéntico al del cierre, el evento no admite más check-ins ni otras
 * transiciones, y ningún dato del evento se modifica ni se elimina (RN-MS04-17, RNF-MS04-05). Cada prueba se
 * deshace al terminar
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainerConfig.class)
@Transactional
class EventoFinalizadoIntegracionTest {

    private static final String BASE = "/api/v1/chi/eventos/";
    private static final String ESTUDIANTE = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final UUID ANA = UUID.fromString(ESTUDIANTE);
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final String INICIO = "2026-11-20T10:00:00-05:00";
    private static final String COORDINADOR = "6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping rutas;

    @MockitoBean
    private AuditRecorder auditoria;

    private final UUID evento = UUID.randomUUID();

    /**
     * Cuerpo de F-05 con los datos de la prueba
     *
     * @param estado estado del evento
     * @param fechaInicio fecha de inicio, null para no enviarla
     * @return el cuerpo JSON
     */
    private String cuerpo(String estado, String fechaInicio) {
        StringBuilder cuerpo = new StringBuilder();
        cuerpo.append("{\"eventoId\":\"").append(evento).append("\",");
        cuerpo.append("\"nombre\":\"Simulacro de evacuación bloque A\",\"simulacro\":true,");
        cuerpo.append("\"estado\":\"").append(estado).append("\",");
        if (fechaInicio != null) {
            cuerpo.append("\"fechaInicio\":\"").append(fechaInicio).append("\",");
        }
        cuerpo.append("\"escenario\":{\"edificios\":[\"Bloque A\"],\"zonas\":[\"Zona norte\"]},");
        cuerpo.append("\"rolesConvocados\":[\"ESTUDIANTE\",\"PROFESOR\"]}");
        return cuerpo.toString();
    }

    /**
     * Envía el estado del evento como lo hace MS06
     *
     * @param estado estado del evento
     * @param fechaInicio fecha de inicio, null para no enviarla
     * @return el resultado de la petición
     * @throws Exception si falla la petición
     */
    private ResultActions informar(String estado, String fechaInicio) throws Exception {
        return mockMvc.perform(put(BASE + evento + "/estado").header(IdentityHeaders.SERVICE_NAME, "ms06")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(estado, fechaInicio)));
    }

    /**
     * Crea el evento en ejecución y le agrega check-ins de dos personas, uno de ellos con una solicitud de
     * asistencia
     *
     * @throws Exception si falla la petición
     */
    private void eventoEnEjecucionConCheckins() throws Exception {
        informar("EN_EJECUCION", INICIO).andExpect(status().isCreated());
        reportar(ANA, "EVACUATING", null, null, null, "2026-11-20T15:02:05Z");
        UUID asistencia = reportar(ANA, "NEEDS_ASSISTANCE", null, "EDIFICIO", EDIFICIO, "2026-11-20T15:04:12Z");
        reportar(LUIS, "OFF_CAMPUS", null, null, null, "2026-11-20T15:05:00Z");
        reportar(ANA, "SAFE", PUNTO_A, null, null, "2026-11-20T15:09:40Z");
        reportar(LUIS, "EVACUATING", null, null, null, "2026-11-20T15:10:15Z");
        jdbc.update("INSERT INTO solicitudes_asistencia (checkin_id, evento_id, usuario_id, ubicacion_tipo, "
                + "ubicacion_id, solicitud_ms05_id, estado_envio, intentos, ultimo_intento_en, reportada_en) "
                + "VALUES (?, ?, ?, 'EDIFICIO', ?, 'SOL-2026-0042', 'ENVIADA', 1, ?, ?)", asistencia, evento, ANA,
                EDIFICIO, Timestamp.from(Instant.parse("2026-11-20T15:04:13Z")),
                Timestamp.from(Instant.parse("2026-11-20T15:04:12Z")));
    }

    /**
     * Agrega un check-in con la fecha de registro indicada
     *
     * @param usuario usuario que reporta
     * @param estado estado reportado
     * @param punto punto de encuentro, solo con SAFE
     * @param tipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacion id de la ubicación, solo con NEEDS_ASSISTANCE
     * @param registradoEn fecha y hora del reporte
     * @return el id del check-in
     */
    private UUID reportar(UUID usuario, String estado, UUID punto, String tipo, UUID ubicacion, String registradoEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO checkins (id, evento_id, usuario_id, estado, punto_encuentro_id, ubicacion_tipo, "
                + "ubicacion_id, registrado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, evento, usuario, estado, punto,
                tipo, ubicacion, Timestamp.from(Instant.parse(registradoEn)));
        return id;
    }

    /**
     * Consulta el histórico del evento con el rol y la consulta indicados
     *
     * @param rol valor de la cabecera X-User-Role
     * @param consulta parámetros de la consulta, con el signo ? si hay alguno
     * @return el resultado de la petición
     * @throws Exception si falla la petición
     */
    private ResultActions historial(String rol, String consulta) throws Exception {
        return mockMvc.perform(get(BASE + evento + "/historial" + consulta)
                .header(IdentityHeaders.USER_ID, COORDINADOR).header(IdentityHeaders.USER_ROLE, rol));
    }

    /**
     * Cuerpo del histórico completo del evento
     *
     * @return el JSON de la respuesta
     * @throws Exception si falla la petición
     */
    private String historialCompleto() throws Exception {
        return historial("AUDITOR", "?size=100").andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
    }

    /**
     * Copia de las filas de una tabla del evento, para comparar antes y después
     *
     * @param tabla tabla checkins o solicitudes_asistencia
     * @param orden columna por la que se ordenan las filas
     * @return las filas del evento como texto
     */
    private String filas(String tabla, String orden) {
        return jdbc.queryForObject("SELECT coalesce(string_agg(t::text, '|' ORDER BY " + orden + "), '') FROM "
                + tabla + " t WHERE evento_id = ?", String.class, evento);
    }

    /**
     * Fecha de cierre guardada del evento
     *
     * @return la fecha de cierre, null mientras no esté FINALIZADO
     */
    private Instant fechaCierre() {
        Timestamp cierre = jdbc.queryForObject("SELECT fecha_cierre FROM eventos WHERE id = ?", Timestamp.class,
                evento);
        if (cierre == null) {
            return null;
        }
        return cierre.toInstant();
    }

    /**
     * Estado guardado del evento
     *
     * @return el estado del evento
     */
    private String estadoDelEvento() {
        return jdbc.queryForObject("SELECT estado FROM eventos WHERE id = ?", String.class, evento);
    }

    /**
     * El histórico de un evento FINALIZADO se entrega completo e idéntico al que había al cierre
     *
     * @throws Exception si falla la petición
     */
    @Test
    void elHistoricoDeUnEventoFinalizadoEsCompletoEIdenticoAlDelCierre() throws Exception {
        eventoEnEjecucionConCheckins();
        String antes = historialCompleto();

        informar("FINALIZADO", null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FINALIZADO"))
                .andExpect(jsonPath("$.checkinHabilitado").value(false));

        String despues = historialCompleto();
        assertEquals(antes, despues);
        historial("AUDITOR", "?size=100").andExpect(jsonPath("$.total_elementos").value(5))
                .andExpect(jsonPath("$.contenido[0].estado").value("EVACUATING"))
                .andExpect(jsonPath("$.contenido[4].estado").value("EVACUATING"));
    }

    /**
     * El histórico de un evento FINALIZADO admite los filtros de usuario y estado
     *
     * @throws Exception si falla la petición
     */
    @Test
    void elHistoricoDeUnEventoFinalizadoSeFiltraPorUsuarioYEstado() throws Exception {
        eventoEnEjecucionConCheckins();
        informar("FINALIZADO", null).andExpect(status().isOk());

        historial("COORDINADOR", "?usuario_id=" + ANA).andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(3))
                .andExpect(jsonPath("$.contenido[0].estado").value("EVACUATING"))
                .andExpect(jsonPath("$.contenido[1].estado").value("NEEDS_ASSISTANCE"))
                .andExpect(jsonPath("$.contenido[1].ubicacion.tipo").value("EDIFICIO"))
                .andExpect(jsonPath("$.contenido[1].ubicacion.id").value(EDIFICIO.toString()))
                .andExpect(jsonPath("$.contenido[2].estado").value("SAFE"))
                .andExpect(jsonPath("$.contenido[2].punto_encuentro_id").value(PUNTO_A.toString()));
        historial("ORGANIZADOR", "?estado=EVACUATING").andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(2));
        historial("ORGANIZADOR", "?usuario_id=" + LUIS + "&estado=OFF_CAMPUS").andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elementos").value(1));
    }

    /**
     * Repetir FINALIZADO no cambia la fecha de cierre ni repite la operación (A1)
     *
     * @throws Exception si falla la petición
     */
    @Test
    void repetirFinalizadoNoCambiaLaFechaDeCierre() throws Exception {
        eventoEnEjecucionConCheckins();
        assertEquals(null, fechaCierre());
        informar("FINALIZADO", null).andExpect(status().isOk());
        Instant cierre = fechaCierre();
        assertNotNull(cierre);

        informar("FINALIZADO", null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("FINALIZADO"));

        assertEquals(cierre, fechaCierre());
        assertEquals("FINALIZADO", estadoDelEvento());
    }

    /**
     * Un check-in en un evento FINALIZADO devuelve 409 y no agrega nada al histórico
     *
     * @throws Exception si falla la petición
     */
    @Test
    void unCheckinEnUnEventoFinalizadoDevuelve409YNoAgregaNada() throws Exception {
        eventoEnEjecucionConCheckins();
        informar("FINALIZADO", null).andExpect(status().isOk());
        String antes = historialCompleto();
        String filasAntes = filas("checkins", "numero");

        mockMvc.perform(post(BASE + evento + "/checkins").header(IdentityHeaders.USER_ID, ESTUDIANTE)
                .header(IdentityHeaders.USER_ROLE, "ESTUDIANTE").contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EVACUATING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));

        assertEquals(antes, historialCompleto());
        assertEquals(filasAntes, filas("checkins", "numero"));
    }

    /**
     * Un evento FINALIZADO no admite ninguna otra transición: responde 409 y no cambia su estado ni su cierre
     *
     * @param estado estado que se intenta enviar después de FINALIZADO
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = { "EN_EJECUCION", "PROGRAMADO", "BORRADOR" })
    void unEventoFinalizadoNoAdmiteOtraTransicion(String estado) throws Exception {
        eventoEnEjecucionConCheckins();
        informar("FINALIZADO", null).andExpect(status().isOk());
        Instant cierre = fechaCierre();
        String fechaInicio = null;
        if (!"BORRADOR".equals(estado)) {
            fechaInicio = INICIO;
        }

        informar(estado, fechaInicio).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLICTO"));

        assertEquals("FINALIZADO", estadoDelEvento());
        assertEquals(cierre, fechaCierre());
    }

    /**
     * Las solicitudes de asistencia del evento se conservan idénticas al cerrarlo (RN-MS04-17)
     *
     * @throws Exception si falla la petición
     */
    @Test
    void lasSolicitudesAsociadasSeConservanIdenticasAlCierre() throws Exception {
        eventoEnEjecucionConCheckins();
        String antes = filas("solicitudes_asistencia", "id");
        assertFalse(antes.isEmpty());
        String checkinsAntes = filas("checkins", "numero");

        informar("FINALIZADO", null).andExpect(status().isOk());
        informar("FINALIZADO", null).andExpect(status().isOk());

        assertEquals(antes, filas("solicitudes_asistencia", "id"));
        assertEquals(checkinsAntes, filas("checkins", "numero"));
    }

    /**
     * La base de datos no deja modificar ni eliminar los check-ins de un evento FINALIZADO, ni eliminar el evento
     * que los tiene
     *
     * @param sentencia sentencia que se intenta ejecutar sobre el evento
     * @throws Exception si falla la petición
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE checkins SET estado = 'OFF_CAMPUS', punto_encuentro_id = NULL WHERE evento_id = ?",
            "UPDATE checkins SET registrado_en = now() WHERE evento_id = ?",
            "DELETE FROM checkins WHERE evento_id = ?",
            "DELETE FROM eventos WHERE id = ?" })
    void laBaseDeDatosNoDejaModificarNiEliminarLosDatosDeUnEventoFinalizado(String sentencia) throws Exception {
        eventoEnEjecucionConCheckins();
        informar("FINALIZADO", null).andExpect(status().isOk());

        assertThrows(DataAccessException.class, () -> jdbc.update(sentencia, evento));
    }

    /**
     * Los roles con ms04:history:read consultan el histórico de un evento FINALIZADO; los demás, incluido el
     * BRIGADISTA, reciben 403
     *
     * @throws Exception si falla la petición
     */
    @Test
    void soloLosRolesConElPermisoConsultanElHistoricoDeUnEventoFinalizado() throws Exception {
        eventoEnEjecucionConCheckins();
        informar("FINALIZADO", null).andExpect(status().isOk());

        for (String rol : List.of("COORDINADOR", "ORGANIZADOR", "AUDITOR")) {
            historial(rol, "").andExpect(status().isOk()).andExpect(jsonPath("$.total_elementos").value(5));
        }
        for (String rol : List.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO", "BRIGADISTA", "ADMIN")) {
            historial(rol, "").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        }
    }

    /**
     * A4 y A3: un evento sin check-ins responde la lista vacía y un evento que no existe responde 404
     *
     * @throws Exception si falla la petición
     */
    @Test
    void elHistoricoSinCheckinsEsVacioYUnEventoInexistenteEs404() throws Exception {
        informar("EN_EJECUCION", INICIO).andExpect(status().isCreated());

        historial("COORDINADOR", "").andExpect(status().isOk()).andExpect(jsonPath("$.total_elementos").value(0))
                .andExpect(jsonPath("$.total_paginas").value(0));
        mockMvc.perform(get(BASE + UUID.randomUUID() + "/historial").header(IdentityHeaders.USER_ID, COORDINADOR)
                .header(IdentityHeaders.USER_ROLE, "COORDINADOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    /**
     * MS04 no expone operaciones de modificación ni de eliminación de check-ins (RNF-MS04-05): ninguna ruta usa
     * DELETE ni PATCH, y las únicas escrituras son el estado del evento de MS06 (PUT) y el registro de un check-in
     * nuevo (POST)
     */
    @Test
    void noHayRutasQueModifiquenOEliminenCheckins() {
        Set<String> escrituras = new TreeSet<>();
        for (RequestMappingInfo ruta : rutas.getHandlerMethods().keySet()) {
            Set<HttpMethod> metodos = new HashSet<>();
            for (RequestMethod metodo : ruta.getMethodsCondition()
                    .getMethods()) {
                metodos.add(HttpMethod.valueOf(metodo.name()));
            }
            assertFalse(metodos.contains(HttpMethod.DELETE), "Ruta con DELETE: " + ruta);
            assertFalse(metodos.contains(HttpMethod.PATCH), "Ruta con PATCH: " + ruta);
            if (metodos.contains(HttpMethod.PUT) || metodos.contains(HttpMethod.POST)) {
                List<String> patrones = new ArrayList<>(ruta.getPathPatternsCondition().getPatternValues());
                escrituras.add(metodos + " " + patrones);
            }
        }

        assertTrue(escrituras.contains("[PUT] [/api/v1/chi/eventos/{eventoId}/estado]"), escrituras.toString());
        assertTrue(escrituras.contains("[POST] [/api/v1/chi/eventos/{eventoId}/checkins]"), escrituras.toString());
        assertEquals(2, escrituras.size(), escrituras.toString());
    }
}
