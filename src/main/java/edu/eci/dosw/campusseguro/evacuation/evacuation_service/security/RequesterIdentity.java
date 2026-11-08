package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Identidad de quien hace la petición, tomada solo de las cabeceras X-User-Id y X-User-Role que inyecta el
 * Gateway (RN-MS04-06): nunca del cuerpo de la petición
 *
 * @param userId id del usuario autenticado (UUID)
 * @param roles roles del usuario
 */
public record RequesterIdentity(UUID userId, Set<Rol> roles) {

    /**
     * Valida los componentes y deja los roles como un conjunto inmutable
     */
    public RequesterIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roles, "roles");
        EnumSet<Rol> copia = EnumSet.noneOf(Rol.class);
        copia.addAll(roles);
        roles = Collections.unmodifiableSet(copia);
    }

    /**
     * Permisos del solicitante: la unión de los de todos sus roles
     *
     * @return los permisos de MS04 que tiene
     */
    public Set<Permiso> permisos() {
        return Rol.permisosDe(roles);
    }

    /**
     * Indica si alguno de los roles concede el permiso
     *
     * @param permiso permiso a comprobar
     * @return true si el solicitante lo tiene
     */
    public boolean tienePermiso(Permiso permiso) {
        return permisos().contains(permiso);
    }

    /**
     * Indica si el solicitante tiene el rol
     *
     * @param rol rol a comprobar
     * @return true si el rol está entre los suyos
     */
    public boolean tieneRol(Rol rol) {
        return roles.contains(rol);
    }

    /**
     * Roles con el formato de la cabecera X-User-Role: separados por coma, sin espacios y en orden alfabético
     *
     * @return los nombres de los roles
     */
    public String rolesComoCabecera() {
        return Rol.ordenados(roles).stream().map(Rol::name).collect(Collectors.joining(","));
    }

    /**
     * Nombres de los roles en orden alfabético, tal como se comparan con los roles convocados de un evento
     *
     * @return los nombres de los roles
     */
    public java.util.List<String> nombresDeRoles() {
        return Rol.ordenados(roles).stream().map(Rol::name).toList();
    }
}
