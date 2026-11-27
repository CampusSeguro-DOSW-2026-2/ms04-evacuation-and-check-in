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
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.IdentidadesDePrueba;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las zonas del brigadista se piden a MS05 en su nombre y, si MS05 no las entrega, la consulta falla con 503
 * (RNF-MS04-11). Se leen del panel de responsabilidades de MS05: ver {@link ZonasBrigadistaClientImpl}
 */
@ExtendWith(MockitoExtension.class)
class ZonasBrigadistaClientTest {

    private static final UUID BRIGADISTA = UUID.fromString("6c0d2f48-1b3e-4a57-9d8c-2e4f6a8b0c13");
    private static final UUID EDIFICIO = UUID.fromString("0b6f3d1a-2c4e-4f58-8a97-5d3e1b7c9a20");
    private static final UUID ESPACIO = UUID.fromString("4e9c7a15-3b2d-4f60-a8e1-7c5d9b3f1a28");
    private static final UUID ZONA_SEGURA = UUID.fromString("1f2e3d4c-5b6a-4978-8a9b-0c1d2e3f4a5b");
    private static final UUID PUNTO = UUID.fromString("7a1d4e2b-5c3f-4a8e-b9d0-2e6f1c4a7b35");
    private static final UUID EVENTO = UUID.fromString("5d8a2c61-9e4b-4f37-8b1a-3c6e0d9f2a74");
    private static final UUID OTRO_EVENTO = UUID.fromString("8e3b1f05-6a2d-4c49-9f7e-1b4d6a0c3e58");
    private static final RequesterIdentity IDENTIDAD = IdentidadesDePrueba.identidad(BRIGADISTA, "BRIGADISTA", "ESTUDIANTE");

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private GatewayClient gateway;

    private static String asignacion(String tipo, UUID ubicacion, String estado) {
        return asignacion(EVENTO, tipo, ubicacion, estado);
    }

    private static String asignacion(UUID evento, String tipo, UUID ubicacion, String estado) {
        return """
                {"idAsignacion":"%s","eventoId":"%s","ubicacionId":"%s","tipoUbicacion":"%s","estado":"%s"}"""
                .formatted(UUID.randomUUID(), evento, ubicacion, tipo, estado);
    }

    private void respuesta(String asignaciones) {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADISTA, "BRIGADISTA,ESTUDIANTE",
                IDENTIDAD.permisosComoCabecera()))
                .thenReturn(json.readTree("""
                        {"asignaciones":[%s],"tareasPendientes":[],
                         "metadatosPaginacion":{"pagina":0,"tamano":1,"totalElementos":0,"totalPaginas":0}}"""
                        .formatted(asignaciones)));
    }

    /**
     * Llama a /api/v1/responsabilidades/mias en nombre del brigadista, sin modificar su identidad, y reparte las
     * asignaciones vigentes por tipo de ubicación
     */
    @Test
    void llamaAlPanelDeMs05EnNombreDelBrigadistaYReparteLasAsignacionesPorTipo() {
        respuesta(String.join(",", asignacion("EDIFICIO", EDIFICIO, "VIGENTE"),
                asignacion("ESPACIO", ESPACIO, "VIGENTE"),
                asignacion("PUNTO_ENCUENTRO", PUNTO, "VIGENTE"),
                asignacion("ZONA_SEGURA", ZONA_SEGURA, "VIGENTE")));

        ZonasAsignadas zonas = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD, EVENTO);

        assertEquals(Set.of(EDIFICIO), zonas.edificios());
        assertEquals(Set.of(ESPACIO), zonas.espacios());
        assertEquals(Set.of(PUNTO, ZONA_SEGURA), zonas.puntosEncuentro());
    }

    /**
     * Las asignaciones retiradas no cuentan, y un tipo que MS04 no conoce se ignora
     */
    @Test
    void ignoraLasAsignacionesRetiradasYLosTiposDesconocidos() {
        respuesta(String.join(",", asignacion("EDIFICIO", EDIFICIO, "RETIRADA"),
                asignacion("OTRO", PUNTO, "VIGENTE")));

        ZonasAsignadas zonas = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD, EVENTO);

        assertTrue(zonas.edificios().isEmpty());
        assertTrue(zonas.espacios().isEmpty());
        assertTrue(zonas.puntosEncuentro().isEmpty());
    }

    /**
     * Las asignaciones vigentes de otro evento no cuentan: el brigadista solo ve sus zonas en el evento consultado
     */
    @Test
    void ignoraLasAsignacionesDeOtrosEventos() {
        respuesta(String.join(",", asignacion(OTRO_EVENTO, "EDIFICIO", EDIFICIO, "VIGENTE"),
                asignacion(OTRO_EVENTO, "PUNTO_ENCUENTRO", PUNTO, "VIGENTE"),
                asignacion("ESPACIO", ESPACIO, "VIGENTE")));

        ZonasAsignadas zonas = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD, EVENTO);

        assertTrue(zonas.edificios().isEmpty());
        assertEquals(Set.of(ESPACIO), zonas.espacios());
        assertTrue(zonas.puntosEncuentro().isEmpty());
    }

    /**
     * Un brigadista sin asignaciones, o una respuesta sin la lista, recibe conjuntos vacíos
     */
    @Test
    void sinAsignacionesDevuelveConjuntosVacios() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADISTA, "BRIGADISTA,ESTUDIANTE",
                IDENTIDAD.permisosComoCabecera()))
                .thenReturn(json.readTree("{\"tareasPendientes\":[]}"));

        ZonasAsignadas zonas = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD, EVENTO);

        assertTrue(zonas.edificios().isEmpty());
        assertTrue(zonas.espacios().isEmpty());
        assertTrue(zonas.puntosEncuentro().isEmpty());
    }

    /**
     * Si MS05 no responde, el 503 sube sin cambios
     */
    @Test
    void siMs05NoRespondeResponde503() {
        when(gateway.get(ZonasBrigadistaClientImpl.ZONES_PATH, BRIGADISTA, "BRIGADISTA,ESTUDIANTE",
                IDENTIDAD.permisosComoCabecera()))
                .thenThrow(ApiException.dependenciaNoDisponible());

        ApiException error = assertThrows(ApiException.class,
                () -> new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD, EVENTO));

        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE, error.getCode());
    }

    /**
     * Un id de ubicación que no es UUID no puede coincidir con ninguna ubicación de MS04: se ignora y las demás
     * asignaciones siguen valiendo
     */
    @Test
    void ignoraLasAsignacionesConIdsQueNoSonUuid() {
        respuesta("""
                {"eventoId":"%s","ubicacionId":"ZONA-001","tipoUbicacion":"ZONA_SEGURA","estado":"VIGENTE"},"""
                .formatted(EVENTO) + asignacion("EDIFICIO", EDIFICIO, "VIGENTE"));

        ZonasAsignadas zonas = new ZonasBrigadistaClientImpl(gateway).zonasDe(IDENTIDAD, EVENTO);

        assertEquals(Set.of(EDIFICIO), zonas.edificios());
        assertTrue(zonas.puntosEncuentro().isEmpty());
    }
}
