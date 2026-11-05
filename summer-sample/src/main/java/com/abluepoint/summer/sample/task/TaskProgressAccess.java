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

    private final com.abluepoint.summer.sample.resource.BusinessResourceAccess business;
    TaskProgressAccess(AuthorizationService authorization, com.abluepoint.summer.sample.resource.BusinessResourceAccess business) {
        this.authorization = authorization; this.business = business;
    }

    void requireReadable(String enterpriseId, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || jwt.getToken().getExpiresAt() == null || !jwt.getToken().getExpiresAt().isAfter(Instant.now())) {
            throw new AccessDeniedException("Task progress access expired");
        }
        boolean managed;
        try {
            managed = business.requireHttp(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId),
                    authentication, "GET", "/api/v1/enterprises/{enterpriseId}/tasks/{taskId}/events", "enterprise.tasks.read");
        } catch (AccessDeniedException denied) {
            throw new AccessDeniedException("Task progress access revoked", denied);
        }
        if (managed) {
            authorization.requireEnterpriseMembership(enterpriseId, authentication);
            return;
        }
        if (jwt.getAuthorities().stream().noneMatch(authority -> "SCOPE_enterprise.tasks.read".equals(authority.getAuthority())))
            throw new AccessDeniedException("Task progress access expired");
        authorization.requireEnterpriseMembership(enterpriseId, authentication);
        if (!authorization.decision(authentication, enterpriseId, PERMISSION, null).allowed()) {
            throw new AccessDeniedException("Task progress access revoked");
        }
    }
}
