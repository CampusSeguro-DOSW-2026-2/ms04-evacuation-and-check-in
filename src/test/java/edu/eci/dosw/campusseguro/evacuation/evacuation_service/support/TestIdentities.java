package edu.eci.dosw.campusseguro.evacuation.evacuation_service.support;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Test identities with the initial permission assignment of the role-permission matrix (MS04 section). In
 * production the permissions arrive already resolved in X-User-Permission and MS04 does not know which role has each
 * one; here the headers that the Gateway would send for the eight initial roles are reproduced.
 *
 * Every role also has {@code ms01:profile:read} (matrix, MS01 section), so a role without MS04 permissions (ADMIN, or
 * one created later by the administrator) still carries a non empty X-User-Permission header, as a real token does
 */
public final class TestIdentities {

    /** Permission that every initial role has and that MS04 does not use */
    public static final String FOREIGN_PERMISSION = "ms01:profile:read";

    private static final Set<String> COMMUNITY = Set.of("ms04:checkin:write", "ms04:checkin:read_own",
            "ms04:assistance:request");

    /** MS04 permissions of each initial role when the system is deployed (role-permission matrix) */
    private static final Map<String, Set<String>> MS04_PERMISSIONS = Map.of(
            "ESTUDIANTE", COMMUNITY,
            "PROFESOR", COMMUNITY,
            "ADMINISTRATIVO", COMMUNITY,
            "BRIGADISTA", union(COMMUNITY, Set.of("ms04:dashboard:read")),
            "COORDINADOR", union(COMMUNITY, Set.of("ms04:dashboard:read", "ms04:history:read")),
            "ORGANIZADOR", union(COMMUNITY, Set.of("ms04:dashboard:read", "ms04:history:read")),
            "ADMIN", Set.of(),
            "AUDITOR", Set.of("ms04:dashboard:read", "ms04:history:read"));

    /**
     * Utility class without instances
     */
    private TestIdentities() {
    }

    /**
     * Permissions that the Gateway would send in X-User-Permission for some roles: the union of their permissions,
     * in alphabetical order and separated by commas. A role that is not initial adds no MS04 permission
     *
     * @param roles roles separated by commas, as in X-User-Role
     * @return the value of X-User-Permission
     */
    public static String permissions(String roles) {
        return String.join(",", permissionsOf(roles.split(",")));
    }

    /**
     * Identity of a user with initial roles and their permissions
     *
     * @param userId id of the user
     * @param roles names of the roles
     * @return the identity with the union of the permissions of those roles
     */
    public static RequesterIdentity identity(UUID userId, String... roles) {
        return new RequesterIdentity(userId, Set.of(roles), permissionsOf(roles));
    }

    /**
     * Union of the permissions of some roles, plus the permission that every role has
     *
     * @param roles names of the roles
     * @return the permissions in alphabetical order
     */
    private static Set<String> permissionsOf(String... roles) {
        TreeSet<String> permissions = new TreeSet<>();
        permissions.add(FOREIGN_PERMISSION);
        Arrays.stream(roles)
                .map(String::trim)
                .forEach(role -> permissions.addAll(MS04_PERMISSIONS.getOrDefault(role, Set.of())));
        return permissions;
    }

    /**
     * Union of two sets of permissions
     *
     * @param first first set
     * @param second second set
     * @return a new set with the elements of both
     */
    private static Set<String> union(Set<String> first, Set<String> second) {
        TreeSet<String> union = new TreeSet<>(first);
        union.addAll(second);
        return union;
    }
}
