package edu.eci.dosw.campusseguro.evacuation.evacuation_service.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.GatewayClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClientImpl;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClientImpl;

/**
 * Configuración de las llamadas de MS04 a MS01 y MS05 a través del Gateway. La URL, la clave de servicio y el
 * timeout llegan por variables de entorno (RNF-MS04-13) y el timeout es el de las llamadas internas (2000 ms)
 */
@Configuration
public class GatewayClientsConfig {

    /**
     * Cliente del API Gateway que comparten los clientes de MS01 y MS05
     *
     * @param gatewayUrl URL base del Gateway; vacía hace fallar las llamadas como dependencia no disponible
     * @param serviceKey clave de servicio de MS04
     * @param timeoutMs timeout de cliente en milisegundos
     * @return el cliente del Gateway
     */
    @Bean
    public GatewayClient gatewayClient(
            @Value("${gateway.url:}") String gatewayUrl,
            @Value("${gateway.service-key:}") String serviceKey,
            @Value("${gateway.timeout-ms:2000}") long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
        return new GatewayClient(httpClient, gatewayUrl, serviceKey, timeout);
    }

    /**
     * Cliente de los nombres de MS01
     *
     * @param gateway cliente del API Gateway
     * @return el cliente de nombres
     */
    @Bean
    public UsuarioNombresClient usuarioNombresClient(GatewayClient gateway) {
        return new UsuarioNombresClientImpl(gateway);
    }

    /**
     * Cliente de las zonas de brigadista de MS05
     *
     * @param gateway cliente del API Gateway
     * @return el cliente de zonas
     */
    @Bean
    public ZonasBrigadistaClient zonasBrigadistaClient(GatewayClient gateway) {
        return new ZonasBrigadistaClientImpl(gateway);
    }
}
