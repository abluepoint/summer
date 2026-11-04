package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization;

/** One authoritative implementation per persisted domain mode; never combines policy results. */
public interface AuthorizationPolicy {
    String mode();
    ResourceAuthorization.Decision decide(ResourceAuthorization.Request request, long revision);
}
