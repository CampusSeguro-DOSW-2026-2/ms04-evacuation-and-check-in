package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los nombres salen de MS01 con POST /users/batch y, si MS01 falla, la consulta sigue con el mapa vacío
 * (RN-MS04-25)
 */
@ExtendWith(MockitoExtension.class)
class UsuarioNombresClientTest {

    private static final UUID ANA = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");
    private static final UUID LUIS = UUID.fromString("5d2e8f10-6a4b-4c93-b7e1-3f9a0c2d4e68");

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private GatewayClient gateway;

    /**
     * Pide a MS01 los ids recibidos con el contrato de POST /users/batch
     */
    @Test
    void pideAMs01LosIdsConElContratoDelBatch() {
        when(gateway.post(eq(UsuarioNombresClientImpl.BATCH_PATH), any()))
                .thenReturn(json.readTree("{\"usuarios\":[],\"no_encontrados\":[]}"));

        new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA, LUIS));

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(gateway).post(eq("/api/v1/users/batch"), cuerpo.capture());
        assertEquals("{\"ids\":[\"" + ANA + "\",\"" + LUIS + "\"]}", cuerpo.getValue());
    }

    /**
     * Arma el nombre completo con los cuatro campos y omite el segundo nombre y el segundo apellido si no hay
     */
    @Test
    void armaElNombreCompletoOmitiendoLosCamposVacios() {
        when(gateway.post(any(), any())).thenReturn(json.readTree("""
                {"usuarios":[
                  {"id":"%s","primer_nombre":"Ana","segundo_nombre":"María","primer_apellido":"Pérez",
                   "segundo_apellido":"Gómez","correo":"ana@escuelaing.edu.co","roles":["ESTUDIANTE"]},
                  {"id":"%s","primer_nombre":"Luis","primer_apellido":"Rojas","segundo_nombre":"  "}
                ],"no_encontrados":[]}""".formatted(ANA, LUIS)));

        Map<UUID, String> nombres = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA, LUIS));

        assertEquals(Map.of(ANA, "Ana María Pérez Gómez", LUIS, "Luis Rojas"), nombres);
    }

    /**
     * Los usuarios que MS01 no encontró no aparecen en el mapa
     */
    @Test
    void losUsuariosNoEncontradosNoAparecen() {
        when(gateway.post(any(), any())).thenReturn(json.readTree("""
                {"usuarios":[{"id":"%s","primer_nombre":"Ana","primer_apellido":"Pérez"}],
                 "no_encontrados":["%s"]}""".formatted(ANA, LUIS)));

        Map<UUID, String> nombres = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA, LUIS));

        assertEquals(Map.of(ANA, "Ana Pérez"), nombres);
    }

    /**
     * Sin ids no llama a MS01
     */
    @Test
    void sinIdsNoLlamaAMs01() {
        Map<UUID, String> nombres = new UsuarioNombresClientImpl(gateway).nombresDe(List.of());

        assertTrue(nombres.isEmpty());
        verifyNoInteractions(gateway);
    }

    /**
     * Si MS01 no responde, devuelve el mapa vacío en lugar de fallar
     */
    @Test
    void siMs01NoRespondeDevuelveElMapaVacio() {
        when(gateway.post(any(), any())).thenThrow(ApiException.dependenciaNoDisponible());

        Map<UUID, String> nombres = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA));

        assertTrue(nombres.isEmpty());
    }

    /**
     * Si MS01 responde un id que no es UUID, devuelve el mapa vacío en lugar de fallar
     */
    @Test
    void siMs01RespondeUnIdInvalidoDevuelveElMapaVacio() {
        when(gateway.post(any(), any())).thenReturn(json.readTree(
                "{\"usuarios\":[{\"id\":\"no-es-uuid\",\"primer_nombre\":\"Ana\"}]}"));

        Map<UUID, String> nombres = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA));

        assertTrue(nombres.isEmpty());
    }
}
