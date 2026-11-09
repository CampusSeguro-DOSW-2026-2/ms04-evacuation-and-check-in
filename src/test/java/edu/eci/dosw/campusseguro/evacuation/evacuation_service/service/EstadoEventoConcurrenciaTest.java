package edu.eci.dosw.campusseguro.evacuation.evacuation_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditRecorder;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.Escenario;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.EstadoEventoRequest;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.dto.ResultadoEstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.entity.EstadoEvento;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.repository.EventoRepository;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.PostgresTestcontainerConfig;

/**
 * RNF-MS04-18: la recepción del estado del evento es idempotente, dos llamadas simultáneas sobre el mismo evento
 * se atienden una tras otra sin errores 500 y responde en menos de 2000 ms. Sin transacción de prueba, para que
 * cada llamada use su propia conexión como en producción
 */
@SpringBootTest
@Import(PostgresTestcontainerConfig.class)
class EstadoEventoConcurrenciaTest {

    private static final Duration TIMEOUT_MS06 = Duration.ofMillis(2000);

    @Autowired
    private EstadoEventoService service;

    @Autowired
    private EventoRepository eventos;

    @MockitoBean
    private AuditRecorder auditoria;

    private static EstadoEventoRequest request(UUID eventoId, EstadoEvento estado) {
        return new EstadoEventoRequest(eventoId, "Simulacro concurrente", true, estado,
                estado == EstadoEvento.BORRADOR ? null : "2026-11-20T10:00:00-05:00",
                new Escenario(List.of("Bloque A"), List.of()), List.of("ESTUDIANTE"));
    }

    /** Lanza todas las llamadas a la vez; si alguna lanza excepción, la prueba falla */
    private static <T> List<T> enSimultaneo(int veces, Callable<T> tarea) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        try (ExecutorService hilos = Executors.newFixedThreadPool(veces)) {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < veces; i++) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        }
    }

    @Test
    void llamadasSimultaneasSobreUnEventoNuevoLoCreanUnaSolaVez() throws Exception {
        UUID eventoId = UUID.randomUUID();

        List<ResultadoEstadoEvento> resultados = enSimultaneo(10,
                () -> service.recibir(eventoId, request(eventoId, EstadoEvento.PROGRAMADO), "ms06", null));

        // Ninguna falla por la llave primaria: una lo crea (201) y las demás lo reprograman (200)
        assertEquals(1, resultados.stream().filter(ResultadoEstadoEvento::creado).count());
        assertEquals(EstadoEvento.PROGRAMADO, eventos.findById(eventoId).orElseThrow().getEstado());
    }

    @Test
    void finalizarDosVecesALaVezEsIdempotenteYConservaLaFechaDeCierre() throws Exception {
        UUID eventoId = UUID.randomUUID();
        service.recibir(eventoId, request(eventoId, EstadoEvento.EN_EJECUCION), "ms06", null);

        List<ResultadoEstadoEvento> resultados = enSimultaneo(2,
                () -> service.recibir(eventoId, request(eventoId, EstadoEvento.FINALIZADO), "ms06", null));

        assertEquals(EstadoEvento.FINALIZADO, eventos.findById(eventoId).orElseThrow().getEstado());
        assertEquals(resultados.get(0).evento().fechaCierre(), resultados.get(1).evento().fechaCierre());
    }

    @Test
    void respondeDentroDelTimeoutDeMs06() {
        UUID eventoId = UUID.randomUUID();
        // La primera llamada calienta el contexto y las conexiones; se mide la siguiente
        service.recibir(eventoId, request(eventoId, EstadoEvento.PROGRAMADO), "ms06", null);

        long inicio = System.nanoTime();
        service.recibir(eventoId, request(eventoId, EstadoEvento.EN_EJECUCION), "ms06", null);
        Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);

        assertTrue(duracion.compareTo(TIMEOUT_MS06) < 0, "RNF-MS04-18: tardó " + duracion.toMillis() + " ms");
    }
}
