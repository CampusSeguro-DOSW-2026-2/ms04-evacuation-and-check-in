package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;

/**
 * Lógica de RF-MS04-05 (consultar el propio estado vigente) con los repositorios simulados
 */
@ExtendWith(MockitoExtension.class)
class EstadoPropioServiceTest {

    private static final UUID EVENTO = UUID.fromString("7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17");
    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID CHECKIN = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant REGISTRADO = Instant.parse("2026-11-20T15:04:12Z");

    @Mock
    private EventoRepository eventos;

    @Mock
    private CheckinRepository checkins;

    @Mock
    private SolicitudAsistenciaRepository solicitudes;

    private EstadoPropioService service;

    private RequesterIdentity requester;

    /**
     * Crea el service con los repositorios simulados y un estudiante como solicitante
     */
    @BeforeEach
    void preparar() {
        service = new EstadoPropioService(eventos, checkins, solicitudes);
        requester = new RequesterIdentity(USUARIO, EnumSet.of(Rol.ESTUDIANTE));
    }

    /**
     * Crea un check-in como lo devuelve la base de datos, con el id y la fecha que asigna ella
     *
     * @param estado estado reportado
     * @param puntoEncuentro punto de encuentro, solo con SAFE
     * @param tipo tipo de ubicación, solo con NEEDS_ASSISTANCE
     * @param ubicacion id de la ubicación, solo con NEEDS_ASSISTANCE
     * @return el check-in con su id y su fecha de registro
     */
    private Checkin checkin(EstadoCheckin estado, UUID puntoEncuentro, TipoUbicacion tipo, UUID ubicacion) {
        Checkin checkin = new Checkin(EVENTO, USUARIO, estado, puntoEncuentro, tipo, ubicacion);
        ReflectionTestUtils.setField(checkin, "id", CHECKIN);
        ReflectionTestUtils.setField(checkin, "registradoEn", REGISTRADO);
        return checkin;
    }

    /**
     * Crea la solicitud de asistencia del usuario con el estado de envío y el id de MS05 indicados
     *
     * @param estadoEnvio estado de envío a MS05
     * @param idMs05 id de la solicitud en MS05, null si no llegó
     * @return la solicitud de asistencia
     */
    private SolicitudAsistencia solicitud(EstadoEnvio estadoEnvio, String idMs05) {
        SolicitudAsistencia solicitud = new SolicitudAsistencia(CHECKIN, EVENTO, USUARIO, TipoUbicacion.EDIFICIO,
                EDIFICIO, REGISTRADO);
        solicitud.setEstadoEnvio(estadoEnvio);
        solicitud.setSolicitudMs05Id(idMs05);
        return solicitud;
    }

