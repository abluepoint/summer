package com.abluepoint.summer.sample.authorization.domain.permission;

import java.util.Set;

/** Closed installed business surface; component and protocol endpoints stay outside it. */
public final class BusinessConsumerRoutes {
  private static final String ROOT = "com.abluepoint.summer.sample.";
  private static final Set<String> ENTERPRISE = Set.of(
      "configuration.ConfigurationOverrideController", "dictionary.DictionaryController",
      "dictionary.DictionaryNotificationController", "systemparameter.SystemParameterController",
      "systemparameter.SystemParameterCountController", "systemparameter.SystemParameterEventController",
      "systemparameter.PublicSystemParameterController",
      "statistics.StatisticsController", "realtime.RealtimeMessageController",
      "task.TaskProgressController", "task.TaskSchedulerController");
  private static final Set<String> PLATFORM = Set.of(
      "customer.CustomerController", "customer.CustomerAuditController", "key.KeyCapabilityController", "key.ReferenceKeyCapabilityController",
      "release.ReleaseGateController");
  private BusinessConsumerRoutes() {}
  public static String domainKind(String owner, String path) {
    if (!owner.startsWith(ROOT)) return "COMPONENT";
    String relative = owner.substring(ROOT.length());
    if (relative.equals("identity.SessionController") && Set.of(
        "/api/v1/sessions", "/api/v1/sessions/{reference}").contains(path)) return "PLATFORM";
    if (relative.equals("delegation.DelegationController")) {
      if (path.startsWith("/api/v1/enterprises/{e}/") || Set.of(
          "/internal/v1/mcp/approvals", "/api/v1/mcp/approvals/{id}:approve",
          "/api/v1/mcp/approvals/{id}:reject", "/api/v1/mcp/approvals/{id}:withdraw",
          "/api/v1/mcp/approvals/{id}:recheck", "/api/v1/mcp/approvals/{id}/audit").contains(path))
        return "ENTERPRISE";
      return "COMPONENT";
    }
    if (relative.equals("mcpauthorization.McpAuthorizationController")
        && path.startsWith("/api/v1/enterprises/{e}/")) return "ENTERPRISE";
    if (ENTERPRISE.contains(relative)) return "ENTERPRISE";
    if (PLATFORM.contains(relative)) return "PLATFORM";
    return "COMPONENT";
  }
}
