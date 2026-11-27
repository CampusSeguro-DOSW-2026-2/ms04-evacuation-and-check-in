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
 * RNF-MS04-01: the requests that exceed the threshold are logged with their correlation id
 */
class PeticionLentaFilterTest {

    private static final String CORRELATION = "c19b472d-7a2a-4d3b-a12b-abcdef123456";
    private static final String ROUTE = "/api/v1/chi/eventos/7c1d5b2e-4f3a-4e8b-9a61-0d2f8c3b5a17/checkins";

    private final AtomicLong nanos = new AtomicLong();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(PeticionLentaFilter.class);

    /**
     * Captures the log of the filter and sets the correlation id of the request
     */
    @BeforeEach
    void captureLog() {
        appender.start();
        logger.addAppender(appender);
        MDC.put(CorrelationIds.MDC_KEY, CORRELATION);
    }

    /**
     * Releases the log and the correlation id
     */
    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        MDC.clear();
    }

    /**
     * Runs a request whose processing takes the given milliseconds on the controlled clock
     *
     * @param uri path of the request
     * @param durationMs duration of the processing
     * @param status status of the answer
     * @return the answer
     * @throws Exception if the filter fails
     */
    private MockHttpServletResponse run(String uri, long durationMs, int status) throws Exception {
        PeticionLentaFilter filter = new PeticionLentaFilter(5000, nanos::get);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(durationMs));
            response.setStatus(status);
        };
        filter.doFilter(request, response, chain);
        return response;
    }

    /**
     * A request slower than the threshold is logged with its correlation id
     *
     * @throws Exception if the filter fails
     */
    @Test
    void aRequestSlowerThanTheThresholdIsLogged() throws Exception {
        run(ROUTE, 5001, 201);

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertEquals(CORRELATION, event.getMDCPropertyMap().get(CorrelationIds.MDC_KEY));
        assertEquals("Petición lenta: POST " + ROUTE + " respondió 201 en 5001 ms (umbral 5000 ms)",
                event.getFormattedMessage());
    }

    /**
     * A request within the threshold is not logged
     *
     * @throws Exception if the filter fails
     */
    @Test
    void aRequestWithinTheThresholdIsNotLogged() throws Exception {
        run(ROUTE, 5000, 201);
        run(ROUTE, 120, 200);

        assertTrue(appender.list.isEmpty());
    }

    /**
     * A slow request is also logged when it ends with an exception
     */
    @Test
    void aSlowRequestIsLoggedEvenWhenItFails() {
        PeticionLentaFilter filter = new PeticionLentaFilter(5000, nanos::get);
        FilterChain chain = (req, res) -> {
            nanos.addAndGet(TimeUnit.SECONDS.toNanos(7));
            throw new IllegalStateException("fallo");
        };

        assertThrows(IllegalStateException.class, () -> filter.doFilter(new MockHttpServletRequest("POST", ROUTE),
                new MockHttpServletResponse(), chain));
        assertEquals(1, appender.list.size());
    }

    /**
     * The log line includes neither headers nor body
     *
     * @throws Exception if the filter fails
     */
    @Test
    void theLogIncludesNeitherHeadersNorBody() throws Exception {
        run(ROUTE, 6000, 201);

        String line = appender.list.get(0).getFormattedMessage();
        assertFalse(line.contains("X-User-Id"));
        assertFalse(line.contains("estado"));
    }

    /**
     * The health is not measured
     *
     * @throws Exception if the filter fails
     */
    @Test
    void theHealthIsNotMeasured() throws Exception {
        run("/actuator/health", 9000, 200);

        assertTrue(appender.list.isEmpty());
    }
}
