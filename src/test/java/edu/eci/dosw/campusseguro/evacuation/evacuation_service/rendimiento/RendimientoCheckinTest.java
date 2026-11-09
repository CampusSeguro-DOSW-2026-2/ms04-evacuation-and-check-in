package edu.eci.dosw.campusseguro.evacuation.evacuation_service.rendimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.CheckinRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Consolidado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.TotalesPorEstado;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoCheckin;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.Evento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.CheckinService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.service.DashboardService;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * Rendimiento y concurrencia del check-in (historia KAIR-606, subtareas KAIR-680 y KAIR-681), con la aplicación
 * completa y su configuración real (pool de conexiones incluido) sobre PostgreSQL con el esquema de database/:
 *
 * - RNF-MS04-02 y RNF-MS04-04: 500 check-ins simultáneos sin pérdida, sin duplicados y con un solo estado vigente
 *   por usuario; los reportes simultáneos del mismo usuario se serializan sin 409 ni 500
 * - RNF-MS04-01: al menos el 80 % de los check-ins responde en menos de 5 s
 * - RNF-MS04-06: un check-in se refleja en el consolidado en menos de 5 s
 *
 * Se usan los estados que no consultan a otros servicios (EVACUATING y OFF_CAMPUS); los cuatro estados, con dobles
 * de MS02 y MS05 y a través de HTTP, los cubre la prueba de carga de rendimiento/k6. Están marcadas con la etiqueta
 * "rendimiento" y no corren con {@code mvn verify}: se ejecutan con {@code mvn verify -Prendimiento}. Necesitan
 * RF-MS04-02/03 (CheckinService) y RF-MS04-06 (DashboardService) implementados
 */
@Tag("rendimiento")
@SpringBootTest
@Import(PostgresTestcontainerConfig.class)
class RendimientoCheckinTest {

    private static final Logger log = LoggerFactory.getLogger(RendimientoCheckinTest.class);

    /** RNF-MS04-02: usuarios simultáneos */
    private static final int USUARIOS_SIMULTANEOS = 500;
    /** RNF-MS04-01 y RNF-MS04-06: tiempo máximo */
    private static final Duration LIMITE = Duration.ofSeconds(5);
    /** RNF-MS04-01: porcentaje mínimo de check-ins por debajo del límite */
    private static final double PORCENTAJE_MINIMO = 0.80;

    private static final RequesterIdentity COORDINADOR = new RequesterIdentity(UUID.randomUUID(),
            EnumSet.of(Rol.COORDINADOR));

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EventoRepository eventos;

    @Autowired
    private JdbcTemplate jdbc;

    /** Resultado de un check-in con lo que tardó */
    private record Medicion(ResultadoCheckin resultado, Duration duracion) {
    }

    private Evento eventoEnEjecucion() {
        Evento evento = new Evento(UUID.randomUUID());
        evento.setNombre("Prueba de carga del check-in");
        evento.setSimulacro(true);
        evento.setEstado(EstadoEvento.EN_EJECUCION);
        evento.setFechaInicio(Instant.now());
        evento.setRolesConvocados(List.of("ESTUDIANTE", "PROFESOR", "ADMINISTRATIVO"));
        return eventos.saveAndFlush(evento);
    }

    private static RequesterIdentity estudiante(UUID usuario) {
        return new RequesterIdentity(usuario, EnumSet.of(Rol.ESTUDIANTE));
    }

    private Medicion reportar(UUID eventoId, UUID usuario, EstadoCheckin estado) {
        long inicio = System.nanoTime();
        ResultadoCheckin resultado = checkinService.registrar(eventoId, new CheckinRequest(estado, null, null),
                estudiante(usuario), UUID.randomUUID().toString());
        return new Medicion(resultado, Duration.ofNanos(System.nanoTime() - inicio));
    }

