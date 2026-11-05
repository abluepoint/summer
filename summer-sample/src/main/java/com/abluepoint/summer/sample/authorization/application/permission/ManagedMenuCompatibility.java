package com.abluepoint.summer.sample.authorization.application.permission;

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

  private void require(Domain domain, Authentication actor) {
    access.require(
        domain,
        actor,
        domain.kind().equals("PLATFORM") ? "platform.menu.write" : "enterprise.menu.write");
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
    var changed = catalog.mutate(domain, actor.getName(), menuId, body, etag, key, requestId);
    return new Mutation(changed.body(), changed.status(), changed.version());
  }
}
