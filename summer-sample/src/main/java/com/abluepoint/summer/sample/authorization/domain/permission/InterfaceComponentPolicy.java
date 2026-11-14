package com.abluepoint.summer.sample.authorization.domain.permission;

import java.util.Set;

/** Exact component-owned applicant entries retain their scope, subject and state protocols. */
public final class InterfaceComponentPolicy {
  private InterfaceComponentPolicy() {}

  private static final String ROOT = "com.abluepoint.summer.sample.";
  // These installed component seams retain their own subject, attestation, scope and
  // state checks. Classification delegates to those checks; it grants no business permission.
  private static final Set<String> COMPONENT_ENTRIES = Set.of(
      "identity.SessionController#session|GET:/api/v1/session",
      "identity.SessionController#logout|DELETE:/api/v1/session",
      "agent.AgentController#register|POST:/internal/v1/agent-instances",
      "agent.AgentController#heartbeat|POST:/internal/v1/agent-instances/{instanceId}:heartbeat",
      "connection.ConnectionController#catalog|PUT:/internal/v1/connection-services/{serviceId}/connections/{connectionId}/catalog",
      "mcpauthorization.McpAuthorizationController#decide|POST:/internal/v1/mcp/execution-decisions",
      "mcpauthorization.McpAuthorizationController#claim|POST:/internal/v1/mcp/execution-permits/{id}:claim",
      "physicalconnection.PhysicalExecutionController#invoke|POST:/internal/v1/connections/{connectionId}/tools/{tool}:invoke",
      "auditledger.AuditLedgerController#append|POST:/internal/v1/audit-events",
      "auditledger.AuditLedgerController#complete|POST:/internal/v1/mcp/invocations/{invocationId}:complete",
      "auditledger.AuditLedgerController#reconcile|POST:/internal/v1/mcp/invocations/{invocationId}:reconcile",
      "authorization.policy.PolicyController#evaluate|POST:/internal/v1/authorization/evaluate",
      "operations.RuntimeDiagnosticsController#runtime|GET:/internal/v1/diagnostics/runtime",
      "operations.RedisDiagnosticsController#redis|GET:/internal/v1/diagnostics/redis");

  public static boolean componentOwned(String owner, String handler, String method, String template) {
    if (applicant(owner, method, template)) return true;
    if (!owner.startsWith(ROOT)) return false;
    String verb = "HEAD".equals(method) ? "GET" : method;
    return COMPONENT_ENTRIES.contains(owner.substring(ROOT.length()) + "#" + handler + "|" + verb + ":" + template);
  }

  public static boolean applicant(String owner, String method, String template) {
    return owner.equals("com.abluepoint.summer.sample.invitation.InvitationController")
        && Set.of(
                "POST:/api/v1/enterprises/{enterpriseId}/join-applications",
                "PATCH:/api/v1/enterprises/{enterpriseId}/join-applications/{id}",
                "POST:/api/v1/enterprises/{enterpriseId}/join-applications/{id}:withdraw",
                "POST:/api/v1/invitations/{token}:accept",
                "POST:/api/v1/invitations/{token}:reject")
            .contains(method + ":" + template);
  }
}
