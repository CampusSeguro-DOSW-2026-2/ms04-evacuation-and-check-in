package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.CorrelationIds;
import jakarta.servlet.FilterChain;

/**
 * RNF-MS04-01: las peticiones que superan el umbral quedan en el log con su Correlation ID
 */
class PeticionLentaFilterTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String RUTA = "/api/v1/chi/eventos/7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17/checkins";

    private final AtomicLong nanos = new AtomicLong();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(PeticionLentaFilter.class);

    @BeforeEach
    void capturarLog() {
        appender.start();
        logger.addAppender(appender);
        MDC.put(CorrelationIds.MDC_KEY, CORRELATION);
    }

    @AfterEach
    void soltarLog() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    /** Petición cuyo procesamiento "tarda" los milisegundos indicados en el reloj controlado */
    private MockHttpServletResponse ejecutar(String uri, long duracionMs, int status) throws Exception {
        PeticionLentaFilter filtro = new PeticionLentaFilter(5000, nanos::get);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadena = (req, res) -> {
            nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(duracionMs));
            response.setStatus(status);
        };
        filtro.doFilter(request, response, cadena);
        return response;
    }

    @Test
    void unaPeticionMasLentaQueElUmbralQuedaEnElLogConSuCorrelationId() throws Exception {
        ejecutar(RUTA, 5001, 201);

        assertEquals(1, appender.list.size());
        ILoggingEvent evento = appender.list.get(0);
        assertEquals(Level.WARN, evento.getLevel());
        assertEquals(CORRELATION, evento.getMDCPropertyMap().get(CorrelationIds.MDC_KEY));
        assertEquals("Petición lenta: POST " + RUTA + " respondió 201 en 5001 ms (umbral 5000 ms)",
                evento.getFormattedMessage());
    }

    @Test
    void unaPeticionDentroDelUmbralNoSeRegistra() throws Exception {
        ejecutar(RUTA, 5000, 201);
        ejecutar(RUTA, 120, 200);

        assertTrue(appender.list.isEmpty());
    }

    @Test
    void tambienSeRegistraSiLaPeticionTerminaConExcepcion() {
        PeticionLentaFilter filtro = new PeticionLentaFilter(5000, nanos::get);
        FilterChain cadena = (req, res) -> {
            nanos.addAndGet(TimeUnit.SECONDS.toNanos(7));
            throw new IllegalStateException("fallo");
        };

        assertThrows(IllegalStateException.class, () -> filtro.doFilter(new MockHttpServletRequest("POST", RUTA),
                new MockHttpServletResponse(), cadena));
        assertEquals(1, appender.list.size());
    }

    @Test
    void elRegistroNoIncluyeCabecerasNiCuerpo() throws Exception {
        ejecutar(RUTA, 6000, 201);

        String linea = appender.list.get(0).getFormattedMessage();
        assertFalse(linea.contains("X-User-Id"));
        assertFalse(linea.contains("estado"));
    }

    @Test
    void elHealthNoSeMide() throws Exception {
        ejecutar("/actuator/health", 9000, 200);

        assertTrue(appender.list.isEmpty());
    }
}
