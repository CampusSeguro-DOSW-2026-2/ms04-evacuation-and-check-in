package edu.eci.dosw.campusseguro.evacuation.evacuation_service.client;

import java.util.Optional;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Consulta de puntos de encuentro en MS02, a través del API Gateway (RN-MS04-02). Es una interfaz para poder
 * reemplazarla por un doble en las pruebas
 */
public interface PuntoEncuentroClient {

    /**
     * Busca un punto de encuentro en MS02
     *
     * @param puntoEncuentroId id del punto de encuentro (UUID de MS02)
     * @param requester usuario que hace el check-in; su identidad se propaga a MS02
     * @param correlationId correlation id de la petición
     * @return el punto de encuentro, o vacío si MS02 responde que no existe
     * @throws ApiException 503 DEPENDENCIA_NO_DISPONIBLE si MS02 no responde, responde un error o el Gateway no
     *         está configurado; nunca se reenvía el cuerpo de error de MS02 (RNF-MS04-11)
     */
    Optional<PuntoEncuentro> buscar(UUID puntoEncuentroId, RequesterIdentity requester, String correlationId);
}
