package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import org.springframework.stereotype.Service;

/** The sole legacy policy adapter. Old service checks remain until consumer batches 09 and 10. */
@Service
final class LegacyAuthorizationPolicy implements AuthorizationPolicy {
    private final AuthorizationService legacy;
    private final LegacyResourceAdapter resources;
    LegacyAuthorizationPolicy(AuthorizationService legacy, LegacyResourceAdapter resources) {
        this.legacy = legacy; this.resources = resources;
    }
    @Override public String mode() { return "LEGACY"; }
    @Override public ResourceAuthorization.Decision decide(ResourceAuthorization.Request request, long revision) {
        String code = resources.permission(request.resource());
        var old = code == null ? new AuthorizationService.Decision(false, "UNREGISTERED_RESOURCE")
                : "PLATFORM".equals(request.domain().kind())
                    ? legacy.platformDecision(request.principal(), code, request.requestId())
                    : legacy.decision(request.principal(), request.domain().id(), code, request.requestId());
        return new ResourceAuthorization.Decision(old.allowed(), old.reasonCode(), revision, request.domain(), request.resource());
    }
}
