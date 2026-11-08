package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica las rutas internas (RN-MS04-23) con la cabecera X-Service-Name que el Gateway inyecta a partir de la
 * clave de servicio; no usa X-User-Id ni X-User-Role. Sin la cabecera la petición queda sin autenticar y responde
 * 401; un servicio que no está en la lista de autorizados queda autenticado sin autoridades y responde 403
 */
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    /** Autoridad que reciben los servicios autorizados */
    public static final String AUTHORITY = "ROLE_INTERNAL_SERVICE";

    private final Set<String> allowedServices;

    /**
     * @param allowedServices nombres de los servicios autorizados separados por coma (p. ej. {@code ms06}); se
     *        ignoran los espacios, las mayúsculas y los valores vacíos
     */
    public InternalServiceAuthenticationFilter(String allowedServices) {
        this.allowedServices = Arrays.stream(allowedServices == null ? new String[0] : allowedServices.split(","))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(IdentityHeaders.SERVICE_NAME);
        if (header == null || header.isBlank()) {
            request.setAttribute(IdentityHeadersAuthenticationFilter.ERROR_ATTRIBUTE,
                    IdentityHeaders.MISSING_SERVICE_MESSAGE);
        } else {
            String service = header.trim().toLowerCase(Locale.ROOT);
            List<GrantedAuthority> authorities = allowedServices.contains(service)
                    ? List.of(new SimpleGrantedAuthority(AUTHORITY))
                    : List.of();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(service, null, authorities));
            SecurityContextHolder.setContext(context);
        }
        filterChain.doFilter(request, response);
    }
}
