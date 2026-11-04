package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.sample.authorization.*;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import org.springframework.stereotype.Service;

@Service
final class ManagedAuthorizationPolicy implements AuthorizationPolicy {
  private final ManagedPermissionAccess access;

  ManagedAuthorizationPolicy(ManagedPermissionAccess access) {
    this.access = access;
  }

  @Override
  public String mode() {
    return "MANAGED";
  }

  @Override
  public ResourceAuthorization.Decision decide(ResourceAuthorization.Request r, long revision) {
    boolean allowed =
        access.allows(r.domain(), r.principal(), r.resource().type(), r.resource().id());
    return new ResourceAuthorization.Decision(
        allowed,
        allowed ? "CURRENT_PERMISSION_ALLOW" : "NO_APPLICABLE_PERMISSION",
        revision,
        r.domain(),
        r.resource());
  }
}
