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
 * The configuration builds the MS01 and MS05 clients on the same Gateway client and, without Gateway URL, the calls
 * fail as an unavailable dependency instead of preventing the start
 */
class GatewayClientsConfigTest {

    private final GatewayClientsConfig config = new GatewayClientsConfig();

    /**
     * Creates the three beans with the Gateway not configured
     */
    @Test
    void createsTheClientsEvenWithoutGatewayUrl() {
        GatewayClient gateway = config.gatewayClient("", "", 2000);

        UsuarioNombresClient names = config.usuarioNombresClient(gateway);
        ZonasBrigadistaClient zones = config.zonasBrigadistaClient(gateway);

        assertInstanceOf(UsuarioNombresClientImpl.class, names);
        assertInstanceOf(ZonasBrigadistaClientImpl.class, zones);
    }

    /**
     * Without Gateway URL the names are empty and the zones fail with 503, without errors when starting
     */
    @Test
    void withoutGatewayTheNamesAreEmptyAndTheZonesFailWith503() {
        GatewayClient gateway = config.gatewayClient("", "", 2000);
        UsuarioNombresClient names = config.usuarioNombresClient(gateway);

        assertEquals(0, names.nombresDe(List.of(UUID.randomUUID())).size());
        ApiException error = assertThrows(ApiException.class,
                () -> gateway.get("/api/v1/panel/zones", null, null, null));
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }
}
