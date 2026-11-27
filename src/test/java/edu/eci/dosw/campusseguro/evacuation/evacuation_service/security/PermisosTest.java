package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Autorización con roles y permisos flexibles (Estándares de Integración, sección 6; Matriz Rol-Permiso, MR-07): el
 * permiso sale solo de X-User-Permission, que ya trae la unión de los permisos de todos los roles, y el nombre del
 * rol no decide nada
 */
class PermisosTest {

    private static final UUID USUARIO = UUID.fromString("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42");

    @Test
    void losCodigosDePermisoSonLosDelOpenApiYDeLaMatriz() {
        assertEquals("ms04:checkin:write", Permiso.CHECKIN_WRITE.getCode());
        assertEquals("ms04:checkin:read_own", Permiso.CHECKIN_READ_OWN.getCode());
        assertEquals("ms04:assistance:request", Permiso.ASSISTANCE_REQUEST.getCode());
        assertEquals("ms04:dashboard:read", Permiso.DASHBOARD_READ.getCode());
        assertEquals("ms04:history:read", Permiso.HISTORY_READ.getCode());
    }

    @Test
    void elPermisoSaleDeXUserPermissionYNoDelNombreDelRol() {
        // Un BRIGADISTA al que el administrador le quitó ms04:dashboard:read ya no lo tiene
        RequesterIdentity sinPermiso = new RequesterIdentity(USUARIO, Set.of("BRIGADISTA"),
                Set.of("ms04:checkin:write"));
        // Un rol creado por el administrador con ms04:dashboard:read sí lo tiene
        RequesterIdentity rolNuevo = new RequesterIdentity(USUARIO, Set.of("JEFE_DE_PISO"),
                Set.of("ms04:dashboard:read"));

        assertFalse(sinPermiso.tienePermiso(Permiso.DASHBOARD_READ));
        assertTrue(rolNuevo.tienePermiso(Permiso.DASHBOARD_READ));
        assertFalse(rolNuevo.tienePermiso(Permiso.CHECKIN_WRITE));
    }

    @Test
    void unCodigoDeOtroServicioNoConcedeNadaEnMs04() {
        RequesterIdentity admin = new RequesterIdentity(USUARIO, Set.of("ADMIN"),
                Set.of("ms01:role:manage", "ms01:user:read"));

        for (Permiso permiso : Permiso.values()) {
            assertFalse(admin.tienePermiso(permiso), permiso.getCode());
        }
    }

    @Test
    void rolesYPermisosSeEntreganEnOrdenAlfabeticoComoLasCabeceras() {
        RequesterIdentity identidad = new RequesterIdentity(USUARIO, Set.of("ESTUDIANTE", "BRIGADISTA"),
                Set.of("ms04:dashboard:read", "ms04:checkin:write", "ms01:profile:read"));

        assertEquals("BRIGADISTA,ESTUDIANTE", identidad.rolesComoCabecera());
        assertEquals(List.of("BRIGADISTA", "ESTUDIANTE"), identidad.nombresDeRoles());
        assertEquals("ms01:profile:read,ms04:checkin:write,ms04:dashboard:read", identidad.permisosComoCabecera());
    }
}
