package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.resource.ResourcePermission;
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

/** Applies managed role/deny decisions at the HTTP resource seam. */
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

    AuthorizationRequestInterceptor(AuthorizationService authorizationService, RequestIdAccessor requestId) {
        this.authorizationService = authorizationService;
        this.requestId = requestId;
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
        String permissionCode = permissionCode(request, method);
        if (method.getBeanType().getName().equals("com.abluepoint.summer.sample.identity.SessionController")
                && ("GET:/api/v1/session".equals(permissionCode) || "DELETE:/api/v1/session".equals(permissionCode))) {
            // Reading or destroying one's own session does not require a tenant role.
            // The controller retains authentication, revocation and current-session checks.
            return true;
        }
        if (isApplicantEndpoint(method, permissionCode, jwt)) return true;
        String requestedEnterprise = enterpriseFromPath(request.getRequestURI());
        if (AuthorizationChangeEventController.class.isAssignableFrom(method.getBeanType())) {
            // This global-looking route explicitly addresses one enterprise's outbox.
            // Other Sample/platform routes retain their existing fixed boundary.
            requestedEnterprise = AuthorizationChangeEventController.requestedEnterpriseId(request.getParameterValues("enterpriseId"));
        }
        if (requestedEnterprise != null && !hasActiveMembership(jwt, requestedEnterprise)) {
            throw new AccessDeniedException("Inactive enterprise membership");
        }
        if ("GET:/api/v1/enterprises/{enterpriseId}/menu-manifest".equals(permissionCode)
                && method.getBeanType().getName().equals("com.abluepoint.summer.sample.menu.MenuManifestController")) {
            return true;
        }
        // Global Sample business and platform resources share the Sample's fixed tenant boundary.
        String enterpriseId = requestedEnterprise == null ? SAMPLE_ENTERPRISE : requestedEnterprise;
        String controllerClass = method.getBeanType().getName();
        if (requestedEnterprise == null && (controllerClass.startsWith("com.abluepoint.summer.sample.systemparameter.")
                || controllerClass.startsWith("com.abluepoint.summer.sample.dictionary."))) {
            enterpriseId = EnterpriseRequestContext.resolve(authentication);
        }
        AuthorizationService.Decision decision = authorizationService.decision(authentication, enterpriseId, permissionCode,
                requestId.currentRequestId());
        if (!decision.allowed()) throw new AccessDeniedException("Authorization denied: " + decision.reasonCode());
        return true;
    }

    private static String permissionCode(HttpServletRequest request, HandlerMethod method) {
        ResourcePermission explicit = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), ResourcePermission.class);
        if (explicit == null) explicit = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), ResourcePermission.class);
        if (explicit != null && !explicit.value().isBlank()) return explicit.value().trim();
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path;
        if (matched instanceof PathPattern pattern) path = pattern.getPatternString();
        else if (matched instanceof String pattern && !pattern.isBlank()) path = pattern;
        else throw new AccessDeniedException("Matched resource mapping is unavailable");
        path = path.trim().replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return request.getMethod().toUpperCase(Locale.ROOT) + ":" + path;
    }

    private static boolean isApplicantEndpoint(HandlerMethod method, String permissionCode, JwtAuthenticationToken jwt) {
        if (!method.getBeanType().getName().equals("com.abluepoint.summer.sample.invitation.InvitationController")) return false;
        String scope = APPLICANT_ENDPOINT_SCOPES.get(permissionCode);
        if (scope == null) return false;
        if (jwt.getAuthorities().stream().noneMatch(authority -> ("SCOPE_" + scope).equals(authority.getAuthority()))) {
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
