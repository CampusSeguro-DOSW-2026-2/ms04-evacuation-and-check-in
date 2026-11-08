package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Los permisos de MS04 por rol deben ser exactamente los de la Matriz Rol-Permiso (wiki de documentation, sección
 * MS04). La tabla de abajo es la matriz transcrita: si la matriz cambia, cambia aquí y en {@link Rol}
 */
class MatrizRolPermisoTest {

    private static final Set<Permiso> COMUNIDAD = EnumSet.of(Permiso.CHECKIN_WRITE, Permiso.CHECKIN_READ_OWN,
            Permiso.ASSISTANCE_REQUEST);

    private static final Map<Rol, Set<Permiso>> MATRIZ = Map.of(
            Rol.ESTUDIANTE, COMUNIDAD,
            Rol.PROFESOR, COMUNIDAD,
            Rol.ADMINISTRATIVO, COMUNIDAD,
            Rol.BRIGADISTA, union(COMUNIDAD, Permiso.DASHBOARD_READ),
            Rol.COORDINADOR, union(COMUNIDAD, Permiso.DASHBOARD_READ, Permiso.HISTORY_READ),
            Rol.ORGANIZADOR, union(COMUNIDAD, Permiso.DASHBOARD_READ, Permiso.HISTORY_READ),
            Rol.ADMIN, EnumSet.noneOf(Permiso.class),
            Rol.AUDITOR, EnumSet.of(Permiso.DASHBOARD_READ, Permiso.HISTORY_READ));

    private static Set<Permiso> union(Set<Permiso> base, Permiso... extra) {
        EnumSet<Permiso> resultado = EnumSet.copyOf(base);
        resultado.addAll(List.of(extra));
        return resultado;
    }

    @Test
    void cadaRolTieneExactamenteLosPermisosDeLaMatriz() {
        for (Rol rol : Rol.values()) {
            assertEquals(MATRIZ.get(rol), rol.getPermisos(), "Permisos de " + rol);
        }
    }

    @Test
    void elCatalogoTieneLosOchoRolesEnMayusculas() {
        assertEquals(8, Rol.values().length);
        assertEquals(MATRIZ.keySet(), EnumSet.allOf(Rol.class));
    }

    @Test
    void elAdminNoTienePermisosEnMs04() {
        assertTrue(Rol.ADMIN.getPermisos().isEmpty());
    }

    @Test
    void elAuditorEsSoloLecturaSinCheckin() {
        assertFalse(Rol.AUDITOR.getPermisos().contains(Permiso.CHECKIN_WRITE));
        assertFalse(Rol.AUDITOR.getPermisos().contains(Permiso.ASSISTANCE_REQUEST));
    }

    @Test
    void unUsuarioConVariosRolesTieneLaUnionDeSusPermisos() {
        RequesterIdentity identidad = new RequesterIdentity(UUID.randomUUID(), Set.of(Rol.ADMIN, Rol.BRIGADISTA));

        assertTrue(identidad.tienePermiso(Permiso.DASHBOARD_READ));
        assertTrue(identidad.tienePermiso(Permiso.CHECKIN_WRITE));
        assertFalse(identidad.tienePermiso(Permiso.HISTORY_READ));
    }

    @Test
    void losRolesSeEntreganEnOrdenAlfabeticoComoLaCabecera() {
        RequesterIdentity identidad = new RequesterIdentity(UUID.randomUUID(),
                Set.of(Rol.ESTUDIANTE, Rol.BRIGADISTA));

        assertEquals("BRIGADISTA,ESTUDIANTE", identidad.rolesComoCabecera());
        assertEquals(List.of("BRIGADISTA", "ESTUDIANTE"), identidad.nombresDeRoles());
        assertTrue(identidad.tieneRol(Rol.BRIGADISTA));
        assertFalse(identidad.tieneRol(Rol.ADMIN));
    }

    @Test
    void losCodigosDePermisoSonLosDelOpenApi() {
        assertEquals("ms04:checkin:write", Permiso.CHECKIN_WRITE.getCode());
        assertEquals("ms04:checkin:read_own", Permiso.CHECKIN_READ_OWN.getCode());
        assertEquals("ms04:assistance:request", Permiso.ASSISTANCE_REQUEST.getCode());
        assertEquals("ms04:dashboard:read", Permiso.DASHBOARD_READ.getCode());
        assertEquals("ms04:history:read", Permiso.HISTORY_READ.getCode());
    }
}
