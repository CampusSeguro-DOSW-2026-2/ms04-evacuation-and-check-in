package edu.eci.dosw.campusseguro.evacuation.evacuation_service.support;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentity;

/**
 * Identidades de prueba con la asignación inicial de permisos de la Matriz Rol-Permiso (wiki, sección MS04). En
 * producción los permisos llegan ya calculados en X-User-Permission y MS04 no conoce qué rol tiene cada uno; aquí
 * se reproduce lo que entregaría el Gateway para los 8 roles iniciales.
 *
 * Todo rol tiene además {@code ms01:profile:read} (Matriz, MS01), así un rol sin permisos en MS04 (ADMIN, o uno
 * creado después por el administrador) igual trae una cabecera X-User-Permission no vacía, como un token real
 */
public final class IdentidadesDePrueba {

    /** Permiso que tienen todos los roles iniciales y que MS04 no usa */
    public static final String PERMISO_AJENO = "ms01:profile:read";

    private static final Set<String> COMUNIDAD = Set.of("ms04:checkin:write", "ms04:checkin:read_own",
            "ms04:assistance:request");

    /** Permisos de MS04 de cada rol inicial al desplegar (Matriz Rol-Permiso) */
    private static final Map<String, Set<String>> PERMISOS_MS04 = Map.of(
            "ESTUDIANTE", COMUNIDAD,
            "PROFESOR", COMUNIDAD,
            "ADMINISTRATIVO", COMUNIDAD,
            "BRIGADISTA", union(COMUNIDAD, Set.of("ms04:dashboard:read")),
            "COORDINADOR", union(COMUNIDAD, Set.of("ms04:dashboard:read", "ms04:history:read")),
            "ORGANIZADOR", union(COMUNIDAD, Set.of("ms04:dashboard:read", "ms04:history:read")),
            "ADMIN", Set.of(),
            "AUDITOR", Set.of("ms04:dashboard:read", "ms04:history:read"));

    private IdentidadesDePrueba() {
    }

    /**
     * Permisos que el Gateway entregaría en X-User-Permission para unos roles: la unión de sus permisos, en orden
     * alfabético y separados por coma. Un rol que no es inicial no aporta permisos de MS04
     *
     * @param roles roles separados por coma, como en X-User-Role
     * @return el valor de X-User-Permission
     */
    public static String permisos(String roles) {
        return String.join(",", permisosDe(roles.split(",")));
    }

    /**
     * Identidad de un usuario con roles iniciales y sus permisos
     *
     * @param userId id del usuario
     * @param roles nombres de los roles
     * @return la identidad con la unión de los permisos de esos roles
     */
    public static RequesterIdentity identidad(UUID userId, String... roles) {
        return new RequesterIdentity(userId, Set.of(roles), permisosDe(roles));
    }

    private static Set<String> permisosDe(String... roles) {
        TreeSet<String> permisos = new TreeSet<>();
        permisos.add(PERMISO_AJENO);
        Arrays.stream(roles).map(String::trim).forEach(rol -> permisos.addAll(PERMISOS_MS04.getOrDefault(rol, Set.of())));
        return permisos;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        TreeSet<String> union = new TreeSet<>(a);
        union.addAll(b);
        return union;
    }
}
