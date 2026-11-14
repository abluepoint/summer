package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/** Checks token lifetime, current membership and current managed resources. */
@Component
final class RealtimeAccess implements com.abluepoint.summer.sample.resource.RuntimeBusinessResources {
    private final AuthorizationService authorization;

    private final com.abluepoint.summer.sample.resource.BusinessResourceAccess business;

    RealtimeAccess(AuthorizationService authorization,
                   com.abluepoint.summer.sample.resource.BusinessResourceAccess business) {
        this.authorization = authorization;
        this.business = business;
    }

    @Override public java.util.List<Operation> operations() {
        return java.util.List.of(
            new Operation("read", "WS", "/ws/v1/messages", "ENTERPRISE", Map.of("frame", java.util.List.of("read", "ping"))),
            new Operation("send", "WS", "/ws/v1/messages", "ENTERPRISE", Map.of("frame", java.util.List.of("send", "ack"))));
    }

    void require(String enterpriseId, Authentication authentication, String capability) {
        if (!java.util.Set.of(RealtimeMessageService.READ, RealtimeMessageService.WRITE).contains(capability))
            throw new AccessDeniedException("Unregistered message operation");
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || enterpriseId == null || enterpriseId.isBlank()
                || jwt.getToken().getExpiresAt() == null
                || !jwt.getToken().getExpiresAt().isAfter(Instant.now())) {
            throw new AccessDeniedException("Message capability denied or expired");
        }
        var domain = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId);
        boolean managed;
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes context
                && context.getRequest().getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
                    instanceof org.springframework.web.method.HandlerMethod) {
            managed = business.require(domain, authentication, capability);
        } else {
            managed = business.requireRuntime(domain, authentication, this,
                    RealtimeMessageService.READ.equals(capability) ? "read" : "send", capability);
        }
        if (!managed) throw new AccessDeniedException("Current managed message permission required");
        authorization.requireEnterpriseMembership(enterpriseId, authentication);
    }
}
