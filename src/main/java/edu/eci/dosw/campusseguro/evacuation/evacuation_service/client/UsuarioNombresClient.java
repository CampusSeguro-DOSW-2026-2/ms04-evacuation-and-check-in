package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Nombres de las personas que entrega MS01. MS04 los pide en cada consulta y no los guarda (RN-MS04-02,
 * RN-MS04-25)
 */
public interface UsuarioNombresClient {

    /**
     * Nombre completo de cada usuario, pedido a MS01 con POST /users/batch. Si MS01 no responde, la consulta no
     * falla: devuelve un mapa vacío y el fallo queda en el log
     *
     * @param usuarioIds ids de los usuarios, hasta 100 (una página)
     * @return el nombre completo por id; los usuarios que MS01 no entregó no aparecen
     */
    Map<UUID, String> nombresDe(Collection<UUID> usuarioIds);
}
