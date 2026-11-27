package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Authorization with flexible roles and permissions (integration standards, section 6; role-permission matrix,
 * MR-07): the permission comes only from X-User-Permission, which already brings the union of the permissions of
 * every role, and the name of the role decides nothing
 */
class PermissionsTest {

    private static final UUID USER = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");

    /**
     * The permission codes are the ones of the OpenAPI contract and of the matrix
     */
    @Test
    void thePermissionCodesAreTheOnesOfTheContractAndTheMatrix() {
        assertEquals("ms04:checkin:write", Permiso.CHECKIN_WRITE.getCode());
        assertEquals("ms04:checkin:read_own", Permiso.CHECKIN_READ_OWN.getCode());
        assertEquals("ms04:assistance:request", Permiso.ASSISTANCE_REQUEST.getCode());
        assertEquals("ms04:dashboard:read", Permiso.DASHBOARD_READ.getCode());
        assertEquals("ms04:history:read", Permiso.HISTORY_READ.getCode());
    }

    /**
     * The permission comes from X-User-Permission and not from the name of the role
     */
    @Test
    void thePermissionComesFromTheHeaderAndNotFromTheRoleName() {
        // A BRIGADISTA whose ms04:dashboard:read was removed by the administrator no longer has it
        RequesterIdentity withoutPermission = new RequesterIdentity(USER, Set.of("BRIGADISTA"),
                Set.of("ms04:checkin:write"));
        // A role created by the administrator with ms04:dashboard:read has it
        RequesterIdentity newRole = new RequesterIdentity(USER, Set.of("JEFE_DE_PISO"),
                Set.of("ms04:dashboard:read"));

        assertFalse(withoutPermission.tienePermiso(Permiso.DASHBOARD_READ));
        assertTrue(newRole.tienePermiso(Permiso.DASHBOARD_READ));
        assertFalse(newRole.tienePermiso(Permiso.CHECKIN_WRITE));
    }

    /**
     * A code of another service grants nothing in MS04
     */
    @Test
    void aCodeOfAnotherServiceGrantsNothingInMs04() {
        RequesterIdentity admin = new RequesterIdentity(USER, Set.of("ADMIN"),
                Set.of("ms01:role:manage", "ms01:user:read"));

        for (Permiso permission : Permiso.values()) {
            assertFalse(admin.tienePermiso(permission), permission.getCode());
        }
    }

    /**
     * Roles and permissions are delivered in alphabetical order, like the headers
     */
    @Test
    void rolesAndPermissionsAreInAlphabeticalOrderLikeTheHeaders() {
        RequesterIdentity identity = new RequesterIdentity(USER, Set.of("ESTUDIANTE", "BRIGADISTA"),
                Set.of("ms04:dashboard:read", "ms04:checkin:write", "ms01:profile:read"));

        assertEquals("BRIGADISTA,ESTUDIANTE", identity.rolesComoCabecera());
        assertEquals(List.of("BRIGADISTA", "ESTUDIANTE"), identity.nombresDeRoles());
        assertEquals("ms01:profile:read,ms04:checkin:write,ms04:dashboard:read", identity.permisosComoCabecera());
    }
}
