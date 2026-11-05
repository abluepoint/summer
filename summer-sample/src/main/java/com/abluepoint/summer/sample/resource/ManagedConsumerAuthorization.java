package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.security.core.Authentication;

/** Current resource authorization for existing service gates; false selects the legacy adapter. */
public interface ManagedConsumerAuthorization {
  boolean require(Domain domain, Authentication actor, String... delegatedScopes);

  boolean managed(Domain domain);

  boolean organizationWriteAllowed(Domain domain, Authentication actor);
}
