package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

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
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository.ConteoPorEstadoYPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;

/**
 * Lógica de RF-MS04-06 (consolidar el estado del evento y listar personas por estado y punto de encuentro) y de
 * RF-MS04-07 (personas pendientes) con los repositorios y los clientes de MS01 y MS05 simulados
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID BRIGADISTA = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID PUNTO_B = UUID.fromString("d3f5a7c9-1b2e-4d6f-8a0c-4e6b8d0f2a41");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID ESPACIO = UUID.fromString("4e9c7a15-3b2d-4f60-a8e1-7c5d9b3f1a28");
    private static final Instant REGISTRADO = Instant.parse("2026-11-20T15:04:12Z");
    private static final Instant CALCULADO = Instant.parse("2026-11-20T15:10:00Z");
    private static final Clock RELOJ = Clock.fixed(CALCULADO, ZoneOffset.UTC);
    private static final Set<UUID> NADA = Set.of();

    /** Fila de conteo como la devuelve la base de datos */
    private record Fila(String estado, UUID punto, long total) implements ConteoPorEstadoYPunto {

        @Override
        public String getEstado() {
            return estado;
        }

        @Override
        public UUID getPunto() {
            return punto;
        }

        @Override
        public long getTotal() {
            return total;
        }
    }

    @Mock
    private EventoRepository eventos;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private UsuarioNombresClient nombres;

    @Mock
    private ZonasBrigadistaClient zonasBrigadista;

    @Mock
    private PoblacionObjetivoClient poblacionObjetivo;

    private DashboardService service;

    /**
     * Crea el service con el reloj fijo
     */
    @BeforeEach
    void crearService() {
        service = new DashboardService(eventos, checkins, nombres, zonasBrigadista, poblacionObjetivo, RELOJ);
    }

    /**
     * Crea el evento en el estado indicado
     *
     * @param estado estado del evento
     * @return el evento
     */
    private Evento evento(EstadoEvento estado) {
        Evento evento = new Evento(EVENTO);
        evento.setEstado(estado);
        return evento;
    }

    /**
     * Crea un check-in como lo devuelve la base de datos
     *
     * @param usuario usuario que reporta
     * @param estado estado reportado
     * @param punto punto de encuentro, solo con SAFE
     * @return el check-in con su fecha de registro
     */
    private Checkin checkin(UUID usuario, EstadoCheckin estado, UUID punto) {
        Checkin checkin = new Checkin(EVENTO, usuario, estado, punto, null, null);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTRADO);
        return checkin;
    }

    /**
     * Identidad con los roles indicados
     *
     * @param roles roles del solicitante
     * @return la identidad
     */
    private RequesterIdentity con(Rol... roles) {
        return new RequesterIdentity(BRIGADISTA, EnumSet.of(roles[0], roles));
    }

    // ---------------------------------------------------------------- consolidado

    /**
     * Arma los totales de los cuatro estados, las personas a salvo por punto, el total reportado y la fecha del
     * cálculo
     */
    @Test
    void consolidadoArmaLosTotalesLosPuntosYLaFecha() {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of(
                new Fila("SAFE", PUNTO_A, 7), new Fila("SAFE", PUNTO_B, 3), new Fila("EVACUATING", null, 4),
                new Fila("NEEDS_ASSISTANCE", null, 2), new Fila("OFF_CAMPUS", null, 5)));

        Consolidado consolidado = service.consolidado(EVENTO, con(Rol.COORDINADOR));

        assertEquals(EVENTO, consolidado.eventoId());
        assertEquals(EstadoEvento.EN_EJECUCION, consolidado.estadoEvento());
        assertEquals(10, consolidado.totalesPorEstado().safe());
        assertEquals(4, consolidado.totalesPorEstado().evacuating());
        assertEquals(2, consolidado.totalesPorEstado().needsAssistance());
        assertEquals(5, consolidado.totalesPorEstado().offCampus());
        assertEquals(21, consolidado.totalReportados());
        assertEquals(List.of(new TotalPorPunto(PUNTO_A, 7), new TotalPorPunto(PUNTO_B, 3)),
                consolidado.porPuntoEncuentro());
        assertEquals(CALCULADO, consolidado.calculadoEn());
        verifyNoInteractions(zonasBrigadista, nombres);
    }

    /**
     * La fecha del cálculo se entrega en milisegundos
     */
    @Test
    void consolidadoEntregaLaFechaDelCalculoEnMilisegundos() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-20T15:10:00.123456789Z"), ZoneOffset.UTC);
        service = new DashboardService(eventos, checkins, nombres, zonasBrigadista, poblacionObjetivo, reloj);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());

        Consolidado consolidado = service.consolidado(EVENTO, con(Rol.COORDINADOR));

        assertEquals(Instant.parse("2026-11-20T15:10:00.123Z"), consolidado.calculadoEn());
    }

    /**
     * Sin check-ins devuelve los cuatro estados en 0, sin puntos y con total 0
     */
    @Test
    void consolidadoSinCheckinsDevuelveLosCuatroEstadosEnCero() {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.PROGRAMADO)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());

        Consolidado consolidado = service.consolidado(EVENTO, con(Rol.ORGANIZADOR));

        assertEquals(0, consolidado.totalesPorEstado().safe());
        assertEquals(0, consolidado.totalesPorEstado().evacuating());
        assertEquals(0, consolidado.totalesPorEstado().needsAssistance());
        assertEquals(0, consolidado.totalesPorEstado().offCampus());
        assertEquals(0, consolidado.totalReportados());
        assertTrue(consolidado.porPuntoEncuentro().isEmpty());
    }

    /**
     * Funciona con el evento en cualquier estado y lo informa en la respuesta
     *
     * @param estado estado del evento
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void consolidadoFuncionaConElEventoEnCualquierEstado(EstadoEvento estado) {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(estado)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());

        Consolidado consolidado = service.consolidado(EVENTO, con(Rol.AUDITOR));

        assertEquals(estado, consolidado.estadoEvento());
    }

    /**
     * COORDINADOR, ORGANIZADOR y AUDITOR ven todo el evento sin consultar a MS05
     *
     * @param rol rol con acceso a todo el evento
     */
    @ParameterizedTest
    @EnumSource(value = Rol.class, names = { "COORDINADOR", "ORGANIZADOR", "AUDITOR" })
    void consolidadoNoRestringeANingunRolConAccesoATodoElEvento(Rol rol) {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());

        service.consolidado(EVENTO, con(rol));

        verifyNoInteractions(zonasBrigadista);
    }

    /**
     * Un evento inexistente responde 404 sin consultar conteos ni zonas
     */
    @Test
    void consolidadoDeUnEventoInexistenteResponde404() {
        when(eventos.findById(EVENTO)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class,
                () -> service.consolidado(EVENTO, con(Rol.BRIGADISTA)));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(DashboardService.EVENTO_NO_ENCONTRADO, error.getMessage());
        verifyNoInteractions(checkins, zonasBrigadista);
    }

    /**
     * El BRIGADISTA pide sus zonas a MS05 y cuenta solo lo de ellas
     */
    @Test
    void consolidadoDelBrigadistaCuentaSoloSusZonas() {
        ZonasAsignadas zonas = new ZonasAsignadas(Set.of(EDIFICIO), Set.of(ESPACIO), Set.of(PUNTO_A));
        RequesterIdentity brigadista = con(Rol.BRIGADISTA);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(zonasBrigadista.zonasDe(brigadista)).thenReturn(zonas);
        when(checkins.contarVigentes(EVENTO, true, Set.of(PUNTO_A), Set.of(EDIFICIO), Set.of(ESPACIO)))
                .thenReturn(List.of(new Fila("SAFE", PUNTO_A, 6), new Fila("NEEDS_ASSISTANCE", null, 1)));

        Consolidado consolidado = service.consolidado(EVENTO, brigadista);

        assertEquals(6, consolidado.totalesPorEstado().safe());
        assertEquals(0, consolidado.totalesPorEstado().evacuating());
        assertEquals(1, consolidado.totalesPorEstado().needsAssistance());
        assertEquals(0, consolidado.totalesPorEstado().offCampus());
        assertEquals(7, consolidado.totalReportados());
        assertEquals(List.of(new TotalPorPunto(PUNTO_A, 6)), consolidado.porPuntoEncuentro());
    }

    /**
     * Quien tiene BRIGADISTA y también un rol con acceso a todo el evento no queda restringido
     */
    @Test
    void consolidadoDeBrigadistaConOtroRolConAccesoNoSeRestringe() {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(checkins.contarVigentes(EVENTO, false, NADA, NADA, NADA)).thenReturn(List.of());

        service.consolidado(EVENTO, con(Rol.BRIGADISTA, Rol.COORDINADOR));

        verifyNoInteractions(zonasBrigadista);
    }

    /**
     * Quien tiene BRIGADISTA y un rol sin acceso al dashboard sigue restringido a sus zonas
     */
    @Test
    void consolidadoDeBrigadistaConUnRolDeLaComunidadSigueRestringido() {
        RequesterIdentity brigadista = con(Rol.BRIGADISTA, Rol.ESTUDIANTE);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(zonasBrigadista.zonasDe(brigadista)).thenReturn(new ZonasAsignadas(NADA, NADA, NADA));
        when(checkins.contarVigentes(EVENTO, true, NADA, NADA, NADA)).thenReturn(List.of());

        Consolidado consolidado = service.consolidado(EVENTO, brigadista);

        assertEquals(0, consolidado.totalReportados());
    }

    /**
     * Si MS05 no entrega las zonas, el BRIGADISTA recibe 503 y no se consulta nada más
     */
    @Test
    void consolidadoDelBrigadistaSinZonasDeMs05Responde503() {
        RequesterIdentity brigadista = con(Rol.BRIGADISTA);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento(EstadoEvento.EN_EJECUCION)));
        when(zonasBrigadista.zonasDe(brigadista)).thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class, () -> service.consolidado(EVENTO, brigadista));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        verifyNoInteractions(checkins);
    }

    // ---------------------------------------------------------------- personas

    /**
     * Lista las personas con su nombre de MS01, el estado vigente, el punto y la fecha, y los totales de la página
     */
    @Test
    void personasListaConNombreEstadoPuntoYFecha() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        Page<Checkin> pagina = new PageImpl<>(
                List.of(checkin(ANA, EstadoCheckin.SAFE, PUNTO_A), checkin(LUIS, EstadoCheckin.EVACUATING, null)),
                PageRequest.of(1, 2), 5);
        when(checkins.buscarVigentes(EVENTO, null, null, false, NADA, NADA, NADA, PageRequest.of(1, 2)))
                .thenReturn(pagina);
        when(nombres.nombresDe(List.of(ANA, LUIS))).thenReturn(Map.of(ANA, "Ana Pérez", LUIS, "Luis Rojas"));

        PageResponse<CheckinResumen> respuesta = service.personas(EVENTO, null, null, 1, 2, con(Rol.COORDINADOR));

        assertEquals(List.of(
                new CheckinResumen(ANA, "Ana Pérez", EstadoCheckin.SAFE, PUNTO_A, REGISTRADO),
                new CheckinResumen(LUIS, "Luis Rojas", EstadoCheckin.EVACUATING, null, REGISTRADO)),
                respuesta.contenido());
        assertEquals(1, respuesta.page());
        assertEquals(2, respuesta.size());
        assertEquals(5, respuesta.totalElementos());
        assertEquals(3, respuesta.totalPaginas());
        verifyNoInteractions(zonasBrigadista);
    }

    /**
     * Si MS01 no entrega los nombres, la lista sale con el nombre nulo
     */
    @Test
    void personasSinNombresDeMs01SaleConNombreNulo() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentes(EVENTO, null, null, false, NADA, NADA, NADA, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(ANA, EstadoCheckin.OFF_CAMPUS, null)),
                        PageRequest.of(0, 20), 1));
        when(nombres.nombresDe(List.of(ANA))).thenReturn(Map.of());

        PageResponse<CheckinResumen> respuesta = service.personas(EVENTO, null, null, 0, 20, con(Rol.AUDITOR));

        assertEquals(1, respuesta.contenido().size());
        assertNull(respuesta.contenido().get(0).nombre());
        assertEquals(ANA, respuesta.contenido().get(0).usuarioId());
    }

    /**
     * Pasa a la consulta el estado y el punto de encuentro del filtro
     */
    @Test
    void personasAplicaElFiltroDeEstadoYPunto() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentes(EVENTO, EstadoCheckin.SAFE, PUNTO_A, false, NADA, NADA, NADA,
                PageRequest.of(0, 20))).thenReturn(Page.empty(PageRequest.of(0, 20)));

        service.personas(EVENTO, EstadoCheckin.SAFE, PUNTO_A, 0, 20, con(Rol.ORGANIZADOR));

        verify(checkins).buscarVigentes(EVENTO, EstadoCheckin.SAFE, PUNTO_A, false, NADA, NADA, NADA,
                PageRequest.of(0, 20));
    }

    /**
     * El punto de encuentro sin estado es válido: solo las personas a salvo tienen punto
     */
    @Test
    void personasAceptaElPuntoDeEncuentroSinEstado() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentes(EVENTO, null, PUNTO_B, false, NADA, NADA, NADA, PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        PageResponse<CheckinResumen> respuesta = service.personas(EVENTO, null, PUNTO_B, 0, 20,
                con(Rol.COORDINADOR));

        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(0, respuesta.totalElementos());
    }

    /**
     * El punto de encuentro con un estado distinto de SAFE responde 400 antes de consultar nada
     *
     * @param estado estado distinto de SAFE
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "NEEDS_ASSISTANCE", "OFF_CAMPUS" })
    void personasConPuntoYEstadoDistintoDeSafeResponde400(EstadoCheckin estado) {
        ApiException error = assertThrows(ApiException.class,
                () -> service.personas(EVENTO, estado, PUNTO_A, 0, 20, con(Rol.COORDINADOR)));

        assertEquals(ErrorCode.VALIDATION, error.getCode());
        assertEquals("punto_encuentro_id", error.getErrors().get(0).campo());
        assertEquals("El punto de encuentro solo aplica a Estoy a salvo.", error.getErrors().get(0).mensaje());
        verifyNoInteractions(eventos, checkins, nombres, zonasBrigadista);
    }

    /**
     * Un evento inexistente responde 404 sin consultar check-ins, nombres ni zonas, ni siquiera para un BRIGADISTA
     */
    @Test
    void personasDeUnEventoInexistenteResponde404() {
        when(eventos.existsById(EVENTO)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.personas(EVENTO, null, null, 0, 20, con(Rol.BRIGADISTA)));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(DashboardService.EVENTO_NO_ENCONTRADO, error.getMessage());
        verifyNoInteractions(checkins, nombres, zonasBrigadista);
    }

    /**
     * El BRIGADISTA pide sus zonas a MS05 y lista solo lo de ellas
     */
    @Test
    void personasDelBrigadistaListaSoloSusZonas() {
        RequesterIdentity brigadista = con(Rol.BRIGADISTA);
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(zonasBrigadista.zonasDe(brigadista))
                .thenReturn(new ZonasAsignadas(Set.of(EDIFICIO), Set.of(ESPACIO), Set.of(PUNTO_A)));
        when(checkins.buscarVigentes(EVENTO, null, null, true, Set.of(PUNTO_A), Set.of(EDIFICIO), Set.of(ESPACIO),
                PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin(ANA, EstadoCheckin.SAFE, PUNTO_A)),
                        PageRequest.of(0, 20), 1));
        when(nombres.nombresDe(List.of(ANA))).thenReturn(Map.of(ANA, "Ana Pérez"));

        PageResponse<CheckinResumen> respuesta = service.personas(EVENTO, null, null, 0, 20, brigadista);

        assertEquals(1, respuesta.totalElementos());
        assertEquals("Ana Pérez", respuesta.contenido().get(0).nombre());
    }

    /**
     * Si MS05 no entrega las zonas, el BRIGADISTA recibe 503 y no se consulta nada más
     */
    @Test
    void personasDelBrigadistaSinZonasDeMs05Responde503() {
        RequesterIdentity brigadista = con(Rol.BRIGADISTA);
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(zonasBrigadista.zonasDe(brigadista)).thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class,
                () -> service.personas(EVENTO, null, null, 0, 20, brigadista));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        verifyNoInteractions(checkins, nombres);
    }

    /**
     * Una página sin personas no falla: devuelve el contenido vacío con los totales en 0
     *
     * @param pagina número de página pedido
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 7 })
    void personasSinResultadosDevuelveUnaPaginaVacia(int pagina) {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigentes(EVENTO, null, null, false, NADA, NADA, NADA, PageRequest.of(pagina, 20)))
                .thenReturn(Page.empty(PageRequest.of(pagina, 20)));

        PageResponse<CheckinResumen> respuesta = service.personas(EVENTO, null, null, pagina, 20,
                con(Rol.COORDINADOR));

        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(pagina, respuesta.page());
        assertEquals(0, respuesta.totalElementos());
        assertEquals(0, respuesta.totalPaginas());
        verify(nombres).nombresDe(List.of());
        verify(zonasBrigadista, never()).zonasDe(any());
    }

    /**
     * Mientras no se implemente, RF-08 sigue respondiendo 501 NO_IMPLEMENTADO
     */
    @Test
    void asistenciaSigueSinImplementar() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);

        assertEquals(ErrorCode.NOT_IMPLEMENTED,
                assertThrows(ApiException.class, () -> service.asistencia(EVENTO, 0, 20, coordinador)).getCode());
    }

    // ---------------------------------------------------------------- pendientes

    private static final List<String> ROLES = List.of("ESTUDIANTE", "PROFESOR");
    private static final Instant INICIO = Instant.parse("2026-11-20T14:00:00Z");

    /**
     * Usuario de prueba cuyo id crece con el número, para comprobar el orden de la lista
     *
     * @param numero número del usuario, de 1 a 9
     * @return el id del usuario
     */
    private static UUID usuario(int numero) {
        return UUID.fromString("00000000-0000-4000-8000-00000000000" + numero);
    }

    /**
     * Crea un evento EN_EJECUCION con los roles convocados y el inicio fijos
     *
     * @return el evento
     */
    private Evento eventoConvocado() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        evento.setRolesConvocados(ROLES);
        evento.setFechaInicio(INICIO);
        return evento;
    }

    /**
     * Pendientes son la población objetivo menos quienes tienen estado vigente, con el nombre de cada uno y el
     * total en total_elementos
     */
    @Test
    void pendientesSonLaPoblacionMenosLosQueReportaron() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador))
                .thenReturn(Optional.of(Set.of(usuario(1), usuario(2), usuario(3), usuario(4))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of(usuario(2), usuario(4)));
        when(nombres.nombresDe(List.of(usuario(1), usuario(3))))
                .thenReturn(Map.of(usuario(1), "Ana Pérez", usuario(3), "Luis Gómez"));

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, coordinador);

        assertEquals(List.of(new Pendiente(usuario(1), "Ana Pérez"), new Pendiente(usuario(3), "Luis Gómez")),
                respuesta.contenido());
        assertEquals(2, respuesta.totalElementos());
        assertEquals(1, respuesta.totalPaginas());
        assertEquals(0, respuesta.page());
        assertEquals(20, respuesta.size());
        verifyNoInteractions(zonasBrigadista);
    }

    /**
     * La lista sale ordenada por id de usuario sin importar el orden en que MS01 entregó la población
     */
    @Test
    void pendientesSalenOrdenadosPorIdDeUsuario() {
        RequesterIdentity auditor = con(Rol.AUDITOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, auditor))
                .thenReturn(Optional.of(Set.of(usuario(9), usuario(3), usuario(7), usuario(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of(usuario(1), usuario(3), usuario(7), usuario(9)))).thenReturn(Map.of());

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, auditor);

        assertEquals(List.of(usuario(1), usuario(3), usuario(7), usuario(9)),
                respuesta.contenido().stream().map(Pendiente::usuarioId).toList());
    }

    /**
     * Sin check-ins en el evento toda la población objetivo es pendiente (A1)
     */
    @Test
    void sinCheckinsTodaLaPoblacionEsPendiente() {
        RequesterIdentity organizador = con(Rol.ORGANIZADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, organizador))
                .thenReturn(Optional.of(Set.of(usuario(1), usuario(2))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of(usuario(1), usuario(2)))).thenReturn(Map.of());

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, organizador);

        assertEquals(2, respuesta.totalElementos());
    }

    /**
     * Si todos reportaron responde total 0 y lista vacía (A2)
     */
    @Test
    void siTodosReportaronResponde200ConTotalCeroYListaVacia() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador))
                .thenReturn(Optional.of(Set.of(usuario(1), usuario(2))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of(usuario(1), usuario(2)));
        when(nombres.nombresDe(List.of())).thenReturn(Map.of());

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, coordinador);

        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(0, respuesta.totalElementos());
        assertEquals(0, respuesta.totalPaginas());
    }

    /**
     * Quien reportó pero ya no está en la población objetivo (p. ej. cambió de rol) no altera la lista
     */
    @Test
    void quienReportoSinEstarEnLaPoblacionNoAlteraLosPendientes() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador))
                .thenReturn(Optional.of(Set.of(usuario(1), usuario(2))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of(usuario(2), usuario(8)));
        when(nombres.nombresDe(List.of(usuario(1)))).thenReturn(Map.of());

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, coordinador);

        assertEquals(1, respuesta.totalElementos());
        assertEquals(usuario(1), respuesta.contenido().get(0).usuarioId());
    }

    /**
     * Con roles convocados sin usuarios la lista es vacía
     */
    @Test
    void sinPoblacionObjetivoLaListaEsVacia() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador)).thenReturn(Optional.of(Set.of()));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of())).thenReturn(Map.of());

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, coordinador);

        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(0, respuesta.totalElementos());
    }

    /**
     * Pagina la lista: cada página trae su parte, el total es el de pendientes y los nombres se piden solo de la
     * página
     */
    @Test
    void pendientesSePaginan() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador)).thenReturn(Optional.of(
                Set.of(usuario(1), usuario(2), usuario(3), usuario(4), usuario(5))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of(usuario(3), usuario(4)))).thenReturn(Map.of(usuario(3), "Tres"));

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 1, 2, coordinador);

        assertEquals(List.of(new Pendiente(usuario(3), "Tres"), new Pendiente(usuario(4), null)),
                respuesta.contenido());
        assertEquals(5, respuesta.totalElementos());
        assertEquals(3, respuesta.totalPaginas());
        assertEquals(1, respuesta.page());
        assertEquals(2, respuesta.size());
    }

    /**
     * Una página más allá de la última devuelve la lista vacía con los totales, sin fallar ni desbordar
     *
     * @param pagina número de página pedido
     */
    @ParameterizedTest
    @ValueSource(ints = { 3, 100, Integer.MAX_VALUE })
    void unaPaginaMasAllaDeLaUltimaDevuelveLaListaVaciaConLosTotales(int pagina) {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador)).thenReturn(Optional.of(
                Set.of(usuario(1), usuario(2), usuario(3), usuario(4), usuario(5))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of())).thenReturn(Map.of());

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, pagina, 2, coordinador);

        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(5, respuesta.totalElementos());
        assertEquals(3, respuesta.totalPaginas());
    }

    /**
     * Si MS01 no entrega los nombres, la lista sale con el nombre nulo y sigue completa
     */
    @Test
    void sinNombresDeMs01LosPendientesSalenConNombreNulo() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador))
                .thenReturn(Optional.of(Set.of(usuario(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of(usuario(1)))).thenReturn(Map.of());

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, coordinador);

        assertNull(respuesta.contenido().get(0).nombre());
        assertEquals(usuario(1), respuesta.contenido().get(0).usuarioId());
    }

    /**
     * Si MS01 no entrega la población objetivo responde 503 con el mensaje de F-03 y no consulta nada más
     */
    @Test
    void sinPoblacionDeMs01Responde503ConElMensajeDeLaLista() {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.pendientes(EVENTO, 0, 20, coordinador));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
        assertEquals("La lista de pendientes no está disponible en este momento.", error.getMessage());
        verifyNoInteractions(checkins, nombres, zonasBrigadista);
    }

    /**
     * El BRIGADISTA recibe 200 con total 0 y lista vacía sin consultar a MS01, a MS05 ni los check-ins (A6)
     */
    @Test
    void elBrigadistaRecibeUnaListaVaciaSinConsultarAMs01() {
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));

        PageResponse<Pendiente> respuesta = service.pendientes(EVENTO, 0, 20, con(Rol.BRIGADISTA));

        assertTrue(respuesta.contenido().isEmpty());
        assertEquals(0, respuesta.totalElementos());
        assertEquals(0, respuesta.totalPaginas());
        assertEquals(20, respuesta.size());
        verifyNoInteractions(poblacionObjetivo, zonasBrigadista, checkins, nombres);
    }

    /**
     * Quien además de BRIGADISTA tiene otro rol con permiso ve la lista completa
     *
     * @param otro rol que ve todo el evento
     */
    @ParameterizedTest
    @EnumSource(value = Rol.class, names = { "COORDINADOR", "ORGANIZADOR", "AUDITOR" })
    void quienTambienTieneOtroRolConPermisoVeLosPendientes(Rol otro) {
        RequesterIdentity solicitante = con(Rol.BRIGADISTA, otro);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(eventoConvocado()));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, solicitante))
                .thenReturn(Optional.of(Set.of(usuario(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of(usuario(1)))).thenReturn(Map.of());

        assertEquals(1, service.pendientes(EVENTO, 0, 20, solicitante).totalElementos());
    }

    /**
     * Un evento inexistente responde 404 sin consultar nada más, ni siquiera para un BRIGADISTA (A5)
     *
     * @param rol rol del solicitante
     */
    @ParameterizedTest
    @EnumSource(value = Rol.class, names = { "BRIGADISTA", "COORDINADOR" })
    void pendientesDeUnEventoInexistenteResponde404(Rol rol) {
        when(eventos.findById(EVENTO)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.pendientes(EVENTO, 0, 20, con(rol)));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals(DashboardService.EVENTO_NO_ENCONTRADO, error.getMessage());
        verifyNoInteractions(poblacionObjetivo, checkins, nombres, zonasBrigadista);
    }

    /**
     * Los pendientes se consultan con el evento en cualquier estado (RN-MS04-22)
     *
     * @param estado estado del evento
     */
    @ParameterizedTest
    @EnumSource(EstadoEvento.class)
    void pendientesFuncionanConElEventoEnCualquierEstado(EstadoEvento estado) {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        Evento evento = eventoConvocado();
        evento.setEstado(estado);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, INICIO, coordinador))
                .thenReturn(Optional.of(Set.of(usuario(1))));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of(usuario(1)))).thenReturn(Map.of());

        assertEquals(1, service.pendientes(EVENTO, 0, 20, coordinador).totalElementos());
    }

    /**
     * El corte de la población es el inicio del evento si ya pasó, y el momento de la consulta si el evento
     * todavía no empieza o no tiene fecha de inicio (RN-MS04-15)
     *
     * @param inicio inicio del evento; null si no tiene
     * @param corte corte esperado
     */
    @ParameterizedTest
    @CsvSource(value = { "2026-11-20T14:00:00Z,2026-11-20T14:00:00Z", "2026-11-20T16:00:00Z,2026-11-20T15:10:00Z",
            "null,2026-11-20T15:10:00Z" }, nullValues = "null")
    void elCorteDeLaPoblacionEsElInicioOElMomentoDeLaConsulta(Instant inicio, Instant corte) {
        RequesterIdentity coordinador = con(Rol.COORDINADOR);
        Evento evento = eventoConvocado();
        evento.setFechaInicio(inicio);
        when(eventos.findById(EVENTO)).thenReturn(Optional.of(evento));
        when(poblacionObjetivo.usuariosActivosPorRoles(ROLES, corte, coordinador))
                .thenReturn(Optional.of(Set.of()));
        when(checkins.buscarUsuariosConEstadoVigente(EVENTO)).thenReturn(List.of());
        when(nombres.nombresDe(List.of())).thenReturn(Map.of());

        assertTrue(service.pendientes(EVENTO, 0, 20, coordinador).contenido().isEmpty());
    }
}
