package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;

/**
 * Convierte las cabeceras X-User-Id y X-User-Role en un {@link RequesterIdentity}. Si faltan o no son válidas
 * responde 401 NO_AUTENTICADO (RNF-MS04-10)
 */
public final class IdentityHeaderParser {

    private static final String ROLE_SEPARATOR = ",";

    private IdentityHeaderParser() {
    }

    /**
     * Construye la identidad del solicitante a partir de las cabeceras recibidas
     *
     * @param userIdHeader valor de X-User-Id, debe ser el UUID del usuario
     * @param roleHeader valor de X-User-Role, roles del catálogo separados por coma
     * @return la identidad con su id y su conjunto de roles
     * @throws ApiException 401 NO_AUTENTICADO si falta alguna cabecera o su valor no es válido
     */
    public static RequesterIdentity parse(String userIdHeader, String roleHeader) {
        if (isBlank(userIdHeader) || isBlank(roleHeader)) {
            throw ApiException.noAutenticado(IdentityHeaders.MISSING_MESSAGE);
        }
        return new RequesterIdentity(parseUserId(userIdHeader), parseRoles(roleHeader));
    }

    private static UUID parseUserId(String userIdHeader) {
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.noAutenticado(IdentityHeaders.INVALID_USER_ID_MESSAGE);
        }
    }

    private static Set<Rol> parseRoles(String roleHeader) {
        Set<Rol> roles = EnumSet.noneOf(Rol.class);
        for (String token : roleHeader.split(ROLE_SEPARATOR)) {
            String name = token.trim();
            if (!name.isEmpty()) {
                roles.add(parseRole(name));
            }
        }
        if (roles.isEmpty()) {
            throw ApiException.noAutenticado(IdentityHeaders.MISSING_MESSAGE);
        }
        return roles;
    }

    private static Rol parseRole(String name) {
        try {
            return Rol.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw ApiException.noAutenticado(IdentityHeaders.INVALID_ROLE_MESSAGE);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
