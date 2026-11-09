package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Deja en el log las peticiones que tardan más que el umbral (RNF-MS04-01: 5 segundos por defecto), con su
 * Correlation ID, para diagnosticar los check-ins lentos durante una emergencia.
 *
 * Corre justo después de {@link CorrelationIdFilter}, así que el Correlation ID ya está en el MDC y encabeza la
 * línea del log, y el tiempo medido incluye la seguridad, el controller, el service y la base de datos. Solo
 * registra el método, la ruta, el estado HTTP y la duración: nunca cabeceras, cuerpos ni datos personales
 * (RNF-MS04-12)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PeticionLentaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PeticionLentaFilter.class);

    private final long umbralMs;
    private final LongSupplier relojNanos;

    /**
     * @param umbralMs duración a partir de la cual una petición se considera lenta (RENDIMIENTO_UMBRAL_LENTO_MS)
     */
    @Autowired
    public PeticionLentaFilter(@Value("${rendimiento.umbral-lento-ms:5000}") long umbralMs) {
        this(umbralMs, System::nanoTime);
    }

    /**
     * Constructor para las pruebas, con un reloj controlado
     *
     * @param umbralMs duración a partir de la cual una petición se considera lenta
     * @param relojNanos fuente de tiempo monotónica en nanosegundos
     */
    PeticionLentaFilter(long umbralMs, LongSupplier relojNanos) {
        this.umbralMs = umbralMs;
        this.relojNanos = relojNanos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long inicio = relojNanos.getAsLong();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duracionMs = TimeUnit.NANOSECONDS.toMillis(relojNanos.getAsLong() - inicio);
            if (duracionMs > umbralMs) {
                log.warn("Petición lenta: {} {} respondió {} en {} ms (umbral {} ms)", request.getMethod(),
                        request.getRequestURI(), response.getStatus(), duracionMs, umbralMs);
            }
        }
    }

    /**
     * El health de Actuator no se mide: no es una operación de MS04
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
