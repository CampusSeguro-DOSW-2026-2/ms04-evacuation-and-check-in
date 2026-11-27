package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * Convierte las cabeceras X-User-Id, X-User-Role y X-User-Permission en un {@link RequesterIdentity}. Si falta
 * alguna o no es válida responde 401 NO_AUTENTICADO (RNF-MS04-10, Estándares de Integración, sección 4)
 *
 * Los roles ya no son un conjunto fijo (Estándares, sección 6): de un rol solo se valida el formato. Lo mismo con
 * los permisos: un código con formato válido que MS04 no conoce se acepta y simplemente no concede nada en MS04
 */
public final class IdentityHeaderParser {

    private static final String LIST_SEPARATOR = ",";

    /** Nombre de rol: mayúsculas, dígitos y guion bajo, empieza con una letra, de 2 a 30 caracteres */
    private static final Pattern ROLE_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    /** Código de permiso: ms&lt;dos dígitos&gt;:&lt;recurso&gt;:&lt;acción&gt; */
    private static final Pattern PERMISSION_FORMAT = Pattern.compile("^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    private IdentityHeaderParser() {
    }

    /**
     * Construye la identidad del solicitante a partir de las cabeceras recibidas
     *
     * @param userIdHeader valor de X-User-Id, debe ser el UUID del usuario
     * @param roleHeader valor de X-User-Role, nombres de rol separados por coma
     * @param permissionHeader valor de X-User-Permission, códigos de permiso separados por coma
     * @return la identidad con su id, sus roles y sus permisos
     * @throws ApiException 401 NO_AUTENTICADO si falta alguna cabecera o su valor no es válido
     */
    public static RequesterIdentity parse(String userIdHeader, String roleHeader, String permissionHeader) {
        if (isBlank(userIdHeader) || isBlank(roleHeader) || isBlank(permissionHeader)) {
            throw ApiException.noAutenticado(IdentityHeaders.MISSING_MESSAGE);
        }
        UUID userId = parseUserId(userIdHeader);
        Set<String> roles = parseList(roleHeader, ROLE_FORMAT, IdentityHeaders.INVALID_ROLE_MESSAGE);
        Set<String> permisos = parseList(permissionHeader, PERMISSION_FORMAT,
                IdentityHeaders.INVALID_PERMISSION_MESSAGE);
        return new RequesterIdentity(userId, roles, permisos);
    }

    private static UUID parseUserId(String userIdHeader) {
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.noAutenticado(IdentityHeaders.INVALID_USER_ID_MESSAGE);
        }
    }

    /**
     * Lee una lista separada por coma: cada valor debe tener el formato indicado y la lista no puede quedar vacía
     */
    private static Set<String> parseList(String header, Pattern format, String invalidMessage) {
        Set<String> valores = new HashSet<>();
        for (String token : header.split(LIST_SEPARATOR)) {
            String valor = token.trim();
            if (!valor.isEmpty()) {
                if (!format.matcher(valor).matches()) {
                    throw ApiException.noAutenticado(invalidMessage);
                }
                valores.add(valor);
            }
        }
        if (valores.isEmpty()) {
            throw ApiException.noAutenticado(IdentityHeaders.MISSING_MESSAGE);
        }
        return valores;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
