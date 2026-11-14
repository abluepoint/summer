package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Existing menu URLs delegate to the same managed metadata and navigation facts. */
@Service
public class ManagedMenuCompatibility
    implements com.abluepoint.summer.sample.resource.ManagedMenuOperations {
  private final ManagedConsumerAccess access;
  private final MenuCatalogService catalog;
  private final MenuNavigationService navigation;

  public ManagedMenuCompatibility(
      ManagedConsumerAccess access, MenuCatalogService catalog, MenuNavigationService navigation) {
    this.access = access;
    this.catalog = catalog;
    this.navigation = navigation;
  }

  public boolean managed(Domain domain) {
    return access.managed(domain);
  }

  public void require(Domain domain, Authentication actor) {
    try {
      access.require(domain, actor,
          domain.kind().equals("PLATFORM") ? "platform.menu.write" : "enterprise.menu.write");
    } catch (org.springframework.security.access.AccessDeniedException denied) {
      throw new org.springframework.security.access.AccessDeniedException(
          (domain.kind().equals("PLATFORM") ? "Platform menu capability denied" : "Authorization capability denied"), denied);
    }
  }

  public Map<String, Object> read(Domain domain, Authentication actor, boolean manifest) {
    require(domain, actor);
    return catalog.read(domain, manifest);
  }

  public Map<String, Object> visible(Domain domain, Authentication actor) {
    return navigation.visible(domain, actor);
  }

  public Mutation mutate(
      Domain domain,
      Authentication actor,
      String menuId,
      Map<String, Object> body,
      String etag,
      String key,
      String requestId) {
    require(domain, actor);
    try {
      var changed = catalog.mutate(domain, actor.getName(), menuId, body, etag, key, requestId);
      return new Mutation(changed.body(), changed.status(), changed.version());
    } catch (SummerException failure) {
      if (failure.errorCode() == PermissionErrorCode.INVALID)
        throw SummerException.withMessage(CommonErrorCode.INVALID_ARGUMENT, failure.getMessage(), failure);
      if (failure.errorCode() == PermissionErrorCode.CONFLICT)
        throw SummerException.withMessage(CommonErrorCode.CONFLICT, failure.getMessage(), failure);
      throw failure;
    }
  }
}
