package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.security.core.Authentication;

/** Business invocation gate. Only an explicitly persisted LEGACY domain returns false. */
public interface BusinessResourceAccess {
  boolean requireCurrent(Authentication actor, String... delegatedScopes);

  boolean require(Domain domain, Authentication actor, String... delegatedScopes);

  /** Fixed public configuration transport retains its installed protocol and current DATA bounds. */
  boolean requirePublicConfigurationRead(Domain domain, Authentication actor, String resource,
      String... delegatedScopes);

  /** Requalifies a trusted, uniquely installed HTTP operation for aggregates and continuous delivery. */
  boolean requireHttp(Domain domain, Authentication actor, String method, String registeredPath,
      String... delegatedScopes);

  /** Materializes a trusted source read inside a fresh managed revision-checked callback. */
  <T> T readManagedHttp(Domain domain,Authentication actor,String method,String registeredPath,
      java.util.function.Supplier<T> query,String... delegatedScopes);

  /** Existing native boundaries keep their established denial message and exception cause. */
  default boolean requirePreservingDenial(Domain domain, Authentication actor, String message,
      String... delegatedScopes) {
    try { return require(domain, actor, delegatedScopes); }
    catch (org.springframework.security.access.AccessDeniedException denied) {
      throw new org.springframework.security.access.AccessDeniedException(message, denied);
    }
  }

  boolean requireRuntime(
      Domain domain, Authentication actor, RuntimeBusinessResources installed, String operation,
      String... delegatedScopes);
}
