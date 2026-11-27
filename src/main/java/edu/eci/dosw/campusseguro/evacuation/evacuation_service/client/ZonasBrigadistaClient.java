package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Zonas que MS05 le asigna a un brigadista. Con ellas MS04 limita lo que el BRIGADISTA ve del evento (RN-MS04-14)
 */
public interface ZonasBrigadistaClient {

    /**
     * Zonas asignadas al brigadista en un evento, pedidas a MS05 en su nombre. Las asignaciones de otros eventos no
     * cuentan
     *
     * @param brigadista identidad del brigadista que consulta
     * @param eventoId evento que se consulta
     * @return los edificios, espacios y puntos de encuentro (ids de MS02) que le corresponden
     * @throws ApiException 503 DEPENDENCIA_NO_DISPONIBLE si MS05 no entrega las zonas
     */
    ZonasAsignadas zonasDe(RequesterIdentity brigadista, UUID eventoId);

    /**
     * Ubicaciones de MS02 que cubren las zonas de un brigadista; MS05 ya expande cada zona en sus ubicaciones
     *
     * @param edificios edificios cubiertos
     * @param espacios espacios (salones, laboratorios, etc.) cubiertos
     * @param puntosEncuentro puntos de encuentro cubiertos
     */
    record ZonasAsignadas(Set<UUID> edificios, Set<UUID> espacios, Set<UUID> puntosEncuentro) {

        /**
         * Keeps immutable copies of the zones of the brigadier
         *
         * @param edificios assigned buildings
         * @param espacios assigned spaces
         * @param puntosEncuentro assigned meeting points
         * @throws NullPointerException if a set or one of its elements is null
         */
        public ZonasAsignadas {
            edificios = Set.copyOf(edificios);
            espacios = Set.copyOf(espacios);
            puntosEncuentro = Set.copyOf(puntosEncuentro);
        }
    }
}
