package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
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
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Requires current managed authorization or an explicit protocol exception at the MVC seam. */
@Component
final class AuthorizationRequestInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private static final String SAMPLE_ENTERPRISE = "ent-acme";
    private static final Map<String, String> APPLICANT_ENDPOINT_SCOPES = Map.of(
            "POST:/api/v1/enterprises/{enterpriseId}/join-applications", "enterprise.join-application.submit",
            "PATCH:/api/v1/enterprises/{enterpriseId}/join-applications/{id}", "enterprise.join-application.submit",
            "POST:/api/v1/enterprises/{enterpriseId}/join-applications/{id}:withdraw", "enterprise.join-application.submit",
            "POST:/api/v1/invitations/{token}:accept", "enterprise.invitation.accept",
            "POST:/api/v1/invitations/{token}:reject", "enterprise.invitation.accept");
    private final AuthorizationService authorizationService;
    private final RequestIdAccessor requestId;
    private final com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard permissionManagement;
    private final UnifiedAuthorizationService unified;
    private final LegacyResourceAdapter resources;

    @org.springframework.beans.factory.annotation.Autowired
    AuthorizationRequestInterceptor(AuthorizationService authorizationService, RequestIdAccessor requestId, UnifiedAuthorizationService unified, LegacyResourceAdapter resources, com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard permissionManagement) {
        this.permissionManagement = permissionManagement;
        this.authorizationService = authorizationService;
        this.requestId = requestId;
        this.unified = unified; this.resources = resources;
    }

    AuthorizationRequestInterceptor(AuthorizationService authorizationService, RequestIdAccessor requestId, UnifiedAuthorizationService unified, LegacyResourceAdapter resources) {
        this(authorizationService, requestId, unified, resources, null);
    }

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.abluepoint.summer.sample.authorization.api.security.ManagedRequestAuthorization managedRequests;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (managedRequests != null && managedRequests.finalHandler(request,method,authentication)) return true;
        if (com.abluepoint.summer.sample.authorization.api.permission.PermissionController.class.equals(method.getBeanType())) {
            if (permissionManagement == null) throw new AccessDeniedException("Permission management adapter unavailable");
            permissionManagement.require(com.abluepoint.summer.sample.authorization.api.permission.PermissionController.domain(request), authentication,
                    !"GET".equals(request.getMethod()));
            return true;
        }
        String routeKey = routeKey(request, method);
        if (method.getBeanType().getName().equals("com.abluepoint.summer.sample.identity.SessionController")
                && ("GET:/api/v1/session".equals(routeKey) || "HEAD:/api/v1/session".equals(routeKey)
                    || "DELETE:/api/v1/session".equals(routeKey))) {
            // Reading or destroying one's own session does not require a tenant role.
            // The controller retains authentication, revocation and current-session checks.
            return true;
        }
        if (isApplicantEndpoint(method, routeKey, authentication)) return true;
        throw new AccessDeniedException("Current managed resource adapter required");
    }

    private static String routeKey(HttpServletRequest request, HandlerMethod method) {
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path;
        if (matched instanceof PathPattern pattern) path = pattern.getPatternString();
        else if (matched instanceof String pattern && !pattern.isBlank()) path = pattern;
        else throw new AccessDeniedException("Matched resource mapping is unavailable");
        path = path.trim().replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return request.getMethod().toUpperCase(Locale.ROOT) + ":" + path;
    }

    private static boolean isApplicantEndpoint(HandlerMethod method, String routeKey, Authentication authentication) {
        if (!method.getBeanType().getName().equals("com.abluepoint.summer.sample.invitation.InvitationController")) return false;
        String scope = APPLICANT_ENDPOINT_SCOPES.get(routeKey);
        if (scope == null) return false;
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken
                || authentication.getAuthorities().stream().noneMatch(authority -> ("SCOPE_" + scope).equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Applicant scope required");
        }
        // These operations precede membership. InvitationService still checks the subject,
        // resource tenant, invitation token, expected version and permitted state transition.
        return true;
    }

    private static boolean hasActiveMembership(JwtAuthenticationToken jwt, String enterpriseId) {
        Object memberships = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(memberships instanceof Collection<?> values)) return false;
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> membership)) continue;
            Object status = membership.get("status");
            if (status == null) status = membership.get("membership_status");
            Object id = membership.get("enterpriseId");
            if (id == null) id = membership.get("enterprise_id");
            if (enterpriseId.equals(String.valueOf(id)) && "ACTIVE".equalsIgnoreCase(String.valueOf(status))) return true;
        }
        return false;
    }

    private static String enterpriseFromPath(String path) {
        String marker = "/api/v1/enterprises/";
        if (!path.startsWith(marker)) return null;
        String remainder = path.substring(marker.length());
        int slash = remainder.indexOf('/');
        String enterpriseId = slash < 0 ? remainder : remainder.substring(0, slash);
        return enterpriseId.isBlank() ? null : enterpriseId;
    }
}
