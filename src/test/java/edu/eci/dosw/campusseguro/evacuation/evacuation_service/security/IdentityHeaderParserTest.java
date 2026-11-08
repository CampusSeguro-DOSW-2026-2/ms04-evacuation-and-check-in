package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;

class IdentityHeaderParserTest {

    private static final String USUARIO = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";

    @Test
    void construyeLaIdentidadConVariosRoles() {
        RequesterIdentity identidad = IdentityHeaderParser.parse(USUARIO, "BRIGADISTA,ESTUDIANTE");

        assertEquals(UUID.fromString(USUARIO), identidad.userId());
        assertEquals(Set.of(Rol.BRIGADISTA, Rol.ESTUDIANTE), identidad.roles());
    }

    @Test
    void ignoraEspaciosRepetidosYElementosVacios() {
        RequesterIdentity identidad = IdentityHeaderParser.parse("  " + USUARIO + " ", " ESTUDIANTE , ,ESTUDIANTE,");

        assertEquals(Set.of(Rol.ESTUDIANTE), identidad.roles());
    }

    @Test
    void sinCabecerasEsNoAutenticado() {
        for (String[] par : new String[][] { { null, "ESTUDIANTE" }, { USUARIO, null }, { "", "ESTUDIANTE" },
                { USUARIO, "  " }, { USUARIO, " , " } }) {
            ApiException ex = assertThrows(ApiException.class, () -> IdentityHeaderParser.parse(par[0], par[1]));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
            assertEquals(IdentityHeaders.MISSING_MESSAGE, ex.getMessage());
        }
    }

    @Test
    void unIdQueNoEsUuidEsNoAutenticado() {
        ApiException ex = assertThrows(ApiException.class, () -> IdentityHeaderParser.parse("u-55", "ESTUDIANTE"));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
        assertEquals(IdentityHeaders.INVALID_USER_ID_MESSAGE, ex.getMessage());
    }

    @Test
    void unRolFueraDelCatalogoEsNoAutenticado() {
        for (String rol : new String[] { "estudiante", "SUPERUSUARIO", "ESTUDIANTE,OTRO" }) {
            ApiException ex = assertThrows(ApiException.class, () -> IdentityHeaderParser.parse(USUARIO, rol));
            assertEquals(ErrorCode.UNAUTHENTICATED, ex.getCode());
            assertEquals(IdentityHeaders.INVALID_ROLE_MESSAGE, ex.getMessage());
        }
    }

    @Test
    void permissionCheckerRechazaConProhibidoSiNoTieneElPermiso() {
        RequesterIdentity admin = new RequesterIdentity(UUID.fromString(USUARIO), Set.of(Rol.ADMIN));

        ApiException ex = assertThrows(ApiException.class,
                () -> PermissionChecker.require(admin, Permiso.CHECKIN_WRITE));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());

        PermissionChecker.require(new RequesterIdentity(UUID.fromString(USUARIO), Set.of(Rol.ESTUDIANTE)),
                Permiso.CHECKIN_WRITE);
    }
}
