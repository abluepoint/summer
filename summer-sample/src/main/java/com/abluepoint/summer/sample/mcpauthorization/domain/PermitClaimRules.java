package com.abluepoint.summer.sample.mcpauthorization.domain;
import java.time.Instant;
/** A permit can be claimed once, with replay limited to the same attempt. */
public final class PermitClaimRules {
 private PermitClaimRules() {}
 public static void requireClaimable(Instant expiresAt, String status, String priorAttempt, String attempt, Instant now) {
  if (expiresAt.isBefore(now) || "REVOKED".equals(status)) throw new McpAuthorizationConflictException("Permit is not claimable");
  if (attempt == null || attempt.isBlank()) throw new McpAuthorizationValidationException("executionAttemptId is required");
  if (priorAttempt != null && !priorAttempt.equals(attempt)) throw new McpAuthorizationConflictException("Permit already claimed by another attempt");
 }
 public static boolean requiresApproval(String risk) { return "HIGH".equalsIgnoreCase(risk) || "CRITICAL".equalsIgnoreCase(risk); }
}
