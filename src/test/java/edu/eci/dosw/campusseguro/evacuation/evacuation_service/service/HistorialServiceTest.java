package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PageResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.RegistroHistorial;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Ubicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;

/**
 * Lógica de RF-MS04-09 (histórico de check-ins del evento) con los repositorios simulados: cada reporte llega con
 * su usuario, estado, punto o ubicación y fecha, en el orden que entrega la consulta, y la paginación y los filtros
 * llegan tal como se piden
 */
@ExtendWith(MockitoExtension.class)
class HistorialServiceTest {

    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final Instant PRIMERO = Instant.parse("2026-11-20T15:02:05Z");
    private static final Instant SEGUNDO = Instant.parse("2026-11-20T15:04:12Z");
    private static final UUID SIN_USUARIO = null;
    private static final EstadoCheckin SIN_ESTADO = null;

    @Mock
    private EventoRepository eventos;

    @Mock
    private CheckinRepository checkins;

    private HistorialService service;

    /**
     * Crea el service con los repositorios simulados y un evento que existe
     */
    @BeforeEach
    void crearElService() {
        service = new HistorialService(eventos, checkins);
        lenient().when(eventos.existsById(EVENTO)).thenReturn(true);
    }

    /**
     * Crea un check-in como lo devuelve la base de datos, con el id y la fecha que asigna ella
     *
     * @param usuario usuario que reporta
     * @param estado estado reportado
     * @param punto punto de encuentro, solo con SAFE
     * @param tipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacion id de la ubicación, solo con NEEDS_ASSISTANCE
     * @param registrado fecha y hora del reporte
     * @return el check-in con su id y su fecha de registro
     */
    private Checkin checkin(UUID usuario, EstadoCheckin estado, UUID punto, TipoUbicacion tipo, UUID ubicacion,
            Instant registrado) {
        Checkin checkin = new Checkin(EVENTO, usuario, estado, punto, tipo, ubicacion);
        ReflectionTestUtils.setField(checkin, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(checkin, "registradoEn", registrado);
        return checkin;
    }

    /**
     * Cada check-in llega con su id, usuario, estado, punto de encuentro y fecha de registro
     */
    @Test
    void cadaCheckinLlegaConSuIdUsuarioEstadoPuntoYFecha() {
        Checkin aSalvo = checkin(ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, PRIMERO);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(aSalvo), PageRequest.of(0, 20), 1));

