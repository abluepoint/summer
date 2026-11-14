package com.abluepoint.summer.sample.authorization.domain.permission;

import java.util.Set;

/** Installed management consumers. This classification grants no access. */
public final class ManagementConsumerRoutes {
  private static final String ROOT = "com.abluepoint.summer.sample.";
  private static final Set<String> OWNERS =
      Set.of(
          "authorization.AuthorizationController",
          "authorization.AuthorizationChangeEventController",
          "authorization.DataScopeController",
          "authorization.OAuthClientManagementController",
          "authorization.policy.PolicyController",
          "enterprise.EnterpriseDirectoryController",
          "enterprise.EnterpriseReviewController",
          "enterprise.PlatformEnterpriseDirectoryController",
          "identity.AuthUserController",
          "identity.CapabilityController",
          "invitation.InvitationController",
          "key.KeyCapabilityController",
          "key.ReferenceKeyCapabilityController",
          "member.MemberController",
          "menu.MenuManifestController",
          "organization.OrganizationController",
          "resource.InterfaceResourceCatalogController");

  private ManagementConsumerRoutes() {}

  public static boolean management(String owner) {
    return owner.startsWith(ROOT + "authorization.api.permission.")
        || owner.startsWith(ROOT) && OWNERS.contains(owner.substring(ROOT.length()));
  }

  public static String domainKind(String owner, String path) {
    if (path.startsWith("/api/v1/platform/")) return "PLATFORM";
    if (path.startsWith("/api/v1/enterprises/{enterpriseId}/")) return "ENTERPRISE";
    if (owner.equals(ROOT + "identity.AuthUserController")
        || owner.equals(ROOT + "authorization.OAuthClientManagementController")) return "PLATFORM";
    if (owner.equals(ROOT + "authorization.AuthorizationChangeEventController"))
      return "ENTERPRISE";
    return BusinessConsumerRoutes.domainKind(owner, path);
  }
}
