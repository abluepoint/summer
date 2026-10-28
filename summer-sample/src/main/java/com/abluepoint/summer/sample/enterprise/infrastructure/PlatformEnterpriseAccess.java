package com.abluepoint.summer.sample.enterprise.infrastructure;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class PlatformEnterpriseAccess {
    private PlatformEnterpriseAccess() {}
    public static void require(Authentication auth, String capability) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("platform capability required");
        Map<String, Object> claims = jwt.getToken().getClaims();
        Object memberships = claims.get("enterprise_memberships");
        if (memberships instanceof Collection<?> values && !values.isEmpty() && !"USER".equals(String.valueOf(claims.get("principal_type"))))
            throw new AccessDeniedException("platform identity required");
        Object claim = claims.get("platform_capabilities");
        if (!(claim instanceof Collection<?> values) || values.stream().noneMatch(value -> capability.equals(String.valueOf(value))))
            throw new AccessDeniedException("platform capability required");
    }
}
