package edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Checkin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEnvio;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.SolicitudAsistencia;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.TipoUbicacion;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Entidades y repositorios contra PostgreSQL real con el esquema de database/ (3_modelo-datos.md). Arrancar el
 * contexto ya verifica que las entidades coinciden con las tablas (ddl-auto: validate)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainerConfig.class)
class PersistenciaTest {

    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private CheckinRepository checkins;

    @Autowired
    private SolicitudAsistenciaRepository solicitudes;

    @Autowired
    private EntityManager entityManager;

    /**
     * Crea un evento en el estado indicado con los roles dados
     */
    private Evento evento(EstadoEvento estado, List<String> roles) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Simulacro de evacuación bloque A");
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(estado == EstadoEvento.BORRADOR ? null : Instant.parse("2026-11-20T15:00:00Z"));
        evento.setRolesConvocados(roles);
        evento.setEscenarioEdificios(List.of("Bloque A"));
        evento.setEscenarioZonas(List.of("Zona norte"));
        return eventos.saveAndFlush(evento);
    }

    @Test
    void guardaYLeeUnEventoConSusListas() {
        Evento guardado = evento(EstadoEvento.PROGRAMADO, List.of("ESTUDIANTE", "PROFESOR"));
        entityManager.clear();

        Evento leido = eventos.findById(guardado.getId()).orElseThrow();

        assertEquals(List.of("ESTUDIANTE", "PROFESOR"), leido.getRolesConvocados());
        assertEquals(List.of("Bloque A"), leido.getEscenarioEdificios());
        assertEquals(List.of("Zona norte"), leido.getEscenarioZonas());
        assertFalse(leido.isCheckinHabilitado());
        assertNotNull(leido.getCreadoEn());
    }

    @Test
    void elEstadoVigenteEsElCheckinMasReciente() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE"));
        checkins.save(new Checkin(evento.getId(), USUARIO, EstadoCheckin.EVACUATING, null, null, null));
        Checkin segundo = checkins.save(new Checkin(evento.getId(), USUARIO, EstadoCheckin.SAFE, PUNTO, null, null));
        entityManager.flush();

        assertNotNull(segundo.getNumero());
        assertNotNull(segundo.getRegistradoEn());
        Checkin vigente = checkins.buscarVigente(evento.getId(), USUARIO).orElseThrow();
        assertEquals(segundo.getId(), vigente.getId());
        assertEquals(EstadoCheckin.SAFE, vigente.getEstado());
        assertEquals(PUNTO, vigente.getPuntoEncuentroId());
    }

    @Test
    void cuentaLosEstadosVigentesSinContarDosVecesAUnaPersona() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE"));
        UUID otro = UUID.randomUUID();
        checkins.save(new Checkin(evento.getId(), USUARIO, EstadoCheckin.EVACUATING, null, null, null));
        checkins.save(new Checkin(evento.getId(), USUARIO, EstadoCheckin.SAFE, PUNTO, null, null));
        checkins.save(new Checkin(evento.getId(), otro, EstadoCheckin.OFF_CAMPUS, null, null, null));
        entityManager.flush();

        Map<String, Long> totales = checkins.contarVigentesPorEstado(evento.getId()).stream()
                .collect(Collectors.toMap(CheckinRepository.ConteoPorEstado::getEstado,
                        CheckinRepository.ConteoPorEstado::getTotal));

        assertEquals(Map.of("SAFE", 1L, "OFF_CAMPUS", 1L), totales);
    }

    @Test
    void soloDevuelveLosEventosEnEjecucionQueConvocanAlRol() {
        Evento activoEstudiantes = evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE", "PROFESOR"));
        evento(EstadoEvento.EN_EJECUCION, List.of("BRIGADISTA"));
        evento(EstadoEvento.PROGRAMADO, List.of("ESTUDIANTE"));

        List<Evento> activos = eventos.buscarActivosPorRoles(List.of("ESTUDIANTE"));

        assertEquals(List.of(activoEstudiantes.getId()), activos.stream().map(Evento::getId).toList());
        assertTrue(eventos.buscarActivosPorRoles(List.of()).isEmpty());
    }

    @Test
    void elBloqueoAsesorSePuedeTomarDentroDeLaTransaccion() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE"));

        checkins.bloquearUsuarioEnEvento(evento.getId(), USUARIO);
        checkins.bloquearUsuarioEnEvento(evento.getId(), USUARIO);

        assertTrue(checkins.buscarVigente(evento.getId(), USUARIO).isEmpty());
    }

    @Test
    void laBaseDeDatosRechazaUnCheckinSafeSinPuntoDeEncuentro() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE"));
        checkins.save(new Checkin(evento.getId(), USUARIO, EstadoCheckin.SAFE, null, null, null));

        assertThrows(PersistenceException.class, () -> entityManager.flush());
    }

    @Test
    void losCheckinsNoSePuedenModificarNiEliminar() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE"));
        checkins.save(new Checkin(evento.getId(), USUARIO, EstadoCheckin.EVACUATING, null, null, null));
        entityManager.flush();

        assertThrows(PersistenceException.class, () -> entityManager
                .createNativeQuery("UPDATE checkins SET estado = 'OFF_CAMPUS'").executeUpdate());
    }

    @Test
    void unUsuarioTieneComoMaximoUnaSolicitudPorEvento() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION, List.of("ESTUDIANTE"));
        UUID edificio = UUID.randomUUID();
        Checkin checkin = checkins.save(new Checkin(evento.getId(), USUARIO, EstadoCheckin.NEEDS_ASSISTANCE, null,
                TipoUbicacion.EDIFICIO, edificio));
        entityManager.flush();
        SolicitudAsistencia primera = solicitudes.saveAndFlush(new SolicitudAsistencia(checkin.getId(),
                evento.getId(), USUARIO, TipoUbicacion.EDIFICIO, edificio, checkin.getRegistradoEn()));

        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, primera.getEstadoEnvio());
        assertTrue(solicitudes.findByEventoIdAndUsuarioId(evento.getId(), USUARIO).isPresent());
        solicitudes.save(new SolicitudAsistencia(checkin.getId(), evento.getId(), USUARIO, TipoUbicacion.EDIFICIO,
                edificio, checkin.getRegistradoEn()));
        assertThrows(PersistenceException.class, () -> entityManager.flush());
    }
}
