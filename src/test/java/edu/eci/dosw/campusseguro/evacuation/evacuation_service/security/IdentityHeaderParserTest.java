package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.support.TestIdentities;

/**
 * Reading of X-User-Id, X-User-Role and X-User-Permission (integration standards, sections 3 and 4)
 */
class IdentityHeaderParserTest {

    private static final String USER = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final String PERMISSIONS = "ms04:checkin:write,ms04:dashboard:read";

    /**
     * Builds the identity with several roles and permissions
     */
    @Test
    void buildsTheIdentityWithSeveralRolesAndPermissions() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER, "BRIGADISTA,ESTUDIANTE", PERMISSIONS);

        assertEquals(UUID.fromString(USER), identity.userId());
        assertEquals(Set.of("BRIGADISTA", "ESTUDIANTE"), identity.roles());
        assertEquals(Set.of("ms04:checkin:write", "ms04:dashboard:read"), identity.permisos());
    }

    /**
     * Ignores spaces, repeated values and empty elements
     */
    @Test
    void ignoresSpacesRepeatedValuesAndEmptyElements() {
        RequesterIdentity identity = IdentityHeaderParser.parse("  " + USER + " ", " ESTUDIANTE , ,ESTUDIANTE,",
                " ms04:checkin:write , ,ms04:checkin:write,");

        assertEquals(Set.of("ESTUDIANTE"), identity.roles());
        assertEquals(Set.of("ms04:checkin:write"), identity.permisos());
    }

    /**
     * Without any of the three headers the request is unauthenticated
     */
    @Test
    void withoutAnyOfTheThreeHeadersIsUnauthenticated() {
        String[][] cases = {
            { null, "ESTUDIANTE", PERMISSIONS }, { USER, null, PERMISSIONS }, { USER, "ESTUDIANTE", null },
            { "", "ESTUDIANTE", PERMISSIONS }, { USER, "  ", PERMISSIONS }, { USER, "ESTUDIANTE", " " },
            { USER, " , ", PERMISSIONS }, { USER, "ESTUDIANTE", " , " } };
        for (String[] headers : cases) {
            ApiException error = assertThrows(ApiException.class,
                    () -> IdentityHeaderParser.parse(headers[0], headers[1], headers[2]));
            assertEquals(ErrorCode.UNAUTHENTICATED, error.getCode());
            assertEquals(IdentityHeaders.MISSING_MESSAGE, error.getMessage());
        }
    }

    /**
     * An id that is not a UUID is unauthenticated
     */
    @Test
    void anIdThatIsNotAUuidIsUnauthenticated() {
        ApiException error = assertThrows(ApiException.class,
                () -> IdentityHeaderParser.parse("u-55", "ESTUDIANTE", PERMISSIONS));

        assertEquals(ErrorCode.UNAUTHENTICATED, error.getCode());
        assertEquals(IdentityHeaders.INVALID_USER_ID_MESSAGE, error.getMessage());
    }

    /**
     * The roles are not a fixed set: a role created by the administrator is accepted when its format is valid
     */
    @Test
    void aNewRoleWithValidFormatIsAccepted() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER, "JEFE_DE_PISO,SUPERUSUARIO", PERMISSIONS);

        assertEquals(Set.of("JEFE_DE_PISO", "SUPERUSUARIO"), identity.roles());
    }

    /**
     * A role with invalid format is unauthenticated
     */
    @Test
    void aRoleWithInvalidFormatIsUnauthenticated() {
        for (String role : new String[] { "estudiante", "E", "1ROL", "ROL-CON-GUION", "ESTUDIANTE,otro",
                "A234567890123456789012345678901" }) {
            ApiException error = assertThrows(ApiException.class,
                    () -> IdentityHeaderParser.parse(USER, role, PERMISSIONS));
            assertEquals(ErrorCode.UNAUTHENTICATED, error.getCode(), role);
            assertEquals(IdentityHeaders.INVALID_ROLE_MESSAGE, error.getMessage(), role);
        }
    }

    /**
     * A permission with invalid format is unauthenticated
     */
    @Test
    void aPermissionWithInvalidFormatIsUnauthenticated() {
        for (String permission : new String[] { "checkin:write", "MS04:checkin:write", "ms4:checkin:write",
                "ms04:checkin", "ms04:Checkin:write", "ms04:checkin:write,dashboard" }) {
            ApiException error = assertThrows(ApiException.class,
                    () -> IdentityHeaderParser.parse(USER, "ESTUDIANTE", permission));
            assertEquals(ErrorCode.UNAUTHENTICATED, error.getCode(), permission);
            assertEquals(IdentityHeaders.INVALID_PERMISSION_MESSAGE, error.getMessage(), permission);
        }
    }

    /**
     * A code with valid format that MS04 does not know is accepted and grants nothing in MS04
     */
    @Test
    void aPermissionOfAnotherServiceIsAcceptedButGrantsNothing() {
        RequesterIdentity identity = IdentityHeaderParser.parse(USER, "ADMIN", "ms01:role:manage");

        assertTrue(identity.permisos().contains("ms01:role:manage"));
        assertFalse(identity.tienePermiso(Permiso.CHECKIN_WRITE));
    }

    /**
     * The permission checker forbids the operation without the permission and lets it pass with it
     */
    @Test
    void thePermissionCheckerForbidsWithoutThePermission() {
        RequesterIdentity admin = TestIdentities.identity(UUID.fromString(USER), "ADMIN");
        RequesterIdentity student = TestIdentities.identity(UUID.fromString(USER), "ESTUDIANTE");

        ApiException error = assertThrows(ApiException.class,
                () -> PermissionChecker.require(admin, Permiso.CHECKIN_WRITE));
        assertEquals(ErrorCode.FORBIDDEN, error.getCode());

        assertDoesNotThrow(() -> PermissionChecker.require(student, Permiso.CHECKIN_WRITE));
    }
}
