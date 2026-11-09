package edu.eci.dosw.campusseguro.evacuation.evacuation_service.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditEventPublisher;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.audit.AuditEventPublisherImpl;

/**
 * Configuración de la auditoría hacia MS08. La URL del Gateway y la clave de servicio llegan por variables de
 * entorno (RNF-MS04-13); el timeout de cliente es el de las llamadas internas (RNF-MS04-10)
 */
@Configuration
public class AuditConfig {

    /**
     * Reloj de la aplicación, en UTC; las pruebas lo reemplazan por uno fijo
     *
     * @return el reloj del sistema en UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Publicador de eventos de auditoría
     *
     * @param gatewayUrl URL base del Gateway; vacía deja la auditoría solo en el log
     * @param serviceKey clave de servicio de MS04
     * @param timeoutMs timeout de cliente en milisegundos
     * @return el publicador
     */
    @Bean
    public AuditEventPublisher auditEventPublisher(
            @Value("${gateway.url:}") String gatewayUrl,
            @Value("${gateway.service-key:}") String serviceKey,
            @Value("${gateway.timeout-ms:2000}") long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        return new AuditEventPublisherImpl(httpClient, gatewayUrl, serviceKey, timeout);
    }
}
