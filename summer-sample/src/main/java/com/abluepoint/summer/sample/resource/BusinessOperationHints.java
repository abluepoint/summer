package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.security.core.Authentication;

/** Closed business operation hint extension. A hint never authorizes an invocation. */
public interface BusinessOperationHints {
  boolean supports(String action);
  boolean allowed(Domain domain, Authentication actor, String action);
}
