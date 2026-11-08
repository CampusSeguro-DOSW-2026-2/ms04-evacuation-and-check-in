package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

/**
 * Permisos de MS04 según la Matriz Rol-Permiso (wiki de documentation). Son los mismos códigos que lleva el
 * {@code x-permiso} del OpenAPI de MS04
 */
public enum Permiso {

    /** Reportar y actualizar el propio estado y punto de encuentro */
    CHECKIN_WRITE("ms04:checkin:write"),
    /** Consultar el propio estado vigente y los eventos activos que le aplican */
    CHECKIN_READ_OWN("ms04:checkin:read_own"),
    /** Solicitar asistencia desde el check-in */
    ASSISTANCE_REQUEST("ms04:assistance:request"),
    /** Ver la consolidación, las personas pendientes, la lista de asistencia y los eventos */
    DASHBOARD_READ("ms04:dashboard:read"),
    /** Consultar el histórico de check-ins y los resultados de un evento */
    HISTORY_READ("ms04:history:read");

    private final String code;

    Permiso(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
