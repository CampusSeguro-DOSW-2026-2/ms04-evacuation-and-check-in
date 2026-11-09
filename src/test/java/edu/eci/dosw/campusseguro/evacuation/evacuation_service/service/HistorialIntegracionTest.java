package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.RegistroHistorial;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * RF-MS04-09 contra PostgreSQL real con el esquema de database/: el histórico es la propia tabla checkins, con
 * todos los reportes de cada usuario en orden cronológico (RN-MS04-08), filtros por usuario y estado,
 * paginación, y funciona con el evento en cualquier estado (RN-MS04-22). Consultar no cambia ningún dato
 * (RN-MS04-17). Necesita Docker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainerConfig.class, HistorialService.class })
class HistorialIntegracionTest {

    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID ESPACIO = UUID.fromString("4e9c7a15-3b2d-4f60-a8e1-7c5d9b3f1a28");
    private static final String T1 = "2026-11-20T15:02:05Z";
    private static final String T2 = "2026-11-20T15:04:12Z";
    private static final String T3 = "2026-11-20T15:06:40Z";
    private static final String T4 = "2026-11-20T15:09:40Z";
    private static final UUID SIN_USUARIO = null;
    private static final EstadoCheckin SIN_ESTADO = null;

    @Autowired
    private HistorialService service;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Crea un evento en el estado indicado, con las fechas que exigen las restricciones del esquema: sin inicio en
     * BORRADOR y con cierre en FINALIZADO
     *
     * @param estado estado del evento
     * @return el evento guardado
     */
    private Evento evento(EstadoEvento estado) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Simulacro de evacuación bloque A");
        evento.setSimulacro(true);
        evento.setEstado(estado);
        if (estado != EstadoEvento.BORRADOR) {
            evento.setFechaInicio(Instant.parse("2026-11-20T15:00:00Z"));
        }
        if (estado == EstadoEvento.FINALIZADO) {
            evento.setFechaCierre(Instant.parse("2026-11-20T16:00:00Z"));
        }
        evento.setRolesConvocados(List.of("ESTUDIANTE"));
        evento.setEscenarioEdificios(List.of("Bloque A"));
        evento.setEscenarioZonas(List.of("Zona norte"));
        return eventos.saveAndFlush(evento);
    }

    /**
     * Agrega un check-in con la fecha de registro indicada, para controlar el orden
     *
     * @param evento id del evento
     * @param usuario id del usuario que reporta
     * @param estado estado reportado
     * @param punto punto de encuentro, solo con SAFE
     * @param tipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacion id de la ubicación, solo con NEEDS_ASSISTANCE
     * @param registradoEn fecha y hora del reporte
     * @return el id del check-in
     */
    private UUID reportar(UUID evento, UUID usuario, EstadoCheckin estado, UUID punto, TipoUbicacion tipo,
            UUID ubicacion, String registradoEn) {
        UUID id = UUID.randomUUID();
        String tipoTexto = null;
        if (tipo != null) {
            tipoTexto = tipo.name();
        }
        jdbc.update("INSERT INTO checkins (id, evento_id, usuario_id, estado, punto_encuentro_id, ubicacion_tipo, "
                + "ubicacion_id, registrado_en) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, evento, usuario, estado.name(),
                punto, tipoTexto, ubicacion, Timestamp.from(Instant.parse(registradoEn)));
        return id;
    }

    /**
     * Agrega un check-in sin punto ni ubicación: EVACUATING u OFF_CAMPUS
     *
     * @param evento id del evento
     * @param usuario id del usuario que reporta
     * @param estado EVACUATING u OFF_CAMPUS
     * @param registradoEn fecha y hora del reporte
     * @return el id del check-in
     */
    private UUID sinUbicacion(UUID evento, UUID usuario, EstadoCheckin estado, String registradoEn) {
        return reportar(evento, usuario, estado, null, null, null, registradoEn);
    }

    /**
     * Ids de los check-ins de una página, en el orden en que llegan
     *
     * @param pagina página del histórico
     * @return los ids de los check-ins
     */
    private List<UUID> ids(PageResponse<RegistroHistorial> pagina) {
        return pagina.contenido().stream().map(RegistroHistorial::checkinId).toList();
    }

    /**
     * RN-MS04-08: el histórico trae todos los reportes de cada usuario, no solo el vigente, en orden
     * cronológico, con su punto de encuentro o ubicación y su fecha
     */
    @Test
    void traeTodosLosCheckinsDelEventoYNoSoloElVigente() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        UUID evacuando = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        UUID asistencia = reportar(evento.getId(), ANA, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO,
                EDIFICIO, T2);
        UUID fuera = sinUbicacion(evento.getId(), LUIS, EstadoCheckin.OFF_CAMPUS, T3);
        UUID aSalvo = reportar(evento.getId(), ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, T4);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(List.of(evacuando, asistencia, fuera, aSalvo), ids(historial));
        assertEquals(4, historial.totalElementos());
        assertEquals(1, historial.totalPaginas());

        RegistroHistorial primero = historial.contenido().get(0);
        assertEquals(ANA, primero.usuarioId());
        assertEquals(EstadoCheckin.EVACUATING, primero.estado());
        assertNull(primero.puntoEncuentroId());
        assertNull(primero.ubicacion());
        assertEquals(Instant.parse(T1), primero.registradoEn());

        RegistroHistorial segundo = historial.contenido().get(1);
        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, segundo.estado());
        assertNull(segundo.puntoEncuentroId());
        assertEquals(new Ubicacion(TipoUbicacion.EDIFICIO, EDIFICIO), segundo.ubicacion());

        RegistroHistorial ultimo = historial.contenido().get(3);
        assertEquals(EstadoCheckin.SAFE, ultimo.estado());
        assertEquals(PUNTO_A, ultimo.puntoEncuentroId());
        assertNull(ultimo.ubicacion());
        assertEquals(Instant.parse(T4), ultimo.registradoEn());
    }

    /**
     * El orden lo fija la fecha de registro y no el orden en que se insertaron las filas
     */
    @Test
    void ordenaPorFechaDeRegistroYNoPorOrdenDeInsercion() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        UUID tercero = sinUbicacion(evento.getId(), ANA, EstadoCheckin.OFF_CAMPUS, T3);
        UUID primero = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        UUID segundo = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T2);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(List.of(primero, segundo, tercero), ids(historial));
    }

    /**
     * Dos check-ins con la misma fecha de registro se desempatan por su número: primero el que llegó antes
     */
    @Test
    void conLaMismaFechaDesempataPorElNumeroDelCheckin() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        UUID llegoPrimero = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        UUID llegoDespues = sinUbicacion(evento.getId(), LUIS, EstadoCheckin.EVACUATING, T1);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(List.of(llegoPrimero, llegoDespues), ids(historial));
    }

    /**
     * El filtro por usuario deja solo los reportes de esa persona, todos y en orden cronológico
     */
    @Test
    void filtraPorUsuario() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        UUID evacuando = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        sinUbicacion(evento.getId(), LUIS, EstadoCheckin.OFF_CAMPUS, T2);
        UUID aSalvo = reportar(evento.getId(), ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, T3);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), ANA, SIN_ESTADO, 0, 20);

        assertEquals(List.of(evacuando, aSalvo), ids(historial));
        assertEquals(2, historial.totalElementos());
        assertTrue(historial.contenido().stream().allMatch(registro -> ANA.equals(registro.usuarioId())));
    }

    /**
     * El filtro por estado deja solo los reportes con ese estado, sea o no el vigente de la persona
     *
     * @param estado estado del filtro
     */
    @ParameterizedTest
    @EnumSource(EstadoCheckin.class)
    void filtraPorCadaEstadoDelCatalogo(EstadoCheckin estado) {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        reportar(evento.getId(), ANA, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.ESPACIO, ESPACIO, T2);
        reportar(evento.getId(), ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, T3);
        sinUbicacion(evento.getId(), LUIS, EstadoCheckin.OFF_CAMPUS, T4);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), SIN_USUARIO, estado, 0, 20);

        assertEquals(1, historial.totalElementos());
        assertEquals(estado, historial.contenido().get(0).estado());
    }

    /**
     * Los filtros de usuario y estado se combinan
     */
    @Test
    void combinaLosFiltrosDeUsuarioYEstado() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        UUID evacuandoLuis = sinUbicacion(evento.getId(), LUIS, EstadoCheckin.EVACUATING, T2);
        reportar(evento.getId(), ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, T3);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), LUIS, EstadoCheckin.EVACUATING,
                0, 20);

        assertEquals(List.of(evacuandoLuis), ids(historial));
        assertEquals(0, service.historial(evento.getId(), LUIS, EstadoCheckin.SAFE, 0, 20).totalElementos());
    }

    /**
     * Un usuario sin check-ins en el evento devuelve la lista vacía, no un error
     */
    @Test
    void unUsuarioSinCheckinsEnElEventoDevuelveLaListaVacia() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), UUID.randomUUID(), SIN_ESTADO,
                0, 20);

        assertEquals(List.of(), historial.contenido());
        assertEquals(0, historial.totalElementos());
        assertEquals(0, historial.totalPaginas());
    }

    /**
     * A4: un evento sin check-ins devuelve la lista vacía, con total 0 y sin páginas
     */
    @Test
    void unEventoSinCheckinsDevuelveLaListaVacia() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(List.of(), historial.contenido());
        assertEquals(0, historial.page());
        assertEquals(20, historial.size());
        assertEquals(0, historial.totalElementos());
        assertEquals(0, historial.totalPaginas());
    }

    /**
     * La paginación recorre el histórico sin repetir ni saltar reportes, y una página más allá de la última viene
     * vacía con el mismo total
     */
    @Test
    void paginaElHistoricoSinRepetirNiSaltarReportes() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        UUID uno = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        UUID dos = sinUbicacion(evento.getId(), LUIS, EstadoCheckin.EVACUATING, T2);
        UUID tres = reportar(evento.getId(), ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, T3);
        UUID cuatro = sinUbicacion(evento.getId(), LUIS, EstadoCheckin.OFF_CAMPUS, T4);
        UUID cinco = sinUbicacion(evento.getId(), ANA, EstadoCheckin.OFF_CAMPUS, "2026-11-20T15:10:00Z");

        PageResponse<RegistroHistorial> primera = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 2);
        PageResponse<RegistroHistorial> segunda = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 1, 2);
        PageResponse<RegistroHistorial> tercera = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 2, 2);
        PageResponse<RegistroHistorial> cuarta = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 3, 2);

        assertEquals(List.of(uno, dos), ids(primera));
        assertEquals(List.of(tres, cuatro), ids(segunda));
        assertEquals(List.of(cinco), ids(tercera));
        assertEquals(List.of(), ids(cuarta));
        assertEquals(5, primera.totalElementos());
        assertEquals(3, primera.totalPaginas());
        assertEquals(5, cuarta.totalElementos());
        assertEquals(3, cuarta.page());
    }

    /**
     * El tamaño máximo de página (100) funciona con un histórico más grande, y la segunda página trae el resto
     */
    @Test
    void conMasDeCienReportesLaPaginaDeCienYLaSiguienteTraenElTotal() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        jdbc.update("INSERT INTO checkins (evento_id, usuario_id, estado, registrado_en) "
                + "SELECT ?, gen_random_uuid(), 'EVACUATING', ?::timestamptz + (g || ' seconds')::interval "
                + "FROM generate_series(1, 120) g", evento.getId(), "2026-11-20T15:00:00Z");

        PageResponse<RegistroHistorial> primera = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 100);
        PageResponse<RegistroHistorial> segunda = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 1, 100);

        assertEquals(100, primera.contenido().size());
        assertEquals(20, segunda.contenido().size());
        assertEquals(120, primera.totalElementos());
        assertEquals(2, primera.totalPaginas());
        assertTrue(primera.contenido().get(99).registradoEn().isBefore(segunda.contenido().get(0).registradoEn()));
    }

    /**
     * El histórico de un evento no incluye los check-ins de otro, aunque sea la misma persona
     */
    @Test
    void noMezclaLosCheckinsDeOtrosEventos() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Evento otro = evento(EstadoEvento.EN_EJECUCION);
        UUID propio = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        sinUbicacion(otro.getId(), ANA, EstadoCheckin.OFF_CAMPUS, T2);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), ANA, SIN_ESTADO, 0, 20);

        assertEquals(List.of(propio), ids(historial));
        assertEquals(1, service.historial(otro.getId(), ANA, SIN_ESTADO, 0, 20).totalElementos());
    }

    /**
     * RN-MS04-22: el histórico se consulta con el evento en cualquier estado, también FINALIZADO
     *
     * @param estado estado del evento
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void funcionaConElEventoEnCualquierEstado(EstadoEvento estado) {
        Evento evento = evento(estado);
        UUID checkin = sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);

        PageResponse<RegistroHistorial> historial = service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(List.of(checkin), ids(historial));
    }

    /**
     * A3: un evento inexistente responde 404
     */
    @Test
    void unEventoInexistenteResponde404() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.historial(UUID.randomUUID(), SIN_USUARIO, SIN_ESTADO, 0, 20));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
    }

    /**
     * RN-MS04-17: consultar el histórico, con o sin filtros, no cambia ninguna fila ni agrega otra
     */
    @Test
    void consultarElHistoricoNoCambiaNingunDato() {
        Evento evento = evento(EstadoEvento.FINALIZADO);
        sinUbicacion(evento.getId(), ANA, EstadoCheckin.EVACUATING, T1);
        reportar(evento.getId(), ANA, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, EDIFICIO, T2);
        reportar(evento.getId(), LUIS, EstadoCheckin.SAFE, PUNTO_A, null, null, T3);
        String foto = "SELECT string_agg(c::text, '|' ORDER BY numero) FROM checkins c WHERE evento_id = ?";
        String antes = jdbc.queryForObject(foto, String.class, evento.getId());

        service.historial(evento.getId(), SIN_USUARIO, SIN_ESTADO, 0, 20);
        service.historial(evento.getId(), ANA, EstadoCheckin.SAFE, 0, 1);
        service.historial(evento.getId(), LUIS, SIN_ESTADO, 5, 100);

        assertEquals(antes, jdbc.queryForObject(foto, String.class, evento.getId()));
    }
}
