package edu.eci.dosw.campusseguro.evacuation.evacuation_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.ForbiddenAccessDeniedHandler;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.IdentityHeadersAuthenticationFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.InternalServiceAuthenticationFilter;
import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.UnauthenticatedEntryPoint;

/**
 * Seguridad de MS04. No hay JWT: el Gateway ya lo validó y MS04 confía en sus cabeceras (RNF-MS04-10). Hay dos
 * cadenas:
 *
 * - Rutas internas de MS06 (RN-MS04-23): se autorizan por el servicio que llama (X-Service-Name), no por la
 *   Matriz Rol-Permiso. Se identifican por método y ruta porque {@code /{eventoId}/checkins} tiene un POST
 *   protegido (usuarios) y un GET interno (MS06)
 * - Todo lo demás: exige la identidad de las cabeceras X-User-Id y X-User-Role. La autorización por permiso
 *   (403) la decide cada controller con la Matriz
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Rutas internas que usa MS06 */
    private static RequestMatcher rutasInternas() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.PUT, "/api/v1/chi/eventos/*/estado"),
                paths.matcher(HttpMethod.GET, "/api/v1/chi/eventos/*/checkins"));
    }

    /**
     * Cadena de las rutas internas
     *
     * @param http configuración de la seguridad HTTP
     * @param allowedServices servicios autorizados separados por coma (por defecto ms06)
     * @return la cadena de filtros de las rutas internas
     * @throws Exception si falla la construcción de la cadena
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http,
            @Value("${internal.allowed-services:ms06}") String allowedServices) throws Exception {
        http
            .securityMatcher(rutasInternas())
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new InternalServiceAuthenticationFilter(allowedServices),
                    AnonymousAuthenticationFilter.class)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new UnauthenticatedEntryPoint())
                .accessDeniedHandler(new ForbiddenAccessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth.anyRequest()
                .hasAuthority(InternalServiceAuthenticationFilter.AUTHORITY));
        return http.build();
    }

    /**
     * Cadena del resto de rutas: solo el health de Actuator y el error son públicos
     *
     * @param http configuración de la seguridad HTTP
     * @param swaggerEnabled indica si la documentación OpenAPI está habilitada (solo en desarrollo local)
     * @return la cadena de filtros configurada
     * @throws Exception si la configuración no se puede construir
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerEnabled) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new IdentityHeadersAuthenticationFilter(), AnonymousAuthenticationFilter.class)
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new UnauthenticatedEntryPoint())
                .accessDeniedHandler(new ForbiddenAccessDeniedHandler()))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                auth.requestMatchers("/error").permitAll();
                if (swaggerEnabled) {
                    auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                }
                auth.anyRequest().authenticated();
            });
        return http.build();
    }
}
