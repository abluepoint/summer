package com.abluepoint.summer.sample.resource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.handler.MappedInterceptor;

import org.springframework.web.util.pattern.PathPattern;

/** Narrow anti-rebinding check; existing role, tenant, scope and protocol checks still run afterwards. */
@Component
final class InterfaceResourceBindingGuard implements HandlerInterceptor {
    private final InterfaceResourceHistory history;
    private org.springframework.beans.factory.ObjectProvider<ManagedInterfaceRequestGuard> managedGuards;
    InterfaceResourceBindingGuard(InterfaceResourceHistory history) { this.history = history; }
    @org.springframework.beans.factory.annotation.Autowired
    InterfaceResourceBindingGuard(InterfaceResourceHistory history, org.springframework.beans.factory.ObjectProvider<ManagedInterfaceRequestGuard> managedGuards) {
        this.history=history;this.managedGuards=managedGuards;
    }

    @Bean MappedInterceptor resourceBindingMappedInterceptor() {
        return new MappedInterceptor(new String[]{
                "/api/**",
                "/public/dictionaries/{code}",
                "/public/dictionaries/{code}/items/{value}",
                "/public/enterprises/{enterpriseId}/dictionaries/{code}",
                "/public/enterprises/{enterpriseId}/dictionaries/{code}/items/{value}"
        }, this);
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (managedGuards != null && managedGuards.orderedStream().anyMatch(guard -> guard.verify(request,handler))) return true;
        if (!(handler instanceof HandlerMethod method)) {
            if (handler instanceof org.springframework.web.servlet.resource.ResourceHttpRequestHandler
                    || handler instanceof org.springframework.web.socket.server.support.WebSocketHttpRequestHandler) return true;
            throw new AccessDeniedException("Non-MVC business resource requires a managed permission adapter");
        }
        ResourcePermission explicit = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ResourcePermission.class);
        if (explicit == null) explicit = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ResourcePermission.class);
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = matched instanceof PathPattern pattern ? pattern.getPatternString() : matched instanceof String value ? value : null;
        if (path == null) throw new AccessDeniedException("Matched resource mapping is unavailable");
        path = path.trim().replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        String automatic = request.getMethod().toUpperCase(java.util.Locale.ROOT) + ":" + path;
        String permission = explicit != null && !explicit.value().isBlank() ? explicit.value().trim() : automatic;
        if (history.isReplaced(permission,
                InterfaceResourceCatalogService.handlerIdentity(method)))
            throw new AccessDeniedException("SUMMER_RESOURCE_PERMISSION_REUSE: replacement requires an explicit new permission");
        return true;
    }
}
