package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;
import tools.jackson.databind.json.JsonMapper;

/**
 * The zones of the brigadier are requested to MS05 on behalf of the brigadier and, if MS05 does not deliver them, the
 * query fails with 503 (RNF-MS04-11). They are read from the responsibilities panel of MS05: see
 * {@link ZonasBrigadistaClientImpl}
 */
@ExtendWith(MockitoExtension.class)
class ZonasBrigadistaClientTest {

    private static final UUID BRIGADIER = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID BUILDING = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID SPACE = UUID.fromString("4e9c7a15-3b2d-4f60-a8e1-7c5d9b3f1a28");
    private static final UUID SAFE_ZONE = UUID.fromString("1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b");
    private static final UUID POINT = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EVENT = UUID.fromString("5d8a2c61-9e4b-4f37-8b1a-3c6e0d9f2a74");
    private static final UUID OTHER_EVENT = UUID.fromString("8e3b1f05-6a2d-4c49-9f7e-1b4d6a0c3e58");
    private static final RequesterIdentity IDENTITY = TestIdentities.identity(BRIGADIER, "BRIGADISTA", "ESTUDIANTE");

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private GatewayClient gateway;

    /**
     * Assignment of the brigadier in the queried event
     *
     * @param type type of location
     * @param location id of the location
     * @param state state of the assignment
     * @return the assignment in JSON
     */
    private static String assignment(String type, UUID location, String state) {
        return assignment(EVENT, type, location, state);
    }

    /**
     * Assignment of the brigadier in an event
     *
     * @param event event of the assignment
     * @param type type of location
     * @param location id of the location
     * @param state state of the assignment
     * @return the assignment in JSON
     */
    private static String assignment(UUID event, String type, UUID location, String state) {
        return """
                {"idAsignacion":"%s","eventoId":"%s","ubicacionId":"%s","tipoUbicacion":"%s","estado":"%s"}"""
                .formatted(UUID.randomUUID(), event, location, type, state);
    }

    /**
     * Makes the panel of MS05 answer the given assignments
     *
     * @param assignments assignments in JSON separated by commas
     */
    private void answer(String assignments) {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADIER, "BRIGADISTA,ESTUDIANTE",
                IDENTITY.permisosComoCabecera()))
                .thenReturn(json.readTree("""
                        {"asignaciones":[%s],"tareasPendientes":[],
                         "metadatosPaginacion":{"pagina":0,"tamano":1,"totalElementos":0,"totalPaginas":0}}"""
                        .formatted(assignments)));
    }

    /**
     * Calls /api/v1/responsabilidades/mias on behalf of the brigadier, without changing the identity, and splits the
     * current assignments by location type
     */
    @Test
    void callsThePanelOfMs05OnBehalfOfTheBrigadierAndSplitsByType() {
        answer(String.join(",", assignment("EDIFICIO", BUILDING, "VIGENTE"),
                assignment("ESPACIO", SPACE, "VIGENTE"),
                assignment("PUNTO_ENCUENTRO", POINT, "VIGENTE"),
                assignment("ZONA_SEGURA", SAFE_ZONE, "VIGENTE")));

        ZonasAsignadas zones = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTITY, EVENT);

        assertEquals(Set.of(BUILDING), zones.edificios());
        assertEquals(Set.of(SPACE), zones.espacios());
        assertEquals(Set.of(POINT, SAFE_ZONE), zones.puntosEncuentro());
    }

    /**
     * The withdrawn assignments do not count, and a type that MS04 does not know is ignored
     */
    @Test
    void ignoresWithdrawnAssignmentsAndUnknownTypes() {
        answer(String.join(",", assignment("EDIFICIO", BUILDING, "RETIRADA"),
                assignment("OTRO", POINT, "VIGENTE")));

        ZonasAsignadas zones = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTITY, EVENT);

        assertTrue(zones.edificios().isEmpty());
        assertTrue(zones.espacios().isEmpty());
        assertTrue(zones.puntosEncuentro().isEmpty());
    }

    /**
     * The current assignments of another event do not count: the brigadier only sees the zones of the queried event
     */
    @Test
    void ignoresTheAssignmentsOfOtherEvents() {
        answer(String.join(",", assignment(OTHER_EVENT, "EDIFICIO", BUILDING, "VIGENTE"),
                assignment(OTHER_EVENT, "PUNTO_ENCUENTRO", POINT, "VIGENTE"),
                assignment("ESPACIO", SPACE, "VIGENTE")));

        ZonasAsignadas zones = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTITY, EVENT);

        assertTrue(zones.edificios().isEmpty());
        assertEquals(Set.of(SPACE), zones.espacios());
        assertTrue(zones.puntosEncuentro().isEmpty());
    }

    /**
     * A brigadier without assignments, or an answer without the list, gets empty sets
     */
    @Test
    void withoutAssignmentsReturnsEmptySets() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADIER, "BRIGADISTA,ESTUDIANTE",
                IDENTITY.permisosComoCabecera()))
                .thenReturn(json.readTree("{\"tareasPendientes\":[]}"));

        ZonasAsignadas zones = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTITY, EVENT);

        assertTrue(zones.edificios().isEmpty());
        assertTrue(zones.espacios().isEmpty());
        assertTrue(zones.puntosEncuentro().isEmpty());
    }

    /**
     * If MS05 does not answer, the 503 goes up unchanged
     */
    @Test
    void ifMs05DoesNotAnswerTheResultIs503() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADIER, "BRIGADISTA,ESTUDIANTE",
                IDENTITY.permisosComoCabecera()))
                .thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class,
                () -> new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTITY, EVENT));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * A location id that is not a UUID cannot match any location of MS04: it is ignored and the other assignments
     * still count
     */
    @Test
    void ignoresAssignmentsWithIdsThatAreNotUuids() {
        answer("""
                {"eventoId":"%s","ubicacionId":"ZONA-001","tipoUbicacion":"ZONA_SEGURA","estado":"VIGENTE"},"""
                .formatted(EVENT) + assignment("EDIFICIO", BUILDING, "VIGENTE"));

        ZonasAsignadas zones = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTITY, EVENT);

        assertEquals(Set.of(BUILDING), zones.edificios());
        assertTrue(zones.puntosEncuentro().isEmpty());
    }
}
