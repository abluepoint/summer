package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import org.springframework.stereotype.Service;

/** Target runtime never evaluates old business grants. Migration executes the pinned old bridge. */
@Service
final class LegacyAuthorizationPolicy implements AuthorizationPolicy {
    private final AuthorizationService legacy;
    private final LegacyResourceAdapter resources;
    LegacyAuthorizationPolicy(AuthorizationService legacy, LegacyResourceAdapter resources) {
        this.legacy = legacy; this.resources = resources;
    }
    @Override public String mode() { return "LEGACY"; }
    @Override public ResourceAuthorization.Decision decide(ResourceAuthorization.Request request, long revision) {
        return new ResourceAuthorization.Decision(false,"CONTROLLED_MANAGED_UPGRADE_REQUIRED",revision,request.domain(),request.resource());
    }
}
