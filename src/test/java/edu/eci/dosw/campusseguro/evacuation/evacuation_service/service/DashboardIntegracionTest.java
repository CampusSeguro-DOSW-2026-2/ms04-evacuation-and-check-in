package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResumen;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Pendiente;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;
import jakarta.persistence.EntityManager;

/**
 * RF-MS04-06 y RF-MS04-07 contra PostgreSQL real con el esquema de database/: el consolidado, el listado y los
 * pendientes salen de la vista v_checkins_vigentes (un solo estado por persona), los totales cumplen RN-MS04-13, el
 * BRIGADISTA solo ve sus zonas (RN-MS04-14) y consultar no cambia ningún dato. Necesita Docker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainerConfig.class, DashboardService.class, DashboardIntegracionTest.RelojFijo.class })
class DashboardIntegracionTest {

    private static final Instant CALCULADO = Instant.parse("2026-11-20T15:10:00Z");
    private static final UUID BRIGADISTA = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID PUNTO_B = UUID.fromString("d3f5a7c9-1b2e-4d6f-8a0c-4e6b8d0f2a41");
    private static final UUID EDIFICIO_1 = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID EDIFICIO_2 = UUID.fromString("9e2c4a6b-8d0f-4b13-a5c7-1d3f5b7a9c02");
    private static final UUID ESPACIO_1 = UUID.fromString("4e9c7a15-3b2d-4f60-a8e1-7c5d9b3f1a28");

    /** Reloj fijo para comprobar la fecha del cálculo */
    @TestConfiguration(proxyBeanMethods = false)
    static class RelojFijo {

        /**
         * Reloj que siempre marca la misma hora
         *
         * @return el reloj fijo en UTC
         */
        @Bean
        Clock clock() {
            return Clock.fixed(CALCULADO, ZoneOffset.UTC);
        }
    }

    @Autowired
    private DashboardService service;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private CheckinRepository checkins;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UsuarioNombresClient nombres;

    @MockitoBean
    private ZonasBrigadistaClient zonasBrigadista;

    @MockitoBean
    private PoblacionObjetivoClient poblacionObjetivo;

    private final RequesterIdentity coordinador = new RequesterIdentity(UUID.randomUUID(),
            EnumSet.of(Rol.COORDINADOR));
    private final RequesterIdentity brigadista = new RequesterIdentity(BRIGADISTA, EnumSet.of(Rol.BRIGADISTA));

    /**
     * Los nombres que entrega MS01 son "Usuario " más el inicio del id, para comprobar que se asocian a la persona
     * correcta
     */
    @BeforeEach
    void simularNombresDeMs01() {
        when(nombres.nombresDe(any())).thenAnswer(invocacion -> {
            Collection<UUID> ids = invocacion.getArgument(0);
            Map<UUID, String> resultado = new HashMap<>();
            for (UUID id : ids) {
                resultado.put(id, "Usuario " + id.toString().substring(0, 8));
            }
            return resultado;
        });
    }

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
     * Agrega un check-in y lo envía a la base para que asigne su número y su fecha de registro
     *
     * @param evento id del evento
     * @param usuario id del usuario que reporta
     * @param estado estado reportado
     * @param punto punto de encuentro, solo con SAFE
     * @param tipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacion id de la ubicación, solo con NEEDS_ASSISTANCE
     * @return el check-in guardado
     */
    private Checkin reportar(UUID evento, UUID usuario, EstadoCheckin estado, UUID punto, TipoUbicacion tipo,
            UUID ubicacion) {
        Checkin checkin = checkins.save(new Checkin(evento, usuario, estado, punto, tipo, ubicacion));
        entityManager.flush();
        return checkin;
    }

    /**
     * Agrega un check-in SAFE en el punto de encuentro
     *
     * @param evento id del evento
     * @param usuario id del usuario que reporta
     * @param punto punto de encuentro
     * @return el check-in guardado
     */
    private Checkin aSalvo(UUID evento, UUID usuario, UUID punto) {
        return reportar(evento, usuario, EstadoCheckin.SAFE, punto, null, null);
    }

    /**
     * Agrega un check-in sin ubicación ni punto: EVACUATING u OFF_CAMPUS
     *
     * @param evento id del evento
     * @param usuario id del usuario que reporta
     * @param estado EVACUATING u OFF_CAMPUS
     * @return el check-in guardado
     */
    private Checkin sinUbicacion(UUID evento, UUID usuario, EstadoCheckin estado) {
        return reportar(evento, usuario, estado, null, null, null);
    }

    /**
     * Agrega un check-in NEEDS_ASSISTANCE con la ubicación indicada
     *
     * @param evento id del evento
     * @param usuario id del usuario que reporta
     * @param tipo tipo de ubicación
     * @param ubicacion id de la ubicación en MS02
     * @return el check-in guardado
     */
    private Checkin requiereAsistencia(UUID evento, UUID usuario, TipoUbicacion tipo, UUID ubicacion) {
        return reportar(evento, usuario, EstadoCheckin.NEEDS_ASSISTANCE, null, tipo, ubicacion);
    }

    /**
     * Cuenta los check-ins de la base, de cualquier evento
     *
     * @return el total de check-ins
     */
    private long contarCheckins() {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM checkins").getSingleResult())
                .longValue();
    }

    /**
     * Genera un id de usuario cuyo inicio permite reconocerlo en los nombres simulados
     *
     * @param numero número que se usa como inicio del id
     * @return el id del usuario
     */
    private UUID usuario(int numero) {
        return UUID.fromString(String.format("%08x-1111-4111-8111-111111111111", numero));
    }

    // ---------------------------------------------------------------- consolidado

    /**
     * Cuenta solo el estado vigente de cada persona y agrupa por punto de encuentro a quienes están a salvo:
     * la suma de los cuatro estados es el número de personas con estado vigente (RN-MS04-13)
     */
    @Test
    void consolidadoCuentaElEstadoVigenteDeCadaPersonaYAgrupaLosSafePorPunto() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        sinUbicacion(evento.getId(), usuario(1), EstadoCheckin.EVACUATING);
        aSalvo(evento.getId(), usuario(2), PUNTO_A);
        aSalvo(evento.getId(), usuario(3), PUNTO_A);
        aSalvo(evento.getId(), usuario(4), PUNTO_B);
        requiereAsistencia(evento.getId(), usuario(5), TipoUbicacion.EDIFICIO, EDIFICIO_1);
        sinUbicacion(evento.getId(), usuario(6), EstadoCheckin.OFF_CAMPUS);
        sinUbicacion(evento.getId(), usuario(7), EstadoCheckin.EVACUATING);

        Consolidado consolidado = service.consolidado(evento.getId(), coordinador);

        assertEquals(evento.getId(), consolidado.eventoId());
        assertEquals(EstadoEvento.EN_EJECUCION, consolidado.estadoEvento());
        assertEquals(3, consolidado.totalesPorEstado().safe());
        assertEquals(2, consolidado.totalesPorEstado().evacuating());
        assertEquals(1, consolidado.totalesPorEstado().needsAssistance());
        assertEquals(1, consolidado.totalesPorEstado().offCampus());
        assertEquals(7, consolidado.totalReportados());
        assertEquals(List.of(new TotalPorPunto(PUNTO_A, 2), new TotalPorPunto(PUNTO_B, 1)),
                consolidado.porPuntoEncuentro());
        assertEquals(CALCULADO, consolidado.calculadoEn());
        Number personas = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(DISTINCT usuario_id) FROM checkins WHERE evento_id = :evento")
                .setParameter("evento", evento.getId()).getSingleResult();
        assertEquals(personas.longValue(), consolidado.totalReportados());
    }

    /**
     * Cuando una persona cambia de estado pasa de un total a otro sin contarse dos veces
     */
    @Test
    void consolidadoMueveALaPersonaDeUnTotalAOtroSinContarlaDosVeces() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        Consolidado antes = service.consolidado(evento.getId(), coordinador);

        sinUbicacion(evento.getId(), usuario(1), EstadoCheckin.OFF_CAMPUS);
        Consolidado despues = service.consolidado(evento.getId(), coordinador);
        aSalvo(evento.getId(), usuario(1), PUNTO_B);
        Consolidado alVolver = service.consolidado(evento.getId(), coordinador);

        assertEquals(1, antes.totalesPorEstado().safe());
        assertEquals(List.of(new TotalPorPunto(PUNTO_A, 1)), antes.porPuntoEncuentro());
        assertEquals(0, despues.totalesPorEstado().safe());
        assertEquals(1, despues.totalesPorEstado().offCampus());
        assertTrue(despues.porPuntoEncuentro().isEmpty());
        assertEquals(1, despues.totalReportados());
        assertEquals(1, alVolver.totalesPorEstado().safe());
        assertEquals(0, alVolver.totalesPorEstado().offCampus());
        assertEquals(List.of(new TotalPorPunto(PUNTO_B, 1)), alVolver.porPuntoEncuentro());
        assertEquals(1, alVolver.totalReportados());
    }

    /**
     * No mezcla los check-ins de otro evento
     */
    @Test
    void consolidadoNoMezclaLosCheckinsDeOtroEvento() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Evento otro = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        aSalvo(otro.getId(), usuario(2), PUNTO_A);
        sinUbicacion(otro.getId(), usuario(3), EstadoCheckin.EVACUATING);

        Consolidado consolidado = service.consolidado(evento.getId(), coordinador);

        assertEquals(1, consolidado.totalReportados());
        assertEquals(1, consolidado.totalesPorEstado().safe());
        assertEquals(0, consolidado.totalesPorEstado().evacuating());
    }

    /**
     * Un evento sin check-ins devuelve los cuatro estados en 0
     */
    @Test
    void consolidadoDeUnEventoSinCheckinsDevuelveCeros() {
        Evento evento = evento(EstadoEvento.PROGRAMADO);

        Consolidado consolidado = service.consolidado(evento.getId(), coordinador);

        assertEquals(EstadoEvento.PROGRAMADO, consolidado.estadoEvento());
        assertEquals(0, consolidado.totalesPorEstado().total());
        assertEquals(0, consolidado.totalReportados());
        assertTrue(consolidado.porPuntoEncuentro().isEmpty());
    }

    /**
     * El consolidado de un evento FINALIZADO sigue disponible (RN-MS04-22)
     */
    @Test
    void consolidadoDeUnEventoFinalizadoSigueDisponible() {
        Evento evento = evento(EstadoEvento.FINALIZADO);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);

        Consolidado consolidado = service.consolidado(evento.getId(), coordinador);

        assertEquals(EstadoEvento.FINALIZADO, consolidado.estadoEvento());
        assertEquals(1, consolidado.totalReportados());
    }

    /**
     * Consultar el consolidado no agrega, modifica ni borra check-ins
     */
    @Test
    void consolidarNoCambiaNingunDato() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        long antes = contarCheckins();

        service.consolidado(evento.getId(), coordinador);
        service.personas(evento.getId(), null, null, 0, 20, coordinador);

        assertEquals(antes, contarCheckins());
    }

    /**
     * El BRIGADISTA solo cuenta SAFE en puntos de sus zonas y NEEDS_ASSISTANCE en edificios, espacios o puntos de
     * sus zonas, con el tipo de ubicación que corresponde; EVACUATING y OFF_CAMPUS salen en 0
     */
    @Test
    void consolidadoDelBrigadistaCuentaSoloSusZonas() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        aSalvo(evento.getId(), usuario(2), PUNTO_A);
        aSalvo(evento.getId(), usuario(3), PUNTO_B);
        requiereAsistencia(evento.getId(), usuario(4), TipoUbicacion.EDIFICIO, EDIFICIO_1);
        requiereAsistencia(evento.getId(), usuario(5), TipoUbicacion.ESPACIO, ESPACIO_1);
        requiereAsistencia(evento.getId(), usuario(6), TipoUbicacion.PUNTO_ENCUENTRO, PUNTO_A);
        requiereAsistencia(evento.getId(), usuario(7), TipoUbicacion.EDIFICIO, EDIFICIO_2);
        requiereAsistencia(evento.getId(), usuario(8), TipoUbicacion.ESPACIO, EDIFICIO_1);
        sinUbicacion(evento.getId(), usuario(9), EstadoCheckin.EVACUATING);
        sinUbicacion(evento.getId(), usuario(10), EstadoCheckin.OFF_CAMPUS);
        when(zonasBrigadista.zonasDe(brigadista))
                .thenReturn(new ZonasAsignadas(Set.of(EDIFICIO_1), Set.of(ESPACIO_1), Set.of(PUNTO_A)));

        Consolidado consolidado = service.consolidado(evento.getId(), brigadista);

        assertEquals(2, consolidado.totalesPorEstado().safe());
        assertEquals(3, consolidado.totalesPorEstado().needsAssistance());
        assertEquals(0, consolidado.totalesPorEstado().evacuating());
        assertEquals(0, consolidado.totalesPorEstado().offCampus());
        assertEquals(5, consolidado.totalReportados());
        assertEquals(List.of(new TotalPorPunto(PUNTO_A, 2)), consolidado.porPuntoEncuentro());
    }

    /**
     * Un BRIGADISTA sin zonas asignadas recibe los cuatro estados en 0
     */
    @Test
    void consolidadoDelBrigadistaSinZonasDevuelveCeros() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        requiereAsistencia(evento.getId(), usuario(2), TipoUbicacion.EDIFICIO, EDIFICIO_1);
        when(zonasBrigadista.zonasDe(brigadista)).thenReturn(new ZonasAsignadas(Set.of(), Set.of(), Set.of()));

        Consolidado consolidado = service.consolidado(evento.getId(), brigadista);

        assertEquals(0, consolidado.totalReportados());
        assertTrue(consolidado.porPuntoEncuentro().isEmpty());
    }

    // ---------------------------------------------------------------- personas

    /**
     * Lista una persona una sola vez con su estado vigente, la más reciente primero, con el nombre de MS01
     */
    @Test
    void personasListaElEstadoVigenteDelMasRecienteAlMasAntiguo() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        sinUbicacion(evento.getId(), usuario(2), EstadoCheckin.OFF_CAMPUS);
        Checkin vigenteDeAna = sinUbicacion(evento.getId(), usuario(1), EstadoCheckin.EVACUATING);
        Checkin ultimo = aSalvo(evento.getId(), usuario(3), PUNTO_B);

        PageResponse<CheckinResumen> pagina = service.personas(evento.getId(), null, null, 0, 20, coordinador);

        assertEquals(3, pagina.totalElementos());
        assertEquals(List.of(usuario(3), usuario(1), usuario(2)),
                pagina.contenido().stream().map(CheckinResumen::usuarioId).toList());
        assertEquals(EstadoCheckin.EVACUATING, pagina.contenido().get(1).estado());
        assertEquals(PUNTO_B, pagina.contenido().get(0).puntoEncuentroId());
        assertEquals(ultimo.getRegistradoEn(), pagina.contenido().get(0).fechaHora());
        assertEquals(vigenteDeAna.getRegistradoEn(), pagina.contenido().get(1).fechaHora());
        assertEquals("Usuario 00000003", pagina.contenido().get(0).nombre());
        assertEquals("Usuario 00000001", pagina.contenido().get(1).nombre());
    }

    /**
     * Pagina la lista: el tamaño recorta cada página y los totales cuentan todas las personas
     */
    @Test
    void personasPaginaLaLista() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        for (int i = 1; i <= 5; i++) {
            aSalvo(evento.getId(), usuario(i), PUNTO_A);
        }

        PageResponse<CheckinResumen> primera = service.personas(evento.getId(), null, null, 0, 2, coordinador);
        PageResponse<CheckinResumen> ultima = service.personas(evento.getId(), null, null, 2, 2, coordinador);
        PageResponse<CheckinResumen> fuera = service.personas(evento.getId(), null, null, 9, 2, coordinador);

        assertEquals(List.of(usuario(5), usuario(4)),
                primera.contenido().stream().map(CheckinResumen::usuarioId).toList());
        assertEquals(List.of(usuario(1)), ultima.contenido().stream().map(CheckinResumen::usuarioId).toList());
        assertEquals(5, primera.totalElementos());
        assertEquals(3, primera.totalPaginas());
        assertTrue(fuera.contenido().isEmpty());
        assertEquals(5, fuera.totalElementos());
    }

    /**
     * Filtra por estado vigente: quien cambió de estado ya no aparece con el anterior
     *
     * @param estado estado por el que se filtra
     */
    @ParameterizedTest
    @EnumSource(EstadoCheckin.class)
    void personasFiltraPorEstadoVigente(EstadoCheckin estado) {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        requiereAsistencia(evento.getId(), usuario(2), TipoUbicacion.EDIFICIO, EDIFICIO_1);
        sinUbicacion(evento.getId(), usuario(3), EstadoCheckin.EVACUATING);
        sinUbicacion(evento.getId(), usuario(4), EstadoCheckin.OFF_CAMPUS);
        sinUbicacion(evento.getId(), usuario(5), EstadoCheckin.OFF_CAMPUS);
        aSalvo(evento.getId(), usuario(5), PUNTO_B);

        PageResponse<CheckinResumen> pagina = service.personas(evento.getId(), estado, null, 0, 20, coordinador);

        assertTrue(pagina.contenido().stream().allMatch(persona -> persona.estado() == estado));
        long esperados = switch (estado) {
            case SAFE -> 2;
            case NEEDS_ASSISTANCE -> 1;
            case EVACUATING -> 1;
            case OFF_CAMPUS -> 1;
        };
        assertEquals(esperados, pagina.totalElementos());
    }

    /**
     * Filtra por punto de encuentro, con SAFE o sin estado, y por SAFE en un punto
     */
    @Test
    void personasFiltraPorPuntoDeEncuentro() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        aSalvo(evento.getId(), usuario(2), PUNTO_B);
        aSalvo(evento.getId(), usuario(3), PUNTO_A);
        sinUbicacion(evento.getId(), usuario(4), EstadoCheckin.EVACUATING);

        PageResponse<CheckinResumen> sinEstado = service.personas(evento.getId(), null, PUNTO_A, 0, 20, coordinador);
        PageResponse<CheckinResumen> conSafe = service.personas(evento.getId(), EstadoCheckin.SAFE, PUNTO_A, 0, 20,
                coordinador);

        assertEquals(List.of(usuario(3), usuario(1)),
                sinEstado.contenido().stream().map(CheckinResumen::usuarioId).toList());
        assertEquals(sinEstado.contenido(), conSafe.contenido());
        assertEquals(2, conSafe.totalElementos());
    }

    /**
     * El BRIGADISTA solo lista las personas de sus zonas y nunca EVACUATING ni OFF_CAMPUS
     */
    @Test
    void personasDelBrigadistaListaSoloSusZonas() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        aSalvo(evento.getId(), usuario(2), PUNTO_B);
        requiereAsistencia(evento.getId(), usuario(3), TipoUbicacion.EDIFICIO, EDIFICIO_1);
        requiereAsistencia(evento.getId(), usuario(4), TipoUbicacion.EDIFICIO, EDIFICIO_2);
        sinUbicacion(evento.getId(), usuario(5), EstadoCheckin.EVACUATING);
        sinUbicacion(evento.getId(), usuario(6), EstadoCheckin.OFF_CAMPUS);
        when(zonasBrigadista.zonasDe(brigadista))
                .thenReturn(new ZonasAsignadas(Set.of(EDIFICIO_1), Set.of(), Set.of(PUNTO_A)));

        PageResponse<CheckinResumen> todas = service.personas(evento.getId(), null, null, 0, 20, brigadista);
        PageResponse<CheckinResumen> evacuando = service.personas(evento.getId(), EstadoCheckin.EVACUATING, null, 0,
                20, brigadista);
        PageResponse<CheckinResumen> otroPunto = service.personas(evento.getId(), EstadoCheckin.SAFE, PUNTO_B, 0,
                20, brigadista);

        assertEquals(List.of(usuario(3), usuario(1)),
                todas.contenido().stream().map(CheckinResumen::usuarioId).toList());
        assertEquals(2, todas.totalElementos());
        assertTrue(evacuando.contenido().isEmpty());
        assertEquals(0, evacuando.totalElementos());
        assertTrue(otroPunto.contenido().isEmpty());
    }

    /**
     * Un evento sin check-ins devuelve una lista vacía
     */
    @Test
    void personasDeUnEventoSinCheckinsDevuelveUnaListaVacia() {
        Evento evento = evento(EstadoEvento.PROGRAMADO);

        PageResponse<CheckinResumen> pagina = service.personas(evento.getId(), null, null, 0, 20, coordinador);

        assertTrue(pagina.contenido().isEmpty());
        assertEquals(0, pagina.totalElementos());
        assertEquals(0, pagina.totalPaginas());
    }

    // ---------------------------------------------------------------- pendientes

    /**
     * Simula la población objetivo que entrega MS01 para los roles del evento
     *
     * @param usuarios usuarios activos con un rol convocado
     */
    private void poblacion(UUID... usuarios) {
        when(poblacionObjetivo.usuariosActivosPorRoles(eq(List.of("ESTUDIANTE")), any(Instant.class),
                eq(coordinador))).thenReturn(Optional.of(Set.of(usuarios)));
    }

    /**
     * Quien tiene cualquiera de los cuatro estados vigentes deja de ser pendiente, y la población se reparte entre
     * pendientes y reportados sin perder a nadie
     */
    @Test
    void pendientesExcluyenALosQueTienenCualquieraDeLosCuatroEstados() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        poblacion(usuario(1), usuario(2), usuario(3), usuario(4), usuario(5), usuario(6));
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        sinUbicacion(evento.getId(), usuario(2), EstadoCheckin.EVACUATING);
        requiereAsistencia(evento.getId(), usuario(3), TipoUbicacion.EDIFICIO, EDIFICIO_1);
        sinUbicacion(evento.getId(), usuario(4), EstadoCheckin.OFF_CAMPUS);

        PageResponse<Pendiente> pendientes = service.pendientes(evento.getId(), 0, 20, coordinador);
        Consolidado consolidado = service.consolidado(evento.getId(), coordinador);

        assertEquals(List.of(usuario(5), usuario(6)),
                pendientes.contenido().stream().map(Pendiente::usuarioId).toList());
        assertEquals("Usuario 00000005", pendientes.contenido().get(0).nombre());
        assertEquals(2, pendientes.totalElementos());
        assertEquals(6, pendientes.totalElementos() + consolidado.totalReportados());
    }

    /**
     * Quien cambia de estado varias veces sigue sin ser pendiente y no altera la lista
     */
    @Test
    void quienCambiaDeEstadoSigueSinSerPendiente() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        poblacion(usuario(1), usuario(2));
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        sinUbicacion(evento.getId(), usuario(1), EstadoCheckin.EVACUATING);
        sinUbicacion(evento.getId(), usuario(1), EstadoCheckin.OFF_CAMPUS);

        PageResponse<Pendiente> pendientes = service.pendientes(evento.getId(), 0, 20, coordinador);

        assertEquals(List.of(usuario(2)), pendientes.contenido().stream().map(Pendiente::usuarioId).toList());
    }

    /**
     * Al reportar un estado la persona deja de aparecer como pendiente
     */
    @Test
    void alReportarLaPersonaDejaDeSerPendiente() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        poblacion(usuario(1), usuario(2));

        PageResponse<Pendiente> antes = service.pendientes(evento.getId(), 0, 20, coordinador);
        sinUbicacion(evento.getId(), usuario(2), EstadoCheckin.OFF_CAMPUS);
        PageResponse<Pendiente> despues = service.pendientes(evento.getId(), 0, 20, coordinador);

        assertEquals(2, antes.totalElementos());
        assertEquals(List.of(usuario(1)), despues.contenido().stream().map(Pendiente::usuarioId).toList());
    }

    /**
     * Los check-ins de otro evento no cuentan: el usuario sigue pendiente en este
     */
    @Test
    void losCheckinsDeOtroEventoNoQuitanPendientes() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Evento otro = evento(EstadoEvento.EN_EJECUCION);
        poblacion(usuario(1), usuario(2));
        aSalvo(otro.getId(), usuario(1), PUNTO_A);
        aSalvo(evento.getId(), usuario(2), PUNTO_A);

        PageResponse<Pendiente> pendientes = service.pendientes(evento.getId(), 0, 20, coordinador);

        assertEquals(List.of(usuario(1)), pendientes.contenido().stream().map(Pendiente::usuarioId).toList());
    }

    /**
     * El repositorio entrega a cada usuario con estado vigente una sola vez, aunque tenga varios check-ins, y solo
     * los del evento pedido
     */
    @Test
    void elRepositorioEntregaUnaVezACadaUsuarioConEstadoVigente() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Evento otro = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        sinUbicacion(evento.getId(), usuario(1), EstadoCheckin.EVACUATING);
        sinUbicacion(evento.getId(), usuario(1), EstadoCheckin.OFF_CAMPUS);
        aSalvo(evento.getId(), usuario(2), PUNTO_B);
        aSalvo(otro.getId(), usuario(3), PUNTO_B);

        List<UUID> usuarios = checkins.buscarUsuariosConEstadoVigente(evento.getId());

        assertEquals(Set.of(usuario(1), usuario(2)), Set.copyOf(usuarios));
        assertEquals(2, usuarios.size());
        assertTrue(checkins.buscarUsuariosConEstadoVigente(UUID.randomUUID()).isEmpty());
    }

    /**
     * Pagina los pendientes ordenados por id con el total de toda la lista
     */
    @Test
    void pendientesSePaginanOrdenadosPorId() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        poblacion(usuario(5), usuario(3), usuario(1), usuario(4), usuario(2));
        aSalvo(evento.getId(), usuario(4), PUNTO_A);

        PageResponse<Pendiente> primera = service.pendientes(evento.getId(), 0, 2, coordinador);
        PageResponse<Pendiente> segunda = service.pendientes(evento.getId(), 1, 2, coordinador);
        PageResponse<Pendiente> tercera = service.pendientes(evento.getId(), 2, 2, coordinador);

        assertEquals(List.of(usuario(1), usuario(2)),
                primera.contenido().stream().map(Pendiente::usuarioId).toList());
        assertEquals(List.of(usuario(3), usuario(5)),
                segunda.contenido().stream().map(Pendiente::usuarioId).toList());
        assertTrue(tercera.contenido().isEmpty());
        assertEquals(4, primera.totalElementos());
        assertEquals(2, primera.totalPaginas());
    }

    /**
     * El BRIGADISTA recibe una lista vacía aunque haya pendientes, y consultar no cambia ningún dato
     */
    @Test
    void elBrigadistaNoRecibePendientesYConsultarNoCambiaDatos() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        aSalvo(evento.getId(), usuario(1), PUNTO_A);
        long antes = contarCheckins();

        PageResponse<Pendiente> delBrigadista = service.pendientes(evento.getId(), 0, 20, brigadista);

        assertTrue(delBrigadista.contenido().isEmpty());
        assertEquals(0, delBrigadista.totalElementos());
        assertEquals(antes, contarCheckins());
    }

    /**
     * Los pendientes se consultan con el evento en cualquier estado
     *
     * @param estado estado del evento
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void pendientesFuncionanConElEventoEnCualquierEstado(EstadoEvento estado) {
        Evento evento = evento(estado);
        poblacion(usuario(1));

        PageResponse<Pendiente> pendientes = service.pendientes(evento.getId(), 0, 20, coordinador);

        assertEquals(1, pendientes.totalElementos());
    }
}
