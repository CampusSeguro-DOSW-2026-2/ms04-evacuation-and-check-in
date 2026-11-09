package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Población objetivo de un evento, que MS04 obtiene de MS01 por su API a través del Gateway (RN-MS04-02,
 * RN-MS04-15). La usan los resultados del evento (RF-MS04-10) y los pendientes (RF-MS04-07)
 */
public interface PoblacionObjetivoClient {

    /**
     * Usuarios ACTIVOS de MS01 con al menos uno de los roles, creados antes del corte. Una persona con varios
     * roles aparece una sola vez
     *
     * @param roles roles convocados del evento
     * @param creadosAntesDe solo cuentan los usuarios creados antes de este instante (RN-MS04-15)
     * @param requester identidad del usuario que hace la consulta, que se propaga a MS01; puede ser null
     * @return los ids de la población objetivo, o vacío si MS01 no la entregó (no responde, timeout o error)
     */
    Optional<Set<UUID>> usuariosActivosPorRoles(Collection<String> roles, Instant creadosAntesDe,
            RequesterIdentity requester);
}
