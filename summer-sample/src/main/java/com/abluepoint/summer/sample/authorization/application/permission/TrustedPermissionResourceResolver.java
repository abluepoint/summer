package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Resource;

/** Resolves server-owned catalog IDs inside the domain-locked association transaction. */
public interface TrustedPermissionResourceResolver {
  String type();

  default void prepare(Domain domain) {}

  Resource resolve(Domain domain, String resourceId);
}
