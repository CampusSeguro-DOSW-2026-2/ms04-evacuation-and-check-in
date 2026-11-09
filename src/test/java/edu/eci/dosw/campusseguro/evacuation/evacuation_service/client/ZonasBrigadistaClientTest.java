package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.client.ZonasBrigadistaClient.ZonasAsignadas;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.Rol;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las zonas del brigadista se piden a MS05 en su nombre y, si MS05 no las entrega, la consulta falla con 503
 * (RNF-MS04-11). El contrato de MS05 es supuesto: ver {@link ZonasBrigadistaClientImpl}
 */
@ExtendWith(MockitoExtension.class)
class ZonasBrigadistaClientTest {

    private static final UUID BRIGADISTA = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID ESPACIO = UUID.fromString("4e9c7a15-3b2d-4f60-a8e1-7c5d9b3f1a28");
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final RequesterIdentity IDENTIDAD = new RequesterIdentity(BRIGADISTA,
            EnumSet.of(Rol.BRIGADISTA, Rol.ESTUDIANTE));

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private GatewayClient gateway;

    /**
     * Llama en nombre del brigadista propagando su identidad sin modificarla y lee las tres listas de ids
     */
    @Test
    void llamaEnNombreDelBrigadistaYLeeLasTresListas() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADISTA, "BRIGADISTA,ESTUDIANTE"))
                .thenReturn(json.readTree("""
                        {"edificios":["%s"],"espacios":["%s"],"puntos_encuentro":["%s","%s"]}"""
                        .formatted(EDIFICIO, ESPACIO, PUNTO, PUNTO)));

        ZonasAsignadas zonas = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD);

        assertEquals(Set.of(EDIFICIO), zonas.edificios());
        assertEquals(Set.of(ESPACIO), zonas.espacios());
        assertEquals(Set.of(PUNTO), zonas.puntosEncuentro());
    }

    /**
     * Un brigadista sin zonas asignadas, o con listas que faltan, recibe conjuntos vacíos
     */
    @Test
    void sinZonasDevuelveConjuntosVacios() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADISTA, "BRIGADISTA,ESTUDIANTE"))
                .thenReturn(json.readTree("{\"edificios\":[]}"));

        ZonasAsignadas zonas = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD);

        assertTrue(zonas.edificios().isEmpty());
        assertTrue(zonas.espacios().isEmpty());
        assertTrue(zonas.puntosEncuentro().isEmpty());
    }

    /**
     * Si MS05 no responde, el 503 sube sin cambios
     */
    @Test
    void siMs05NoRespondeResponde503() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADISTA, "BRIGADISTA,ESTUDIANTE"))
                .thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class,
                () -> new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Si MS05 entrega ids que no son UUID, responde 503 en lugar de adivinar
     */
    @Test
    void siMs05EntregaIdsInvalidosResponde503() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADISTA, "BRIGADISTA,ESTUDIANTE"))
                .thenReturn(json.readTree("{\"puntos_encuentro\":[\"3\"]}"));

        ApiException error = assertThrows(ApiException.class,
                () -> new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }
}
