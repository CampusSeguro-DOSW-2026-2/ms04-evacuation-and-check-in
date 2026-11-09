package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica la petición con las cabeceras X-User-Id y X-User-Role que inyecta el API Gateway (RNF-MS04-10): MS04
 * nunca recibe ni valida el JWT. Si las cabeceras faltan o no son válidas la petición queda sin autenticar y las
 * rutas protegidas responden 401
 */
public class IdentityHeadersAuthenticationFilter extends OncePerRequestFilter {

    /** Atributo de la petición donde queda el motivo del 401, para que lo escriba el punto de entrada */
    public static final String ERROR_ATTRIBUTE = IdentityHeadersAuthenticationFilter.class.getName() + ".ERROR";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            RequesterIdentity identity = IdentityHeaderParser.parse(
                    request.getHeader(IdentityHeaders.USER_ID),
                    request.getHeader(IdentityHeaders.USER_ROLE));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(buildAuthentication(identity));
            SecurityContextHolder.setContext(context);
        } catch (ApiException e) {
            request.setAttribute(ERROR_ATTRIBUTE, e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    /** Los permisos del solicitante quedan como autoridades de la autenticación */
    private Authentication buildAuthentication(RequesterIdentity identity) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Permiso permiso : identity.permisos()) {
            authorities.add(new SimpleGrantedAuthority(permiso.getCode()));
        }
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, authorities);
    }
}
