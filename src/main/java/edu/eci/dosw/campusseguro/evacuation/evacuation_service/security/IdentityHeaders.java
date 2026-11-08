package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

/**
 * Cabeceras técnicas que inyecta el API Gateway (Estándares de Integración, sección 4). MS04 nunca recibe el JWT:
 * confía en estas cabeceras
 */
public final class IdentityHeaders {

    /** UUID del usuario autenticado (claim sub del JWT) */
    public static final String USER_ID = "X-User-Id";
    /** Roles del usuario separados por coma, sin espacios y en orden alfabético */
    public static final String USER_ROLE = "X-User-Role";
    /** Servicio que llama a una ruta interna: ms01 ... ms08, derivado por el Gateway de la clave de servicio */
    public static final String SERVICE_NAME = "X-Service-Name";

    public static final String MISSING_MESSAGE = "Faltan las cabeceras de identidad (X-User-Id / X-User-Role).";
    public static final String INVALID_USER_ID_MESSAGE = "La cabecera X-User-Id no es válida.";
    public static final String INVALID_ROLE_MESSAGE = "La cabecera X-User-Role no es válida.";
    public static final String MISSING_SERVICE_MESSAGE = "Falta la cabecera X-Service-Name.";

    private IdentityHeaders() {
    }
}
