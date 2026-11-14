package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Map;
import org.springframework.security.core.Authentication;

/** Existing menu URLs consume one managed metadata/navigation store through this public port. */
public interface ManagedMenuOperations {
  record Mutation(Object body, int status, long version) {}

  boolean managed(Domain domain);

  /** Preserve the existing authorization-before-metadata-validation boundary. */
  void require(Domain domain, Authentication actor);

  Map<String, Object> read(Domain domain, Authentication actor, boolean manifest);

  Map<String, Object> visible(Domain domain, Authentication actor);

  Mutation mutate(
      Domain domain,
      Authentication actor,
      String menuId,
      Map<String, Object> body,
      String etag,
      String key,
      String requestId);
}
