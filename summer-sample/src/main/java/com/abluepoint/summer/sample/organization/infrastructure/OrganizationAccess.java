package com.abluepoint.summer.sample.organization.infrastructure;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class OrganizationAccess {
    private OrganizationAccess() {}
    public static JwtAuthenticationToken requireJwtMember(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !isActiveMember(jwt, enterpriseId)) throw new AccessDeniedException("The principal is not an active member of the enterprise context");
        return jwt;
    }

    public static void requireCapability(JwtAuthenticationToken jwt, String enterpriseId, String... required) {
        Object claim = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(claim instanceof Map<?, ?> capabilityMap) || !capabilityMap.containsKey(enterpriseId)) throw new AccessDeniedException("The principal lacks organization capability");
        Object values = capabilityMap.get(enterpriseId);
        if (!(values instanceof Collection<?> collection) || collection.stream().map(String::valueOf).noneMatch(value -> List.of(required).contains(value))) throw new AccessDeniedException("The principal lacks organization capability");
    }

    private static boolean isActiveMember(JwtAuthenticationToken jwt, String enterpriseId) {
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> values)) return false;
        return values.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(value -> enterpriseId.equals(valueOf(value, "enterpriseId", "enterprise_id"))
                && "ACTIVE".equalsIgnoreCase(valueOf(value, "status", "membership_status")));
    }

    private static String valueOf(Map<?, ?> map, String first, String second) { Object value = map.get(first); if (value == null) value = map.get(second); return value == null ? null : value.toString(); }
}
