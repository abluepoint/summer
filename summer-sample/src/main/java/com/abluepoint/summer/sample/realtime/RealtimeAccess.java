package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/** Checks current membership and persisted grants as well as the signed token. */
@Component
final class RealtimeAccess {
    private final AuthorizationService authorization;

    RealtimeAccess(AuthorizationService authorization) { this.authorization = authorization; }

    void require(String enterpriseId, Authentication authentication, String capability) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || enterpriseId == null || enterpriseId.isBlank()
                || jwt.getToken().getExpiresAt() == null
                || !jwt.getToken().getExpiresAt().isAfter(Instant.now())
                || !claimedMember(jwt, enterpriseId) || !claimedCapability(jwt, enterpriseId, capability)
                || jwt.getAuthorities().stream().noneMatch(a -> ("SCOPE_" + capability).equals(a.getAuthority()))) {
            throw new AccessDeniedException("Message capability denied or expired");
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
