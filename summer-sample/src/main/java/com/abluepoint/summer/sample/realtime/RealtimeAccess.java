package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** Checks current membership and persisted grants as well as the signed token. */
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
                || !jwt.getToken().getExpiresAt().isAfter(Instant.now())
                || !claimedMember(jwt, enterpriseId)) {
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
        if (managed) {
            authorization.requireEnterpriseMembership(enterpriseId, authentication);
            return;
        }
        if (!claimedCapability(jwt, enterpriseId, capability)
                || jwt.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + capability).equals(a.getAuthority()))
) {
            throw new AccessDeniedException("Message capability denied");
        }
        authorization.requireEnterpriseMembership(enterpriseId, authentication);
        if (!authorization.decision(authentication, enterpriseId, capability, null).allowed()) {
            throw new AccessDeniedException("Message capability revoked");
        }
    }

    private static boolean claimedMember(JwtAuthenticationToken jwt, String enterpriseId) {
        Object value = jwt.getToken().getClaims().get("enterprise_memberships");
        return value instanceof Collection<?> items && items.stream().filter(Map.class::isInstance)
                .map(valueItem -> (Map<?, ?>) valueItem).anyMatch(member ->
                        enterpriseId.equals(String.valueOf(member.containsKey("enterpriseId")
                                ? member.get("enterpriseId") : member.get("enterprise_id")))
                                && "ACTIVE".equalsIgnoreCase(String.valueOf(member.containsKey("status")
                                ? member.get("status") : member.get("membership_status"))));
    }

    private static boolean claimedCapability(JwtAuthenticationToken jwt, String enterpriseId, String capability) {
        Object value = jwt.getToken().getClaims().get("enterprise_capabilities");
        return value instanceof Map<?, ?> map && map.get(enterpriseId) instanceof Collection<?> grants
                && grants.stream().anyMatch(capability::equals);
    }
}
