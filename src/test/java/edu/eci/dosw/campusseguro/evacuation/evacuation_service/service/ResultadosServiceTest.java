package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.PrimerCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Resultados;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.SolicitudResultado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalPorPunto;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
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
 * RF-MS04-10 Entregar resultados del evento, contra PostgreSQL real con el esquema de database/. MS01 es un doble:
 * su cliente se prueba en Ms01PoblacionObjetivoClientTest
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PostgresTestcontainerConfig.class, ResultadosService.class, ResultadosServiceTest.Reloj.class })
class ResultadosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-20T16:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-20T15:00:00Z");
    private static final UUID PUNTO_A = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID PUNTO_B = UUID.fromString("9b2e5f3c-6d4a-4b9f-80e1-3f7a2d5b8c46");
    private static final RequesterIdentity ORGANIZADOR = new RequesterIdentity(
            UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42"), EnumSet.of(Rol.ORGANIZADOR));

    /** Reloj fijo para el corte de la población objetivo */
    @TestConfiguration
    static class Reloj {
        @Bean
        Clock clock() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    private ResultadosService service;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private CheckinRepository checkins;

    @Autowired
    private SolicitudAsistenciaRepository solicitudes;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PoblacionObjetivoClient poblacionObjetivo;

    private Evento evento(EstadoEvento estado) {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Simulacro de evacuación bloque A");
        evento.setSimulacro(true);
        evento.setEstado(estado);
        evento.setFechaInicio(estado == EstadoEvento.BORRADOR ? null : INICIO);
        evento.setFechaCierre(estado == EstadoEvento.FINALIZADO ? AHORA : null);
        evento.setRolesConvocados(List.of("ESTUDIANTE", "PROFESOR"));
        return eventos.saveAndFlush(evento);
    }

    private Checkin checkin(Evento evento, UUID usuario, EstadoCheckin estado) {
        UUID punto = estado == EstadoCheckin.SAFE ? PUNTO_A : null;
        boolean asistencia = estado == EstadoCheckin.NEEDS_ASSISTANCE;
        Checkin guardado = checkins.save(new Checkin(evento.getId(), usuario, estado, punto,
                asistencia ? TipoUbicacion.EDIFICIO : null, asistencia ? UUID.randomUUID() : null));
        entityManager.flush();
        return guardado;
    }

    private Checkin safe(Evento evento, UUID usuario, UUID punto) {
        Checkin guardado = checkins.save(new Checkin(evento.getId(), usuario, EstadoCheckin.SAFE, punto, null, null));
        entityManager.flush();
        return guardado;
    }

    private SolicitudAsistencia solicitud(Checkin checkin, EstadoEnvio estado, String idMs05) {
        SolicitudAsistencia solicitud = new SolicitudAsistencia(checkin.getId(), checkin.getEventoId(),
                checkin.getUsuarioId(), checkin.getUbicacionTipo(), checkin.getUbicacionId(),
                checkin.getRegistradoEn());
        solicitud.setEstadoEnvio(estado);
        solicitud.setSolicitudMs05Id(idMs05);
        return solicitudes.saveAndFlush(solicitud);
    }

    private void poblacion(Set<UUID> usuarios) {
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.of(usuarios));
    }

    @Test
    void losResultadosDeUnEventoFinalizadoTraenTodosLosDatosYSonFinales() {
        Evento evento = evento(EstadoEvento.FINALIZADO);
        UUID ana = UUID.randomUUID();
        UUID beto = UUID.randomUUID();
        UUID carla = UUID.randomUUID();
        UUID pendiente = UUID.randomUUID();
        poblacion(Set.of(ana, beto, carla, pendiente));

        Checkin pidioAyuda = checkin(evento, ana, EstadoCheckin.NEEDS_ASSISTANCE);
        solicitud(pidioAyuda, EstadoEnvio.ENVIADA, "SOL-001");
        safe(evento, ana, PUNTO_A);
        checkin(evento, beto, EstadoCheckin.EVACUATING);
        safe(evento, beto, PUNTO_B);
        checkin(evento, carla, EstadoCheckin.OFF_CAMPUS);

        Resultados r = service.resultados(evento.getId(), ORGANIZADOR);

        assertTrue(r.finales());
        assertEquals(EstadoEvento.FINALIZADO, r.estadoEvento());
        assertEquals(INICIO, r.fechaInicio());
        assertEquals(AHORA, r.fechaCierre());
        assertEquals(4L, r.poblacionObjetivo());
        assertEquals(3L, r.totalReportados());
        assertEquals(1L, r.totalPendientes());
        assertEquals(new TotalesPorEstado(2, 0, 0, 1), r.totalesPorEstado());
        assertEquals(Set.of(new TotalPorPunto(PUNTO_A, 1), new TotalPorPunto(PUNTO_B, 1)),
                Set.copyOf(r.porPuntoEncuentro()));
        // Ana ya no está en Requiero asistencia, pero lo reportó una vez: aparece con su solicitud
        assertEquals(1, r.solicitudesAsistencia().size());
        SolicitudResultado solicitud = r.solicitudesAsistencia().get(0);
        assertEquals(ana, solicitud.usuarioId());
        assertEquals("SOL-001", solicitud.solicitudAsistencia().id());
        assertEquals(EstadoEnvio.ENVIADA, solicitud.solicitudAsistencia().estadoEnvio());
        // Un primer check-in por usuario, en orden cronológico, con la fecha del primero y no del vigente
        assertEquals(List.of(ana, beto, carla), r.primerosCheckins().stream().map(PrimerCheckin::usuarioId).toList());
        assertEquals(pidioAyuda.getRegistradoEn(), r.primerosCheckins().get(0).primerCheckinEn());
    }

    @Test
    void losTotalesCoincidenConElConsolidado() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        poblacion(Set.of());
        for (int i = 0; i < 5; i++) {
            UUID usuario = UUID.randomUUID();
            checkin(evento, usuario, EstadoCheckin.EVACUATING);
            if (i % 2 == 0) {
                safe(evento, usuario, PUNTO_A);
            }
        }

        Resultados r = service.resultados(evento.getId(), ORGANIZADOR);

        Map<String, Long> consolidado = checkins.contarVigentesPorEstado(evento.getId()).stream()
                .collect(Collectors.toMap(CheckinRepository.ConteoPorEstado::getEstado,
                        CheckinRepository.ConteoPorEstado::getTotal));
        assertEquals(TotalesPorEstado.de(consolidado), r.totalesPorEstado());
        assertEquals(new TotalesPorEstado(3, 2, 0, 0), r.totalesPorEstado());
        assertEquals(5, r.totalReportados());
        assertEquals(r.totalReportados(), r.primerosCheckins().size());
        assertEquals(List.of(new TotalPorPunto(PUNTO_A, 3)), r.porPuntoEncuentro());
    }

    @Test
    void unEventoQueNoEstaFinalizadoEntregaResultadosParciales() {
        poblacion(Set.of());
        for (EstadoEvento estado : List.of(EstadoEvento.BORRADOR, EstadoEvento.PROGRAMADO,
                EstadoEvento.EN_EJECUCION)) {
            Resultados r = service.resultados(evento(estado).getId(), ORGANIZADOR);

            assertFalse(r.finales(), estado.name());
            assertNull(r.fechaCierre(), estado.name());
        }
    }

    @Test
    void unEventoSinCheckinsTraeLosCuatroEstadosEnCeroYTodaLaPoblacionPendiente() {
        Evento evento = evento(EstadoEvento.PROGRAMADO);
        poblacion(Set.of(UUID.randomUUID(), UUID.randomUUID()));

        Resultados r = service.resultados(evento.getId(), ORGANIZADOR);

        assertEquals(new TotalesPorEstado(0, 0, 0, 0), r.totalesPorEstado());
        assertEquals(0, r.totalReportados());
        assertEquals(2L, r.totalPendientes());
        assertTrue(r.porPuntoEncuentro().isEmpty());
        assertTrue(r.solicitudesAsistencia().isEmpty());
        assertTrue(r.primerosCheckins().isEmpty());
    }

    @Test
    void siMs01NoEntregaLaPoblacionLosDemasResultadosSeEntregan() {
        Evento evento = evento(EstadoEvento.FINALIZADO);
        checkin(evento, UUID.randomUUID(), EstadoCheckin.OFF_CAMPUS);
        when(poblacionObjetivo.usuariosActivosPorRoles(any(), any(), any())).thenReturn(Optional.empty());

        Resultados r = service.resultados(evento.getId(), ORGANIZADOR);

        assertNull(r.poblacionObjetivo());
        assertNull(r.totalPendientes());
        assertEquals(1, r.totalReportados());
        assertEquals(1, r.primerosCheckins().size());
    }

    @Test
    void quienReportoSinEstarEnLaPoblacionNoRestaPendientes() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        UUID enPoblacion = UUID.randomUUID();
        poblacion(Set.of(enPoblacion));
        checkin(evento, UUID.randomUUID(), EstadoCheckin.EVACUATING);

        Resultados r = service.resultados(evento.getId(), ORGANIZADOR);

        assertEquals(1L, r.poblacionObjetivo());
        assertEquals(1L, r.totalPendientes());
        assertEquals(1, r.totalReportados());
    }

    @Test
    void unaSolicitudPendienteDeEnvioApareceSinIdDeMs05() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        poblacion(Set.of());
        UUID usuario = UUID.randomUUID();
        solicitud(checkin(evento, usuario, EstadoCheckin.NEEDS_ASSISTANCE), EstadoEnvio.PENDIENTE_ENVIO, null);
        // Un segundo Requiero asistencia no duplica a la persona
        checkin(evento, usuario, EstadoCheckin.EVACUATING);
        checkin(evento, usuario, EstadoCheckin.NEEDS_ASSISTANCE);

        Resultados r = service.resultados(evento.getId(), ORGANIZADOR);

        assertEquals(1, r.solicitudesAsistencia().size());
        assertNull(r.solicitudesAsistencia().get(0).solicitudAsistencia().id());
        assertEquals(EstadoEnvio.PENDIENTE_ENVIO, r.solicitudesAsistencia().get(0).solicitudAsistencia().estadoEnvio());
    }

    @Test
    void unEventoInexistenteEsNoEncontradoSinConsultarAMs01() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.resultados(UUID.randomUUID(), ORGANIZADOR));

        assertEquals(ErrorCode.NOT_FOUND, error.getCode());
        org.mockito.Mockito.verifyNoInteractions(poblacionObjetivo);
    }

    @Test
    void laPoblacionSePideConLosRolesConvocadosYElCorteDelInicioDelEvento() {
        Evento evento = evento(EstadoEvento.EN_EJECUCION);
        poblacion(Set.of());

        service.resultados(evento.getId(), ORGANIZADOR);

        verify(poblacionObjetivo).usuariosActivosPorRoles(eq(List.of("ESTUDIANTE", "PROFESOR")), eq(INICIO),
                eq(ORGANIZADOR));
    }

    @Test
    void elCorteEsElMomentoDeLaConsultaSiElEventoNoHaEmpezado() {
        Evento borrador = evento(EstadoEvento.BORRADOR);
        Evento futuro = evento(EstadoEvento.PROGRAMADO);
        futuro.setFechaInicio(AHORA.plusSeconds(3600));

        assertEquals(AHORA, service.corteDePoblacion(borrador));
        assertEquals(AHORA, service.corteDePoblacion(futuro));
        assertEquals(INICIO, service.corteDePoblacion(evento(EstadoEvento.FINALIZADO)));
    }
}
