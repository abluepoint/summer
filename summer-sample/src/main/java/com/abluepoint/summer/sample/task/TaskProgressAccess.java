package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.time.Instant;

/** Rechecks the live persisted decision before delivering another event batch. */
@Component
class TaskProgressAccess {
    private static final String PERMISSION = "GET:/api/v1/enterprises/{enterpriseId}/tasks/{taskId}/events";
    private final AuthorizationService authorization;

    TaskProgressAccess(AuthorizationService authorization) { this.authorization = authorization; }

    void requireReadable(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || jwt.getToken().getExpiresAt() == null || !jwt.getToken().getExpiresAt().isAfter(Instant.now())
                || jwt.getAuthorities().stream().noneMatch(authority -> "SCOPE_enterprise.tasks.read".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Task progress access expired");
        }
        authorization.requireEnterpriseMembership(enterpriseId, authentication);
        if (!authorization.decision(authentication, enterpriseId, PERMISSION, null).allowed()) {
            throw new AccessDeniedException("Task progress access revoked");
        }
    }
}