        PageResponse<RegistroHistorial> respuesta = service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(1, respuesta.contenido().size());
        RegistroHistorial registro = respuesta.contenido().get(0);
        assertEquals(aSalvo.getId(), registro.checkinId());
        assertEquals(ANA, registro.usuarioId());
        assertEquals(EstadoCheckin.SAFE, registro.estado());
        assertEquals(PUNTO_A, registro.puntoEncuentroId());
        assertNull(registro.ubicacion());
        assertEquals(PRIMERO, registro.registradoEn());
    }

    /**
     * Quien pidió asistencia llega con su ubicación y sin punto de encuentro
     */
    @Test
    void quienPidioAsistenciaLlegaConSuUbicacion() {
        Checkin asistencia = checkin(LUIS, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, EDIFICIO,
                SEGUNDO);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(asistencia), PageRequest.of(0, 20), 1));

        RegistroHistorial registro = service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 0, 20).contenido().get(0);

        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, registro.estado());
        assertNull(registro.puntoEncuentroId());
        assertEquals(new Ubicacion(TipoUbicacion.EDIFICIO, EDIFICIO), registro.ubicacion());
    }

    /**
     * EVACUATING y OFF_CAMPUS no traen punto de encuentro ni ubicación
     *
     * @param estado estado sin punto ni ubicación
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "OFF_CAMPUS" })
    void losEstadosSinPuntoNiUbicacionLlegaSinEllos(EstadoCheckin estado) {
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(List.of(checkin(ANA, estado, null, null, null, PRIMERO)), PageRequest.of(0, 20), 1));

        RegistroHistorial registro = service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 0, 20).contenido().get(0);

        assertEquals(estado, registro.estado());
        assertNull(registro.puntoEncuentroId());
        assertNull(registro.ubicacion());
    }

    /**
     * Todos los reportes de una persona llegan, en el orden que entrega la consulta (RN-MS04-08)
     */
    @Test
    void todosLosReportesDeUnaPersonaLleganEnElOrdenDeLaConsulta() {
        Checkin evacuando = checkin(ANA, EstadoCheckin.EVACUATING, null, null, null, PRIMERO);
        Checkin asistencia = checkin(ANA, EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, EDIFICIO,
                SEGUNDO);
        Checkin aSalvo = checkin(ANA, EstadoCheckin.SAFE, PUNTO_A, null, null, Instant.parse("2026-11-20T15:09:40Z"));
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20))).thenReturn(
                new PageImpl<>(List.of(evacuando, asistencia, aSalvo), PageRequest.of(0, 20), 3));

        PageResponse<RegistroHistorial> respuesta = service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(List.of(EstadoCheckin.EVACUATING, EstadoCheckin.NEEDS_ASSISTANCE, EstadoCheckin.SAFE),
                respuesta.contenido().stream().map(RegistroHistorial::estado).toList());
        assertEquals(List.of(evacuando.getId(), asistencia.getId(), aSalvo.getId()),
                respuesta.contenido().stream().map(RegistroHistorial::checkinId).toList());
        assertEquals(3, respuesta.totalElementos());
    }

    /**
     * A4: un evento sin check-ins devuelve la lista vacía, con total 0 y sin páginas
     */
    @Test
    void unEventoSinCheckinsDevuelveLaListaVacia() {
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResponse<RegistroHistorial> respuesta = service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 0, 20);

        assertEquals(List.of(), respuesta.contenido());
        assertEquals(0, respuesta.page());
        assertEquals(20, respuesta.size());
        assertEquals(0, respuesta.totalElementos());
        assertEquals(0, respuesta.totalPaginas());
    }

    /**
     * Una página más allá de la última viene vacía y conserva el total y la página pedida
     */
    @Test
    void unaPaginaMasAllaDeLaUltimaVieneVaciaYConservaElTotal() {
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(7, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(7, 20), 45));

        PageResponse<RegistroHistorial> respuesta = service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 7, 20);

        assertEquals(List.of(), respuesta.contenido());
        assertEquals(7, respuesta.page());
        assertEquals(45, respuesta.totalElementos());
        assertEquals(3, respuesta.totalPaginas());
    }

    /**
     * El total de páginas sale del total de check-ins y el tamaño de página
     */
    @Test
    void elTotalDePaginasSaleDelTotalYElTamano() {
        Checkin checkin = checkin(ANA, EstadoCheckin.EVACUATING, null, null, null, PRIMERO);
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(2, 20)))
                .thenReturn(new PageImpl<>(List.of(checkin), PageRequest.of(2, 20), 41));

        PageResponse<RegistroHistorial> respuesta = service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 2, 20);

        assertEquals(2, respuesta.page());
        assertEquals(20, respuesta.size());
        assertEquals(41, respuesta.totalElementos());
        assertEquals(3, respuesta.totalPaginas());
    }

    /**
     * El usuario, el estado y la página pedidos llegan tal cual a la consulta
     */
    @Test
    void losFiltrosYLaPaginaPedidosLleganALaConsulta() {
        when(checkins.buscarHistorial(EVENTO, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        service.historial(EVENTO, ANA, EstadoCheckin.SAFE, 2, 5);

        verify(checkins).buscarHistorial(EVENTO, ANA, EstadoCheckin.SAFE, PageRequest.of(2, 5));
    }

    /**
     * Sin filtros se pide todo el evento
     */
    @Test
    void sinFiltrosSePideTodoElEvento() {
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 0, 100);

        verify(checkins).buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 100));
    }

    /**
     * A3: un evento que no existe responde 404 y no se consultan los check-ins
     */
    @Test
    void unEventoInexistenteResponde404SinConsultarLosCheckins() {
        UUID otro = UUID.randomUUID();
        when(eventos.existsById(otro)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> service.historial(otro, SIN_USUARIO, SIN_ESTADO, 0, 20));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("Evento no encontrado.", error.getMessage());
        verifyNoInteractions(checkins);
    }

    /**
     * Consultar el histórico no agrega, modifica ni elimina nada (RN-MS04-17)
     */
    @Test
    void consultarElHistoricoSoloLeeLosDatos() {
        when(checkins.buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.historial(EVENTO, SIN_USUARIO, SIN_ESTADO, 0, 20);

        verify(checkins, never()).save(any());
        verify(checkins, never()).flush();
        verify(eventos, never()).save(any());
        verify(checkins).buscarHistorial(EVENTO, SIN_USUARIO, SIN_ESTADO, PageRequest.of(0, 20));
        verifyNoMoreInteractions(checkins);
    }
}
