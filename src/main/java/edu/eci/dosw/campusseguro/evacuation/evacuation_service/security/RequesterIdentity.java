package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Identidad de quien hace la petición, tomada solo de las cabeceras X-User-Id, X-User-Role y X-User-Permission que
 * inyecta el Gateway (RN-MS04-06): nunca del cuerpo de la petición
 *
 * @param userId id del usuario autenticado (UUID)
 * @param roles nombres de los roles del usuario (X-User-Role); informativos, nunca se usan para autorizar
 * @param permisos códigos de los permisos efectivos del usuario (X-User-Permission), ya con la unión de sus roles
 */
public record RequesterIdentity(UUID userId, Set<String> roles, Set<String> permisos) {

    /**
     * Valida los componentes y deja los roles y los permisos como conjuntos inmutables en orden alfabético
     */
    public RequesterIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(permisos, "permisos");
        roles = Collections.unmodifiableSet(new TreeSet<>(roles));
        permisos = Collections.unmodifiableSet(new TreeSet<>(permisos));
    }

    /**
     * Indica si el solicitante tiene el permiso. La autorización se decide solo con los permisos de X-User-Permission
     * y nunca con el nombre de un rol (Matriz Rol-Permiso, MR-07)
     *
     * @param permiso permiso a comprobar
     * @return true si sus permisos incluyen el código del permiso
     */
    public boolean tienePermiso(Permiso permiso) {
        return permisos.contains(permiso.getCode());
    }

    /**
     * Roles con el formato de la cabecera X-User-Role: separados por coma, sin espacios y en orden alfabético
     *
     * @return los nombres de los roles
     */
    public String rolesComoCabecera() {
        return String.join(",", roles);
    }

    /**
     * Permisos con el formato de la cabecera X-User-Permission: separados por coma, sin espacios y en orden
     * alfabético
     *
     * @return los códigos de los permisos
     */
    public String permisosComoCabecera() {
        return String.join(",", permisos);
    }

    /**
     * Nombres de los roles en orden alfabético, tal como se comparan con los roles convocados de un evento
     *
     * @return los nombres de los roles
     */
    public List<String> nombresDeRoles() {
        return List.copyOf(roles);
    }
}
