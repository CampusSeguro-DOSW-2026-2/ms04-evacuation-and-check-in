package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * Comprueba que el solicitante tenga, en X-User-Permission, el permiso que exige una operación (su x-permiso). La
 * autorización la decide siempre el permiso y nunca el nombre de un rol (MR-07)
 */
public final class PermissionChecker {

    private PermissionChecker() {
    }

    /**
     * Exige que el solicitante tenga el permiso; si no, la operación se rechaza con 403 PROHIBIDO
     *
     * @param requester identidad de quien solicita
     * @param permiso permiso que exige la operación
     * @throws ApiException 403 PROHIBIDO si el permiso no está en X-User-Permission
     */
    public static void require(RequesterIdentity requester, Permiso permiso) {
        if (!requester.tienePermiso(permiso)) {
            throw ApiException.prohibido();
        }
    }
}