    /**
     * Lanza todas las tareas a la vez: cada hilo espera la señal de salida para que las peticiones lleguen juntas
     */
    private static <T> List<T> enSimultaneo(List<Callable<T>> tareas) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        try (ExecutorService hilos = Executors.newFixedThreadPool(tareas.size())) {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                // Si un check-in falló (409, 500, saturación del pool...) la excepción se propaga y la prueba falla
                resultados.add(futuro.get());
            }
            return resultados;
        }
    }

    private long contar(String sql, Object... args) {
        Long total = jdbc.queryForObject(sql, Long.class, args);
        return total == null ? 0 : total;
    }

    private static Duration percentil(List<Duration> duraciones, double p) {
        List<Duration> ordenadas = new ArrayList<>(duraciones);
        Collections.sort(ordenadas);
        int indice = (int) Math.ceil(p * ordenadas.size()) - 1;
        return ordenadas.get(Math.max(0, indice));
    }

    @Test
    void quinientosCheckinsSimultaneosSinPerdidaSinDuplicadosYDentroDelTiempo() throws Exception {
        UUID eventoId = eventoEnEjecucion().getId();
        List<Callable<Medicion>> tareas = new ArrayList<>();
        for (int i = 0; i < USUARIOS_SIMULTANEOS; i++) {
            UUID usuario = UUID.randomUUID();
            EstadoCheckin estado = i % 2 == 0 ? EstadoCheckin.EVACUATING : EstadoCheckin.OFF_CAMPUS;
            tareas.add(() -> reportar(eventoId, usuario, estado));
        }

        List<Medicion> mediciones = enSimultaneo(tareas);

        // Sin pérdida y sin duplicados: un check-in por usuario, todos nuevos (201)
        assertTrue(mediciones.stream().allMatch(m -> m.resultado().creado()), "todos son el primer check-in");
        assertEquals(USUARIOS_SIMULTANEOS, contar("SELECT COUNT(*) FROM checkins WHERE evento_id = ?", eventoId));
        assertEquals(USUARIOS_SIMULTANEOS,
                contar("SELECT COUNT(DISTINCT usuario_id) FROM checkins WHERE evento_id = ?", eventoId));
        // Un solo estado vigente por usuario, y el consolidado los cuenta a todos
        assertEquals(USUARIOS_SIMULTANEOS,
                contar("SELECT COUNT(*) FROM v_checkins_vigentes WHERE evento_id = ?", eventoId));
        Consolidado consolidado = dashboardService.consolidado(eventoId, COORDINADOR);
        assertEquals(USUARIOS_SIMULTANEOS, consolidado.totalReportados());
        assertEquals(new TotalesPorEstado(0, USUARIOS_SIMULTANEOS / 2, 0, USUARIOS_SIMULTANEOS / 2),
                consolidado.totalesPorEstado());

        // RNF-MS04-01: al menos el 80 % por debajo de 5 s
        List<Duration> duraciones = mediciones.stream().map(Medicion::duracion).toList();
        long dentroDelLimite = duraciones.stream().filter(d -> d.compareTo(LIMITE) < 0).count();
        log.info("Carga de {} check-ins: p50={} ms, p80={} ms, p95={} ms, máx={} ms, {} de {} por debajo de {} s",
                USUARIOS_SIMULTANEOS, percentil(duraciones, 0.50).toMillis(), percentil(duraciones, 0.80).toMillis(),
                percentil(duraciones, 0.95).toMillis(), percentil(duraciones, 1.0).toMillis(), dentroDelLimite,
                USUARIOS_SIMULTANEOS, LIMITE.toSeconds());
        assertTrue(dentroDelLimite >= Math.ceil(PORCENTAJE_MINIMO * USUARIOS_SIMULTANEOS),
                "RNF-MS04-01: solo " + dentroDelLimite + " de " + USUARIOS_SIMULTANEOS + " por debajo de 5 s");
    }

    @Test
    void losReportesSimultaneosDelMismoUsuarioSeAtiendenUnoTrasOtro() throws Exception {
        UUID eventoId = eventoEnEjecucion().getId();
        UUID usuario = UUID.randomUUID();
        int reportes = 50;
        List<Callable<Medicion>> tareas = new ArrayList<>();
        for (int i = 0; i < reportes; i++) {
            EstadoCheckin estado = i % 2 == 0 ? EstadoCheckin.EVACUATING : EstadoCheckin.OFF_CAMPUS;
            tareas.add(() -> reportar(eventoId, usuario, estado));
        }

        // Ninguno responde 409 ni 500: si alguno lanzara excepción, enSimultaneo la propaga
        List<Medicion> mediciones = enSimultaneo(tareas);

        // Solo uno es el primer check-in (201); los demás son cambios o repeticiones (200)
        assertEquals(1, mediciones.stream().filter(m -> m.resultado().creado()).count());
        // El estado vigente es el último check-in confirmado (el de mayor número)
        UUID ultimo = jdbc.queryForObject("SELECT id FROM checkins WHERE evento_id = ? AND usuario_id = ? "
                + "ORDER BY registrado_en DESC, numero DESC LIMIT 1", UUID.class, eventoId, usuario);
        UUID vigente = jdbc.queryForObject("SELECT id FROM v_checkins_vigentes WHERE evento_id = ? "
                + "AND usuario_id = ?", UUID.class, eventoId, usuario);
        assertEquals(ultimo, vigente);
        assertEquals(1, dashboardService.consolidado(eventoId, COORDINADOR).totalReportados());
        // Como se serializan, dos check-ins seguidos nunca repiten el mismo estado (RF-MS04-03, A3)
        assertEquals(0, contar("SELECT COUNT(*) FROM (SELECT estado, LAG(estado) OVER (ORDER BY registrado_en, "
                + "numero) AS anterior FROM checkins WHERE evento_id = ? AND usuario_id = ?) t "
                + "WHERE estado = anterior", eventoId, usuario));
    }

    @Test
    void unCheckinSeReflejaEnElConsolidadoEnMenosDeCincoSegundos() throws Exception {
        UUID eventoId = eventoEnEjecucion().getId();
        List<Duration> tiempos = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            long inicio = System.nanoTime();
            reportar(eventoId, UUID.randomUUID(), EstadoCheckin.EVACUATING);
            long esperado = i;
            // Se consulta el consolidado hasta que cuenta el nuevo check-in, con el límite de 5 s
            while (dashboardService.consolidado(eventoId, COORDINADOR).totalReportados() < esperado) {
                assertTrue(System.nanoTime() - inicio < LIMITE.toNanos(),
                        "RNF-MS04-06: el check-in " + i + " no se reflejó en el consolidado en 5 s");
                Thread.sleep(50);
            }
            tiempos.add(Duration.ofNanos(System.nanoTime() - inicio));
        }

        log.info("Reflejo en el consolidado: p80={} ms, máx={} ms", percentil(tiempos, 0.80).toMillis(),
                percentil(tiempos, 1.0).toMillis());
        assertTrue(tiempos.stream().allMatch(t -> t.compareTo(LIMITE) < 0));
    }
}
