package com.abluepoint.summer.sample.member.infrastructure;

import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class MemberAccess {
    private MemberAccess() {}
    public static void requireMembership(String enterpriseId, Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("enterprise membership required");
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> values) || values.stream().noneMatch(v -> v instanceof Map<?, ?> m && com.abluepoint.summer.sample.member.domain.MemberRules.activeMembership(enterpriseId, String.valueOf(m.get("enterpriseId")), String.valueOf(m.get("status"))))) {
            throw new AccessDeniedException("enterprise membership required");
        }
    }

}
