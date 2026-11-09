package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import tools.jackson.databind.JsonNode;

/**
 * Pide las zonas del brigadista a MS05 con {@code GET /api/v1/panel/zones}, que MS04 llama en nombre del brigadista
 * propagando X-User-Id y X-User-Role (permiso ms05:panel:read_own de la Matriz).
 *
 * La ruta y la respuesta son las que MS04 espera hasta que los squads las acuerden:
 * {@code {"edificios": [uuid], "espacios": [uuid], "puntos_encuentro": [uuid]}}, con las zonas ya expandidas a ids de
 * MS02. Cuando el contrato real sea otro, solo cambia esta clase
 */
public class ZonasBrigadistaClientImpl implements ZonasBrigadistaClient {

    static final String ZONES_PATH = "/api/v1/panel/zones";

    private static final Logger log = LoggerFactory.getLogger(ZonasBrigadistaClientImpl.class);

    private final GatewayClient gateway;

    /**
     * Crea el cliente de zonas
     *
     * @param gateway cliente del API Gateway
     */
    public ZonasBrigadistaClientImpl(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public ZonasAsignadas zonasDe(RequesterIdentity brigadista) {
        JsonNode respuesta = gateway.get(ZONES_PATH, brigadista.userId(), brigadista.rolesComoCabecera());
        try {
            Set<UUID> edificios = new HashSet<>();
            for (JsonNode id : respuesta.path("edificios")) {
                edificios.add(UUID.fromString(id.asString("")));
            }
            Set<UUID> espacios = new HashSet<>();
            for (JsonNode id : respuesta.path("espacios")) {
                espacios.add(UUID.fromString(id.asString("")));
            }
            Set<UUID> puntosEncuentro = new HashSet<>();
            for (JsonNode id : respuesta.path("puntos_encuentro")) {
                puntosEncuentro.add(UUID.fromString(id.asString("")));
            }
            return new ZonasAsignadas(Set.copyOf(edificios), Set.copyOf(espacios), Set.copyOf(puntosEncuentro));
        } catch (IllegalArgumentException e) {
            log.warn("MS05 entregó zonas con ids que no son UUID");
            throw ApiException.dependenciaNoDisponible();
        }
    }
}
