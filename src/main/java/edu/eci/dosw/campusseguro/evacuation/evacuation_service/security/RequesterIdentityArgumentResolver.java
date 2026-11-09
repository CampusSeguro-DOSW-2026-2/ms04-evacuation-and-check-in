package edu.eci.dosw.campusseguro.evacuation.evacuation_service.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Permite que los controllers reciban la identidad del solicitante como un parámetro
 * {@link RequesterIdentity}, armada con las cabeceras X-User-Id y X-User-Role
 */
public class RequesterIdentityArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return RequesterIdentity.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return IdentityHeaderParser.parse(
                webRequest.getHeader(IdentityHeaders.USER_ID),
                webRequest.getHeader(IdentityHeaders.USER_ROLE));
    }
}
