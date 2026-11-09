package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinResponse;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.CheckinRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.SolicitudAsistenciaRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;
import jakarta.persistence.EntityManager;

/**
 * RF-MS04-05 contra PostgreSQL real con el esquema de database/: el estado vigente sale de la vista
 * v_checkins_vigentes, la solicitud de asistencia de su tabla, y consultar no cambia ningún dato. Necesita Docker
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainerConfig.class, EstadoPropioService.class })
class EstadoPropioIntegracionTest {

    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID OTRO_USUARIO = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");

    @Autowired
    private EstadoPropioService service;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private CheckinRepository checkins;

    @Autowired
    private SolicitudAsistenciaRepository solicitudes;

    @Autowired
    private EntityManager entityManager;

    /**
     * Crea un evento en el estado indicado, con las fechas que exigen las restricciones del esquema
     *
     * @param estado estado del evento
     * @return el evento guardado
     */
    private Evento evento(EstadoEvento estado) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Simulacro de evacuación bloque A");
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(Instant.parse("2026-11-20T15:00:00Z"));
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
     * Agrega la solicitud de asistencia del check-in con el estado de envío y el id de MS05 indicados
     *
     * @param checkin check-in NEEDS_ASSISTANCE que la originó
     * @param estadoEnvio estado de envío a MS05
     * @param idMs05 id de la solicitud en MS05, null si no llegó
     */
    private void solicitar(Checkin checkin, EstadoEnvio estadoEnvio, String idMs05) {
        SolicitudAsistencia solicitud = new SolicitudAsistencia(checkin.getId(), checkin.getEventoId(),
                checkin.getUsuarioId(), checkin.getUbicacionTipo(), checkin.getUbicacionId(),
                checkin.getRegistradoEn());
        solicitud.setEstadoEnvio(estadoEnvio);
        solicitud.setSolicitudMs05Id(idMs05);
        solicitudes.save(solicitud);
        entityManager.flush();
    }

    /**
     * Identidad de un estudiante con el id dado
     *
     * @param usuario id del usuario
     * @return la identidad del solicitante
     */
    private RequesterIdentity estudiante(UUID usuario) {
        return new RequesterIdentity(usuario, EnumSet.of(Rol.ESTUDIANTE));
    }

    /**
     * Cuenta todos los check-ins de la base, de cualquier evento
     *
     * @return el total de check-ins
     */
    private long contarCheckins() {
        return ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM checkins").getSingleResult())
                .longValue();
    }

    /**
     * Con varios cambios de estado devuelve solo el último check-in
     */
    @Test
    void conVariosCambiosDevuelveSoloElUltimoCheckin() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        reportar(evento.getId(), USUARIO, EstadoCheckin.EVACUATING, null, null, null);
        reportar(evento.getId(), USUARIO, EstadoCheckin.OFF_CAMPUS, null, null, null);
        Checkin ultimo = reportar(evento.getId(), USUARIO, EstadoCheckin.SAFE, PUNTO, null, null);
        entityManager.clear();

        CheckinResponse respuesta = service.consultar(evento.getId(), estudiante(USUARIO));

        assertEquals(ultimo.getId(), respuesta.id());
        assertEquals(evento.getId(), respuesta.eventoId());
        assertEquals(EstadoCheckin.SAFE, respuesta.estado());
        assertEquals(PUNTO, respuesta.puntoEncuentroId());
        assertEquals(ultimo.getRegistradoEn(), respuesta.fechaHora());
        assertNull(respuesta.ubicacion());
        assertNull(respuesta.solicitudAsistencia());
    }

    /**
     * Con NEEDS_ASSISTANCE devuelve la ubicación y la solicitud enviada a MS05
     */
    @Test
    void conNeedsAssistanceDevuelveLaUbicacionYLaSolicitudEnviadaAMs05() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Checkin checkin = reportar(evento.getId(), USUARIO, EstadoCheckin.NEEDS_ASSISTANCE, null,
                TipoUbicacion.EDIFICIO, EDIFICIO);
        solicitar(checkin, EstadoEnvio.ENVIADA, "SOL-2026-0042");
        entityManager.clear();

        CheckinResponse respuesta = service.consultar(evento.getId(), estudiante(USUARIO));

        assertEquals(EstadoCheckin.NEEDS_ASSISTANCE, respuesta.estado());
        assertNull(respuesta.puntoEncuentroId());
        assertEquals(TipoUbicacion.EDIFICIO, respuesta.ubicacion().tipo());
        assertEquals(EDIFICIO, respuesta.ubicacion().id());
        assertEquals("SOL-2026-0042", respuesta.solicitudAsistencia().id());
        assertEquals(EstadoEnvio.ENVIADA, respuesta.solicitudAsistencia().estadoEnvio());
    }

    /**
     * La solicitud pendiente de envío no trae id de MS05
     */
    @Test
    void laSolicitudPendienteNoTraeIdDeMs05() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Checkin checkin = reportar(evento.getId(), USUARIO, EstadoCheckin.NEEDS_ASSISTANCE, null,
                TipoUbicacion.ESPACIO, EDIFICIO);
        solicitar(checkin, EstadoEnvio.PENDIENTE_ENVIO, null);
        entityManager.clear();

        CheckinResponse respuesta = service.consultar(evento.getId(), estudiante(USUARIO));

        assertNull(respuesta.solicitudAsistencia().id());
        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, respuesta.solicitudAsistencia().estadoEnvio());
    }

    /**
     * Si pide asistencia otra vez, sigue mostrando la única solicitud del usuario en el evento
     */
    @Test
    void siPideAsistenciaOtraVezSigueMostrandoLaUnicaSolicitudDelUsuario() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Checkin primero = reportar(evento.getId(), USUARIO, EstadoCheckin.NEEDS_ASSISTANCE, null,
                TipoUbicacion.EDIFICIO, EDIFICIO);
        solicitar(primero, EstadoEnvio.ENVIADA, "SOL-2026-0042");
        reportar(evento.getId(), USUARIO, EstadoCheckin.SAFE, PUNTO, null, null);
        Checkin tercero = reportar(evento.getId(), USUARIO, EstadoCheckin.NEEDS_ASSISTANCE, null,
                TipoUbicacion.ESPACIO, EDIFICIO);
        entityManager.clear();

        CheckinResponse respuesta = service.consultar(evento.getId(), estudiante(USUARIO));

        assertEquals(tercero.getId(), respuesta.id());
        assertEquals(TipoUbicacion.ESPACIO, respuesta.ubicacion().tipo());
        assertEquals("SOL-2026-0042", respuesta.solicitudAsistencia().id());
        assertEquals(EstadoEnvio.ENVIADA, respuesta.solicitudAsistencia().estadoEnvio());
    }

    /**
     * Después de cambiar a SAFE ya no se muestra la solicitud
     */
    @Test
    void despuesDeCambiarASafeYaNoMuestraLaSolicitud() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Checkin primero = reportar(evento.getId(), USUARIO, EstadoCheckin.NEEDS_ASSISTANCE, null,
                TipoUbicacion.EDIFICIO, EDIFICIO);
        solicitar(primero, EstadoEnvio.ENVIADA, "SOL-2026-0042");
        reportar(evento.getId(), USUARIO, EstadoCheckin.SAFE, PUNTO, null, null);
        entityManager.clear();

        CheckinResponse respuesta = service.consultar(evento.getId(), estudiante(USUARIO));

        assertEquals(EstadoCheckin.SAFE, respuesta.estado());
        assertNull(respuesta.ubicacion());
        assertNull(respuesta.solicitudAsistencia());
    }

    /**
     * Cada usuario solo ve su propio estado
     */
    @Test
    void cadaUsuarioSoloVeSuPropioEstado() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Checkin delUsuario = reportar(evento.getId(), USUARIO, EstadoCheckin.EVACUATING, null, null, null);
        reportar(evento.getId(), OTRO_USUARIO, EstadoCheckin.OFF_CAMPUS, null, null, null);
        entityManager.clear();

        CheckinResponse respuesta = service.consultar(evento.getId(), estudiante(USUARIO));

        assertEquals(delUsuario.getId(), respuesta.id());
        assertEquals(EstadoCheckin.EVACUATING, respuesta.estado());
    }

    /**
     * El estado vigente no se mezcla entre eventos
     */
    @Test
    void elEstadoVigenteNoSeMezclaEntreEventos() {
        Evento uno = evento(EstadoEvento.EN_EJECUCION);
        Evento otro = evento(EstadoEvento.EN_EJECUCION);
        reportar(uno.getId(), USUARIO, EstadoCheckin.SAFE, PUNTO, null, null);
        reportar(otro.getId(), USUARIO, EstadoCheckin.EVACUATING, null, null, null);
        entityManager.clear();

        assertEquals(EstadoCheckin.SAFE, service.consultar(uno.getId(), estudiante(USUARIO)).estado());
        assertEquals(EstadoCheckin.EVACUATING, service.consultar(otro.getId(), estudiante(USUARIO)).estado());
    }

    /**
     * Sin check-in propio es 404 aunque otras personas hayan reportado
     */
    @Test
    void sinCheckinEnElEventoEsNoEncontradoAunqueOtrosHayanReportado() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        reportar(evento.getId(), OTRO_USUARIO, EstadoCheckin.OFF_CAMPUS, null, null, null);
        entityManager.clear();

        ApiException error = assertThrows(ApiException.class,
                () -> service.consultar(evento.getId(), estudiante(USUARIO)));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("No tiene check-in en este evento.", error.getMessage());
    }

    /**
     * Un evento inexistente es 404
     */
    @Test
    void unEventoInexistenteEsNoEncontrado() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.consultar(UUID.randomUUID(), estudiante(USUARIO)));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        assertEquals("Evento no encontrado.", error.getMessage());
    }

    /**
     * Funciona con el evento FINALIZADO (RN-MS04-22)
     */
    @Test
    void funcionaConElEventoFinalizado() {
        Evento evento = evento(EstadoEvento.FINALIZADO);
        Checkin checkin = reportar(evento.getId(), USUARIO, EstadoCheckin.SAFE, PUNTO, null, null);
        entityManager.clear();

        CheckinResponse respuesta = service.consultar(evento.getId(), estudiante(USUARIO));

        assertEquals(checkin.getId(), respuesta.id());
        assertEquals(EstadoCheckin.SAFE, respuesta.estado());
    }

    /**
     * Consultar no agrega ni modifica check-ins ni solicitudes
     */
    @Test
    void consultarNoAgregaNiModificaDatos() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        Checkin checkin = reportar(evento.getId(), USUARIO, EstadoCheckin.NEEDS_ASSISTANCE, null,
                TipoUbicacion.EDIFICIO, EDIFICIO);
        solicitar(checkin, EstadoEnvio.PENDIENTE_ENVIO, null);
        entityManager.clear();
        long checkinsAntes = contarCheckins();
        long solicitudesAntes = solicitudes.count();

        service.consultar(evento.getId(), estudiante(USUARIO));
        service.consultar(evento.getId(), estudiante(USUARIO));

        assertEquals(checkinsAntes, contarCheckins());
        assertEquals(solicitudesAntes, solicitudes.count());
        assertEquals(EstadoEnvio.PENDIENTE_ENVIO,
                solicitudes.findByEventoIdAndUsuarioId(evento.getId(), USUARIO).orElseThrow().getEstadoEnvio());
    }
}
