package edu.eci.dosw.campusseguro.evacuation.evacuation_service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.GatewayClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.UsuarioNombresClientImpl;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClientImpl;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;

/**
 * La configuración arma los clientes de MS01 y MS05 sobre el mismo cliente del Gateway y, sin URL del Gateway, las
 * llamadas fallan como dependencia no disponible en lugar de impedir el arranque
 */
class GatewayClientsConfigTest {

    private final GatewayClientsConfig config = new GatewayClientsConfig();

    /**
     * Crea los tres beans con el Gateway sin configurar
     */
    @Test
    void creaLosClientesAunSinUrlDelGateway() {
        GatewayClient gateway = config.gatewayClient("", "", 2000);

        UsuarioNombresClient nombres = config.usuarioNombresClient(gateway);
        ZonasBrigadistaClient zonas = config.zonasBrigadistaClient(gateway);

        assertInstanceOf(UsuarioNombresClientImpl.class, nombres);
        assertInstanceOf(ZonasBrigadistaClientImpl.class, zonas);
    }

    /**
     * Sin URL del Gateway los nombres salen vacíos y las zonas fallan con 503, sin lanzar errores al arrancar
     */
    @Test
    void sinGatewayLosNombresSalenVaciosYLasZonasFallanCon503() {
        GatewayClient gateway = config.gatewayClient("", "", 2000);
        UsuarioNombresClient nombres = config.usuarioNombresClient(gateway);

        assertEquals(0, nombres.nombresDe(List.of(UUID.randomUUID())).size());
        ApiException error = assertThrows(ApiException.class, () -> gateway.get("/api/v1/panel/zones", null, null));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }
}