    /**
     * Con SAFE responde el punto de encuentro, sin ubicación ni solicitud, y no consulta solicitudes
     */
    @Test
    void conSafeDevuelveElPuntoDeEncuentroSinUbicacionNiSolicitud() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, PUNTO, null, null)));

        CheckinResponse respuesta = service.consultar(EVENTO, requester);

        assertEquals(CHECKIN, respuesta.id());
        assertEquals(EVENTO, respuesta.eventoId());
        assertEquals(EstadoCheckin.SAFE, respuesta.estado());
        assertEquals(PUNTO, respuesta.puntoEncuentroId());
        assertNull(respuesta.ubicacion());
        assertNull(respuesta.solicitudAsistencia());
        assertEquals(REGISTRADO, respuesta.fechaHora());
        verifyNoInteractions(solicitudes);
    }

    /**
     * Con EVACUATING y OFF_CAMPUS no hay punto de encuentro, ubicación ni solicitud
     *
     * @param estado estado vigente del usuario
     */
    @ParameterizedTest
    @EnumSource(value = EstadoCheckin.class, names = { "EVACUATING", "OFF_CAMPUS" })
    void conEvacuatingOOffCampusNoTraePuntoUbicacionNiSolicitud(EstadoCheckin estado) {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO)).thenReturn(Optional.of(checkin(estado, null, null, null)));

        CheckinResponse respuesta = service.consultar(EVENTO, requester);

        assertEquals(estado, respuesta.estado());
        assertNull(respuesta.puntoEncuentroId());
        assertNull(respuesta.ubicacion());
        assertNull(respuesta.solicitudAsistencia());
        verifyNoInteractions(solicitudes);
    }

    /**
     * Con NEEDS_ASSISTANCE responde la ubicación y el estado de la solicitud enviada a MS05
     */
    @Test
    void conNeedsAssistanceDevuelveLaUbicacionYLaSolicitudEnviada() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, EDIFICIO)));
        when(solicitudes.findByEventoIdAndUsuarioId(EVENTO, USUARIO))
                .thenReturn(Optional.of(solicitud(EstadoEnvio.ENVIADA, "SOL-2026-0042")));

        CheckinResponse respuesta = service.consultar(EVENTO, requester);

        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, respuesta.estado());
        assertNull(respuesta.puntoEncuentroId());
        assertEquals(TipoUbicacion.EDIFICIO, respuesta.ubicacion().tipo());
        assertEquals(EDIFICIO, respuesta.ubicacion().id());
        assertEquals("SOL-2026-0042", respuesta.solicitudAsistencia().id());
        assertEquals(EstadoEnvio.ENVIADA, respuesta.solicitudAsistencia().estadoEnvio());
    }

    /**
     * La solicitud pendiente o rechazada todavía no tiene id de MS05
     *
     * @param estadoEnvio estado de envío de la solicitud
     */
    @ParameterizedTest
    @EnumSource(value = EstadoEnvio.class, names = { "PENDIENTE_ENVIO", "RECHAZADA" })
    void laSolicitudQueNoHaLlegadoAMs05NoTraeId(EstadoEnvio estadoEnvio) {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.ESPACIO, EDIFICIO)));
        when(solicitudes.findByEventoIdAndUsuarioId(EVENTO, USUARIO))
                .thenReturn(Optional.of(solicitud(estadoEnvio, null)));

        CheckinResponse respuesta = service.consultar(EVENTO, requester);

        assertNull(respuesta.solicitudAsistencia().id());
        assertEquals(estadoEnvio, respuesta.solicitudAsistencia().estadoEnvio());
    }

    /**
     * Si el check-in de asistencia no tiene solicitud, responde el estado sin ella
     */
    @Test
    void siFaltaLaSolicitudDelCheckinDeAsistenciaResponderSinElla() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO)).thenReturn(Optional
                .of(checkin(EstadoCheckin.NEEDS_ASSISTANCE, null, TipoUbicacion.EDIFICIO, EDIFICIO)));
        when(solicitudes.findByEventoIdAndUsuarioId(EVENTO, USUARIO)).thenReturn(Optional.empty());

        CheckinResponse respuesta = service.consultar(EVENTO, requester);

        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, respuesta.estado());
        assertEquals(EDIFICIO, respuesta.ubicacion().id());
        assertNull(respuesta.solicitudAsistencia());
    }

    /**
     * Un evento inexistente es 404 y no se consultan los check-ins
     */
    @Test
    void unEventoInexistenteEsNoEncontradoYNoConsultaCheckins() {
        when(eventos.existsById(EVENTO)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class, () -> service.consultar(EVENTO, requester));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("Evento no encontrado.", error.getMessage());
        verifyNoInteractions(checkins, solicitudes);
    }

    /**
     * Sin check-in en el evento es 404 con el mensaje del RF-MS04-05
     */
    @Test
    void sinCheckinEnElEventoEsNoEncontrado() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO)).thenReturn(Optional.empty());

        ApiException error = assertThrows(ApiException.class, () -> service.consultar(EVENTO, requester));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("No tiene check-in en este evento.", error.getMessage());
        verifyNoInteractions(solicitudes);
    }

    /**
     * La consulta usa únicamente el id de usuario de la identidad del solicitante
     */
    @Test
    void soloConsultaPorElIdDeUsuarioDeLaIdentidad() {
        UUID otroUsuario = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
        RequesterIdentity otro = new RequesterIdentity(otroUsuario, EnumSet.of(Rol.PROFESOR));
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, otroUsuario)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.consultar(EVENTO, otro));

        verify(checkins).buscarVigente(EVENTO, otroUsuario);
        verify(checkins, never()).buscarVigente(EVENTO, USUARIO);
    }

    /**
     * Consultar solo lee: no usa ninguna operación de escritura
     */
    @Test
    void consultarNoModificaNingunDato() {
        when(eventos.existsById(EVENTO)).thenReturn(true);
        when(checkins.buscarVigente(EVENTO, USUARIO))
                .thenReturn(Optional.of(checkin(EstadoCheckin.SAFE, PUNTO, null, null)));

        service.consultar(EVENTO, requester);

        verify(eventos).existsById(EVENTO);
        verify(checkins).buscarVigente(EVENTO, USUARIO);
        verifyNoMoreInteractions(eventos, checkins, solicitudes);
    }
}
