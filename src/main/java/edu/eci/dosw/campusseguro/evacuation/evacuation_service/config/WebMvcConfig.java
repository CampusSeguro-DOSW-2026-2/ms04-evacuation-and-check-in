package edu.eci.dosw.campusseguro.evacuation.evacuation_service.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import edu.eci.dosw.campusseguro.evacuation.evacuation_service.security.RequesterIdentityArgumentResolver;

/**
 * Configuración de Spring MVC de MS04. No configura CORS: lo gestiona únicamente el API Gateway (RNF-MS04-10)
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequesterIdentityArgumentResolver());
    }
}
