package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.UnifiedAuthorizationService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Display qualification uses trusted installed operations and the same current resource decision.
 */
@Service
public class InterfaceOperationService {
  private record Operation(String method, String path, boolean management, List<String> scopes) {}

  private static Operation management(String method, String suffix, String... scopes) {
    return new Operation(method, suffix, true, List.of(scopes));
  }

  private static final Map<String, Operation> OPERATIONS =
      Map.ofEntries(
          Map.entry("menu.layout.write", management("PATCH", "/authorization/menu-layout")),
          Map.entry(
              "menu.manifest.write",
              management("PUT", "/api/v1/platform/authorization/menu-manifest")),
          Map.entry("groups.create", management("POST", "/permission-groups")),
          Map.entry("groups.update", management("PATCH", "/permission-groups/{id}")),
          Map.entry("groups.delete", management("DELETE", "/permission-groups/{id}")),
          Map.entry("permissions.create", management("POST", "/permissions")),
          Map.entry("permissions.update", management("PATCH", "/permissions/{id}")),
          Map.entry("permissions.delete", management("DELETE", "/permissions/{id}")),
          Map.entry("permissions.interfaces.write", management("PUT", "/permissions/{permissionId}/interfaces")),
          Map.entry("permissions.menus.write", management("PUT", "/permissions/{permissionId}/menus")),
          Map.entry("roles.create", management("POST", "/roles")),
          Map.entry("roles.update", management("PATCH", "/roles/{id}")),
          Map.entry("roles.permissions.write", management("PUT", "/roles/{id}/permissions")),
          Map.entry("roles.assign", management("PUT", "/users/{username}/roles")),
          Map.entry(
              "organization.create",
              management(
                  "POST",
                  "/org-units",
                  "enterprise.organization.write",
                  "enterprise.organizations.write")),
          Map.entry(
              "organization.update",
              management(
                  "PATCH",
                  "/org-units/{unitId}",
                  "enterprise.organization.write",
                  "enterprise.organizations.write")),
          Map.entry(
              "organization.delete",
              management(
                  "DELETE",
                  "/org-units/{unitId}",
                  "enterprise.organization.write",
                  "enterprise.organizations.write")),
          Map.entry(
              "organization.assign",
              management(
                  "PUT",
                  "/org-assignments:batch",
                  "enterprise.organization.write",
                  "enterprise.organizations.write")),
          Map.entry("members.read", management("GET", "/members", "enterprise.members.read")),
          Map.entry("members.create", management("POST", "/members", "enterprise.members.write")),
          Map.entry(
              "members.disable",
              management("POST", "/members/{memberId}:disable", "enterprise.members.write")),
          Map.entry(
              "members.enable",
              management("POST", "/members/{memberId}:enable", "enterprise.members.write")),
          Map.entry(
              "members.leave",
              management("POST", "/members/{memberId}:leave", "enterprise.members.write")),
          Map.entry(
              "identity.users.create",
              management("POST", "/api/users", "enterprise.members.write")),
          Map.entry(
              "identity.users.write",
              management("PATCH", "/api/users/{username}", "enterprise.members.write")),
          Map.entry(
              "authorization.events.retry",
              management(
                  "POST", "/api/authorization-events/{eventId}/retry", "enterprise.roles.write")),
          Map.entry(
              "customer.read",
              new Operation("GET", "/api/customers", false, List.of("customer.read"))),
          Map.entry(
              "customer.create",
              new Operation("POST", "/api/customers", false, List.of("customer.write"))),
          Map.entry(
              "customer.update",
              new Operation(
                  "PATCH", "/api/customers/{username}", false, List.of("customer.write"))),
          Map.entry(
              "customer.delete",
              new Operation(
                  "DELETE", "/api/customers/{username}", false, List.of("customer.write"))));
  private final ManagedPermissionAccess access;
  private final UnifiedAuthorizationService authorization;
  private final ManagedInterfaceResources resources;
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;
  private final ObjectProvider<com.abluepoint.summer.sample.authorization.AuthorizationService> legacy;
  private static final Set<String> BUSINESS_ACTIONS = Set.of("messages.send", "messages.ack");
  private final ObjectProvider<com.abluepoint.summer.sample.resource.BusinessOperationHints> businessHints;

