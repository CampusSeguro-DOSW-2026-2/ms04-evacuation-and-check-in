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
 * The names come from MS01 with POST /users/batch and, if MS01 fails, the query goes on with an empty map
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
     * Asks MS01 for the received ids with the contract of POST /users/batch
     */
    @Test
    void asksMs01ForTheIdsWithTheBatchContract() {
        when(gateway.post(eq(UsuarioNombresClientImpl.BATCH_PATH), any()))
                .thenReturn(json.readTree("{\"usuarios\":[],\"no_encontrados\":[]}"));

        new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA, LUIS));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(gateway).post(eq("/api/v1/users/batch"), body.capture());
        assertEquals("{\"ids\":[\"" + ANA + "\",\"" + LUIS + "\"]}", body.getValue());
    }

    /**
     * Builds the full name with the four fields and skips the second name and the second surname when missing
     */
    @Test
    void buildsTheFullNameSkippingEmptyFields() {
        when(gateway.post(any(), any())).thenReturn(json.readTree("""
                {"usuarios":[
                  {"id":"%s","primer_nombre":"Ana","segundo_nombre":"María","primer_apellido":"Pérez",
                   "segundo_apellido":"Gómez","correo":"ana@escuelaing.edu.co","roles":["ESTUDIANTE"]},
                  {"id":"%s","primer_nombre":"Luis","primer_apellido":"Rojas","segundo_nombre":"  "}
                ],"no_encontrados":[]}""".formatted(ANA, LUIS)));

        Map<UUID, String> names = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA, LUIS));

        assertEquals(Map.of(ANA, "Ana María Pérez Gómez", LUIS, "Luis Rojas"), names);
    }

    /**
     * Delivers the full name and the email of each user; an empty or missing email stays null
     */
    @Test
    void deliversTheNameAndTheEmailOfEachUser() {
        when(gateway.post(any(), any())).thenReturn(json.readTree("""
                {"usuarios":[
                  {"id":"%s","primer_nombre":"Ana","primer_apellido":"Pérez","correo":" ana@escuelaing.edu.co "},
                  {"id":"%s","primer_nombre":"Luis","primer_apellido":"Rojas"}
                ],"no_encontrados":[]}""".formatted(ANA, LUIS)));

        Map<UUID, DatosUsuario> data = new UsuarioNombresClientImpl(gateway).datosDe(List.of(ANA, LUIS));

        assertEquals(Map.of(ANA, new DatosUsuario("Ana Pérez", "ana@escuelaing.edu.co"),
                LUIS, new DatosUsuario("Luis Rojas", null)), data);
    }

    /**
     * The users that MS01 did not find are not in the map
     */
    @Test
    void theUsersNotFoundAreNotInTheMap() {
        when(gateway.post(any(), any())).thenReturn(json.readTree("""
                {"usuarios":[{"id":"%s","primer_nombre":"Ana","primer_apellido":"Pérez"}],
                 "no_encontrados":["%s"]}""".formatted(ANA, LUIS)));

        Map<UUID, String> names = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA, LUIS));

        assertEquals(Map.of(ANA, "Ana Pérez"), names);
    }

    /**
     * Without ids MS01 is not called
     */
    @Test
    void withoutIdsMs01IsNotCalled() {
        Map<UUID, String> names = new UsuarioNombresClientImpl(gateway).nombresDe(List.of());

        assertTrue(names.isEmpty());
        verifyNoInteractions(gateway);
    }

    /**
     * If MS01 does not answer, the map is empty instead of failing
     */
    @Test
    void ifMs01DoesNotAnswerTheMapIsEmpty() {
        when(gateway.post(any(), any())).thenThrow(ApiException.dependenciaNoDisponible());

        Map<UUID, String> names = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA));

        assertTrue(names.isEmpty());
    }

    /**
     * If MS01 answers an id that is not a UUID, the map is empty instead of failing
     */
    @Test
    void ifMs01AnswersAnInvalidIdTheMapIsEmpty() {
        when(gateway.post(any(), any())).thenReturn(json.readTree(
                "{\"usuarios\":[{\"id\":\"no-es-uuid\",\"primer_nombre\":\"Ana\"}]}"));

        Map<UUID, String> names = new UsuarioNombresClientImpl(gateway).nombresDe(List.of(ANA));

        assertTrue(names.isEmpty());
    }
}
