package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Catálogo de roles de CampusSeguro (Estándares de Integración, sección 6) con los permisos que MS04 les concede
 * según la Matriz Rol-Permiso. Un usuario con varios roles tiene la unión de sus permisos. ADMIN no tiene permisos
 * en MS04
 */
public enum Rol {

    ESTUDIANTE(comunidad()),
    PROFESOR(comunidad()),
    ADMINISTRATIVO(comunidad()),
    BRIGADISTA(comunidad(), Permiso.DASHBOARD_READ),
    COORDINADOR(comunidad(), Permiso.DASHBOARD_READ, Permiso.HISTORY_READ),
    ORGANIZADOR(comunidad(), Permiso.DASHBOARD_READ, Permiso.HISTORY_READ),
    ADMIN(EnumSet.noneOf(Permiso.class)),
    AUDITOR(EnumSet.of(Permiso.DASHBOARD_READ, Permiso.HISTORY_READ));

    private final Set<Permiso> permisos;

    Rol(Set<Permiso> base, Permiso... extra) {
        EnumSet<Permiso> concedidos = EnumSet.noneOf(Permiso.class);
        concedidos.addAll(base);
        concedidos.addAll(List.of(extra));
        this.permisos = Collections.unmodifiableSet(concedidos);
    }

    /** Permisos de la comunidad en el campus: reportar y consultar el propio estado y pedir asistencia */
    private static Set<Permiso> comunidad() {
        return EnumSet.of(Permiso.CHECKIN_WRITE, Permiso.CHECKIN_READ_OWN, Permiso.ASSISTANCE_REQUEST);
    }

    public Set<Permiso> getPermisos() {
        return permisos;
    }

    /**
     * Unión de los permisos de varios roles
     *
     * @param roles roles del usuario
     * @return los permisos de MS04 que concede cualquiera de ellos
     */
    public static Set<Permiso> permisosDe(Collection<Rol> roles) {
        EnumSet<Permiso> union = EnumSet.noneOf(Permiso.class);
        for (Rol rol : roles) {
            union.addAll(rol.permisos);
        }
        return union;
    }

    /**
     * Ordena los roles por nombre, que es el orden de la cabecera X-User-Role
     *
     * @param roles roles a ordenar
     * @return los roles en orden alfabético
     */
    public static List<Rol> ordenados(Collection<Rol> roles) {
        return roles.stream().sorted(Comparator.comparing(Rol::name)).toList();
    }
}