  public InterfaceOperationService(
      ManagedPermissionAccess access,
      UnifiedAuthorizationService authorization,
      ManagedInterfaceResources resources,
      ObjectProvider<RequestMappingHandlerMapping> mappings,
      ObjectProvider<com.abluepoint.summer.sample.authorization.AuthorizationService> legacy,
      ObjectProvider<com.abluepoint.summer.sample.resource.BusinessOperationHints> businessHints) {
    this.access = access;
    this.authorization = authorization;
    this.resources = resources;
    this.mappings = mappings;
    this.legacy = legacy;
    this.businessHints = businessHints;
  }

  public Map<String, Boolean> qualify(
      Domain domain, Authentication actor, List<String> actions, String requestId) {
    if (actions == null
        || actions.isEmpty()
        || actions.size() > 100
        || actions.stream().anyMatch(x -> !OPERATIONS.containsKey(x) && !BUSINESS_ACTIONS.contains(x)))
      throw SummerException.withMessage(
          PermissionErrorCode.INVALID, "Known operation actions required");
    Map<String, Boolean> result = new LinkedHashMap<>();
    boolean managed = access.managed(domain);
    for (String key : actions) {
      if (!managed) {
        result.put(key, legacyAllowed(domain, actor, key, requestId));
        continue;
      }
      if (BUSINESS_ACTIONS.contains(key)) {
        var providers = businessHints.orderedStream().filter(h -> h.supports(key)).toList();
        result.put(key, access.active(domain, actor) && providers.size() == 1
            && providers.getFirst().allowed(domain, actor, key));
        continue;
      }
      var operation = OPERATIONS.get(key);
      boolean allowed = false;
      String path =
          operation.path().startsWith("/api/")
              ? operation.path()
              : (domain.kind().equals("PLATFORM")
                      ? "/api/v1/platform"
                      : "/api/v1/enterprises/{enterpriseId}")
                  + operation.path();
      boolean write = !Set.of("GET", "HEAD", "OPTIONS").contains(operation.method());
      boolean delegated =
          operation.scopes().isEmpty()
              ? access.delegated(domain, actor, write)
              : ManagedConsumerAccess.delegated(actor, operation.scopes().toArray(String[]::new));
      if (access.active(domain, actor) && delegated) {
        var candidates =
            mappings
                .orderedStream()
                .flatMap(m -> m.getHandlerMethods().entrySet().stream())
                .filter(
                    e ->
                        e.getKey().getPatternValues().contains(path)
                            && e.getKey().getMethodsCondition().getMethods().stream()
                                .anyMatch(v -> v.name().equals(operation.method())))
                .toList();
        if (candidates.size() == 1 && candidates.getFirst().getKey().getCustomCondition() == null) {
          var entry = candidates.getFirst();
          var mapping =
              ManagedInterfaceResources.mvc(
                  operation.method(), path, entry.getValue(), entry.getKey());
          if (!mapping.configurable() || !mapping.domainKind().equals(domain.kind())) {
            result.put(key, false);
            continue;
          }
          var resource = resources.registered(mapping);
          allowed =
              authorization
                      .decide(new ResourceAuthorization.Request(domain, actor, resource, requestId))
                      .allowed()
                  && (!operation.management() || access.roleAllows(domain, actor, resource.id()));
        }
      }
      result.put(key, allowed);
    }
    return result;
  }

  /** Target operation hints never revive the old business engine. */
  private boolean legacyAllowed(Domain domain, Authentication actor, String action, String requestId) {
    return false;
  }
}
