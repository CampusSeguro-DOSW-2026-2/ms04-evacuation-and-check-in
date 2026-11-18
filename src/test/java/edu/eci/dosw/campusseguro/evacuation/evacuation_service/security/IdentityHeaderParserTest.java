package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.IdentidadesDePrueba;

/**
 * Lectura de X-User-Id, X-User-Role y X-User-Permission (Estándares de Integración, secciones 3 y 4)
 */
class IdentityHeaderParserTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String PERMISOS = "ms04:checkin:write,ms04:dashboard:read";

    @Test
    void construyeLaIdentidadConVariosRolesYPermisos() {
        RequesterIdentity identidad = IdentityHeaderParser.parse(USUARIO, "BRIGADISTA,ESTUDIANTE", PERMISOS);

        assertEquals(UUID.fromString(USUARIO), identidad.userId());
        assertEquals(Set.of("BRIGADISTA", "ESTUDIANTE"), identidad.roles());
        assertEquals(Set.of("ms04:checkin:write", "ms04:dashboard:read"), identidad.permisos());
    }

    @Test
    void ignoraEspaciosRepetidosYElementosVacios() {
        RequesterIdentity identidad = IdentityHeaderParser.parse("  " + USUARIO + " ", " ESTUDIANTE , ,ESTUDIANTE,",
                " ms04:checkin:write , ,ms04:checkin:write,");

        assertEquals(Set.of("ESTUDIANTE"), identidad.roles());
        assertEquals(Set.of("ms04:checkin:write"), identidad.permisos());
    }

    @Test
    void sinAlgunaDeLasTresCabecerasEsNoAutenticado() {
        String[][] casos = {
            { null, "ESTUDIANTE", PERMISOS }, { USUARIO, null, PERMISOS }, { USUARIO, "ESTUDIANTE", null },
            { "", "ESTUDIANTE", PERMISOS }, { USUARIO, "  ", PERMISOS }, { USUARIO, "ESTUDIANTE", " " },
            { USUARIO, " , ", PERMISOS }, { USUARIO, "ESTUDIANTE", " , " } };
        for (String[] caso : casos) {
            ApiException ex = assertThrows(ApiException.class,
                    () -> IdentityHeaderParser.parse(caso[0], caso[1], caso[2]));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
            assertEquals(IdentityHeaders.MISSING_MESSAGE, ex.getMessage());
        }
    }

    @Test
    void unIdQueNoEsUuidEsNoAutenticado() {
        ApiException ex = assertThrows(ApiException.class,
                () -> IdentityHeaderParser.parse("u-55", "ESTUDIANTE", PERMISOS));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
        assertEquals(IdentityHeaders.INVALID_USER_ID_MESSAGE, ex.getMessage());
    }

    /** Los roles ya no son un conjunto fijo: un rol creado por el administrador se acepta si tiene formato válido */
    @Test
    void unRolNuevoConFormatoValidoSeAcepta() {
        RequesterIdentity identidad = IdentityHeaderParser.parse(USUARIO, "JEFE_DE_PISO,SUPERUSUARIO", PERMISOS);

        assertEquals(Set.of("JEFE_DE_PISO", "SUPERUSUARIO"), identidad.roles());
    }

    @Test
    void unRolConFormatoInvalidoEsNoAutenticado() {
        for (String rol : new String[] { "estudiante", "E", "1ROL", "ROL-CON-GUION", "ESTUDIANTE,otro",
                "A234567890123456789012345678901" }) {
            ApiException ex = assertThrows(ApiException.class,
                    () -> IdentityHeaderParser.parse(USUARIO, rol, PERMISOS));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode(), rol);
            assertEquals(IdentityHeaders.INVALID_ROLE_MESSAGE, ex.getMessage(), rol);
        }
    }

    @Test
    void unPermisoConFormatoInvalidoEsNoAutenticado() {
        for (String permiso : new String[] { "checkin:write", "MS04:checkin:write", "ms4:checkin:write",
                "ms04:checkin", "ms04:Checkin:write", "ms04:checkin:write,dashboard" }) {
            ApiException ex = assertThrows(ApiException.class,
                    () -> IdentityHeaderParser.parse(USUARIO, "ESTUDIANTE", permiso));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode(), permiso);
            assertEquals(IdentityHeaders.INVALID_PERMISSION_MESSAGE, ex.getMessage(), permiso);
        }
    }

    /** Un código con formato válido que MS04 no conoce se acepta y no concede nada en MS04 */
    @Test
    void unPermisoDeOtroServicioSeAceptaPeroNoConcedeNadaEnMs04() {
        RequesterIdentity identidad = IdentityHeaderParser.parse(USUARIO, "ADMIN", "ms01:role:manage");

        assertTrue(identidad.permisos().contains("ms01:role:manage"));
        assertFalse(identidad.tienePermiso(Permiso.CHECKIN_WRITE));
    }

    @Test
    void permissionCheckerRechazaConProhibidoSiNoTieneElPermiso() {
        RequesterIdentity admin = IdentidadesDePrueba.identidad(UUID.fromString(USUARIO), "ADMIN");

        ApiException ex = assertThrows(ApiException.class,
                () -> PermissionChecker.require(admin, Permiso.CHECKIN_WRITE));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());

        PermissionChecker.require(IdentidadesDePrueba.identidad(UUID.fromString(USUARIO), "ESTUDIANTE"),
                Permiso.CHECKIN_WRITE);
    }
}
