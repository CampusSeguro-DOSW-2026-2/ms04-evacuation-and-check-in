package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.util.Json;
import tools.jackson.databind.JsonNode;

/**
 * Pide los nombres a MS01 con {@code POST /api/v1/users/batch} (RF-MS01-09) como ruta interna con la clave de
 * servicio de MS04. MS01 debe autorizar a ms04 en INTERNAL_ALLOWED_SERVICES (decisión abierta n.º 2): mientras no
 * lo haga responde 403 y las listas salen con el nombre nulo (RN-MS04-25)
 */
public class UsuarioNombresClientImpl implements UsuarioNombresClient {

    static final String BATCH_PATH = "/api/v1/users/batch";

    private static final Logger log = LoggerFactory.getLogger(UsuarioNombresClientImpl.class);
    private static final List<String> CAMPOS_NOMBRE = List.of("primer_nombre", "segundo_nombre", "primer_apellido",
            "segundo_apellido");

    private final GatewayClient gateway;

    /**
     * Crea el cliente de nombres
     *
     * @param gateway cliente del API Gateway
     */
    public UsuarioNombresClientImpl(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @Override
    public Map<UUID, String> nombresDe(Collection<UUID> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = usuarioIds.stream().map(UUID::toString).toList();
        try {
            JsonNode respuesta = gateway.post(BATCH_PATH, Json.write(Map.of("ids", ids)));
            Map<UUID, String> nombres = new HashMap<>();
            for (JsonNode usuario : respuesta.path("usuarios")) {
                String nombre = CAMPOS_NOMBRE.stream()
                        .map(campo -> usuario.path(campo).asString("").strip())
                        .filter(parte -> !parte.isEmpty())
                        .collect(Collectors.joining(" "));
                nombres.put(UUID.fromString(usuario.path("id").asString("")), nombre);
            }
            return nombres;
        } catch (ApiException | IllegalArgumentException e) {
            log.warn("MS01 no entregó los nombres de {} usuarios: la lista se entrega con el nombre nulo",
                    ids.size());
            return Map.of();
        }
    }
}
