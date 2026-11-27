package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;
import tools.jackson.databind.JsonNode;

/**
 * Pide las zonas del brigadista al panel de responsabilidades de MS05, {@code GET /api/v1/responsabilidades/mias}
 * (RF-BEI-06), que MS04 llama en nombre del brigadista propagando X-User-Id, X-User-Role y X-User-Permission (permiso
 * ms05:panel:read_own de la Matriz).
 *
 * MS05 responde sus asignaciones vigentes (todas, sin paginar) y, aparte, sus tareas pendientes paginadas. MS04 solo
 * necesita las asignaciones, así que pide la página más pequeña de tareas. Cada asignación trae el tipo de ubicación
 * y el id de la ubicación en MS02:
 * <ul>
 * <li>{@code EDIFICIO} son edificios</li>
 * <li>{@code ZONA_SEGURA} y {@code PUNTO_ENCUENTRO} son puntos de encuentro (en MS02 son zonas seguras)</li>
 * <li>{@code ESPACIO} son espacios; MS05 todavía no asigna ese tipo</li>
 * </ul>
 * Cada asignación trae también el evento ({@code eventoId}): solo cuentan las del evento consultado, porque un
 * brigadista puede tener asignaciones vigentes en más de un evento. Una asignación cuyo id no es un UUID de MS02 se ignora, porque no puede coincidir con ninguna
 * ubicación de MS04
 */
public class ZonasBrigadistaClientImpl implements ZonasBrigadistaClient {

    static final String ZONES_PATH = "/api/v1/responsabilidades/mias?pagina=0&tamano=1";

    private static final String ESTADO_VIGENTE = "VIGENTE";

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
    public ZonasAsignadas zonasDe(RequesterIdentity brigadista, UUID eventoId) {
        JsonNode respuesta = gateway.get(ZONES_PATH, brigadista.userId(), brigadista.rolesComoCabecera(),
                brigadista.permisosComoCabecera());
        Set<UUID> edificios = new HashSet<>();
        Set<UUID> espacios = new HashSet<>();
        Set<UUID> puntosEncuentro = new HashSet<>();
        for (JsonNode asignacion : respuesta.path("asignaciones")) {
            if (!ESTADO_VIGENTE.equals(asignacion.path("estado").asString(""))
                    || !eventoId.toString().equalsIgnoreCase(asignacion.path("eventoId").asString("").strip())) {
                continue;
            }
            Set<UUID> destino = switch (asignacion.path("tipoUbicacion").asString("")) {
                case "EDIFICIO" -> edificios;
                case "ESPACIO" -> espacios;
                case "ZONA_SEGURA", "PUNTO_ENCUENTRO" -> puntosEncuentro;
                default -> null;
            };
            if (destino == null) {
                continue;
            }
            try {
                destino.add(UUID.fromString(asignacion.path("ubicacionId").asString("")));
            } catch (IllegalArgumentException e) {
                log.warn("MS05 entregó una asignación con un id de ubicación que no es UUID: se ignora");
            }
        }
        return new ZonasAsignadas(Set.copyOf(edificios), Set.copyOf(espacios), Set.copyOf(puntosEncuentro));
    }
}
