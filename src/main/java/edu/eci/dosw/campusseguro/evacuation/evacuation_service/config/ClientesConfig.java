package edu.eci.dosw.campusseguro.evacuation.evacuation_service.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.Ms01PoblacionObjetivoClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.PoblacionObjetivoClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Clientes de las llamadas salientes de MS04 a otros microservicios, siempre a través del API Gateway con la clave
 * de servicio y el timeout de cliente de los Estándares (RNF-MS04-10, RNF-MS04-13)
 */
@Configuration
public class ClientesConfig {

    /**
     * Cliente de MS01 para la población objetivo de un evento (RN-MS04-15)
     *
     * @param jsonMapper lector de JSON de la aplicación
     * @param gatewayUrl URL base del Gateway; vacía deja a MS01 como no disponible
     * @param serviceKey clave de servicio de MS04
     * @param timeoutMs timeout de cliente en milisegundos
     * @return el cliente
     */
    @Bean
    public PoblacionObjetivoClient poblacionObjetivoClient(
            JsonMapper jsonMapper,
            @Value("${gateway.url:}") String gatewayUrl,
            @Value("${gateway.service-key:}") String serviceKey,
            @Value("${gateway.timeout-ms:2000}") long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        return new Ms01PoblacionObjetivoClient(httpClient, jsonMapper, gatewayUrl, serviceKey, timeout);
    }
}
