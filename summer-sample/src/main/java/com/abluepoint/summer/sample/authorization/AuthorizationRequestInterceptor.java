package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.resource.ResourcePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Collection;
import java.util.Map;

/** Applies managed role/deny decisions at the HTTP resource seam. */
@Component
final class AuthorizationRequestInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final AuthorizationService authorizationService;

    AuthorizationRequestInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) return true;
        String enterpriseId = activeEnterprise(jwt);
        if (enterpriseId == null) return true;
        String permissionCode = permissionCode(request, method);
        AuthorizationService.Decision decision = authorizationService.decision(authentication, enterpriseId, permissionCode,
                request.getHeader("X-Request-Id"));
        if (!decision.allowed()) throw new AccessDeniedException("Authorization denied: " + decision.reasonCode());
        return true;
    }

    private static String permissionCode(HttpServletRequest request, HandlerMethod method) {
        ResourcePermission explicit = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ResourcePermission.class);
        if (explicit == null) explicit = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ResourcePermission.class);
        if (explicit != null && !explicit.value().isBlank()) return explicit.value();
        String path = request.getRequestURI();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return request.getMethod().toUpperCase() + ":" + path;
    }

    private static String activeEnterprise(JwtAuthenticationToken jwt) {
        Object memberships = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(memberships instanceof Collection<?> values)) return null;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> membership)) continue;
            Object status = membership.get("status");
            if (status == null) status = membership.get("membership_status");
            if (!"ACTIVE".equalsIgnoreCase(String.valueOf(status))) continue;
            Object id = membership.get("enterpriseId");
            if (id == null) id = membership.get("enterprise_id");
            if (id != null && !String.valueOf(id).isBlank()) return String.valueOf(id);
        }
        return null;
    }
}
