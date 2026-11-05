package com.abluepoint.summer.sample.authorization.application.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService.Comparison;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService.Verification;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import java.util.*;

/** Validates a trusted operator receipt; it does not perform HTTP calls. */
public final class AuthorizationDataRuntimeEvidence {
  private AuthorizationDataRuntimeEvidence() {}
  public static List<Comparison> validate(AuthorizationDataMigrationPlan plan, String sourceFingerprint,
      String planHash, Verification evidence) {
    if (evidence == null || !"summer-data-http-comparison/1".equals(evidence.format())
        || !sourceFingerprint.equals(evidence.sourceFingerprint()) || !planHash.equals(evidence.planHash())
        || evidence.runtimeInputsFingerprint() == null || !evidence.runtimeInputsFingerprint().matches("[0-9a-f]{64}")
        || evidence.comparisons() == null || evidence.comparisons().isEmpty())
      throw invalid("Source-bound runtime artifact, exact planHash and comparisons required");
    var required = new HashSet<List<String>>();
    var known = new HashSet<List<String>>();
    for (var item : plan.items()) {
      var key = List.of(item.subjectKind(), item.subject(), item.resource(), item.action());
      if (item.status().equals("PROVED")) known.add(key);
      if (item.status().equals("PROVED"))
        for (String operation : item.action().equals("read") ? List.of("list", "total", "detail") : List.of("write"))
          required.add(List.of(item.subjectKind(), item.subject(), item.resource(), item.action(), operation));
    }
    var normalized = new ArrayList<Comparison>();
    for (var comparison : evidence.comparisons()) {
      if (comparison == null || comparison.subjectKind() == null || comparison.subject() == null
          || comparison.resource() == null || comparison.action() == null || comparison.operation() == null
          || comparison.oldAllowedIds() == null || comparison.newAllowedIds() == null
          || comparison.oldStatus() < 100 || comparison.oldStatus() > 599 || comparison.newStatus() < 100 || comparison.newStatus() > 599)
        throw invalid("Complete comparison cells and actual HTTP statuses required");
      var key = List.of(comparison.subjectKind(), comparison.subject(), comparison.resource(), comparison.action());
      if (!known.contains(key)) throw invalid("Comparison does not belong to the proved matrix");
      var operations = comparison.action().equals("read") ? Set.of("list", "total", "detail") : Set.of("write");
      if (!operations.contains(comparison.operation())) throw invalid("Unsupported comparison operation for this resource/action");
      boolean oldAllowed = allowedOutcome(comparison.oldStatus());
      boolean newAllowed = allowedOutcome(comparison.newStatus());
      if (oldAllowed != newAllowed) throw conflict("HTTP outcome changed between success and controlled denial");
      if (!oldAllowed && (!comparison.oldAllowedIds().isEmpty() || !comparison.newAllowedIds().isEmpty()))
        throw invalid("Controlled HTTP denial cannot contain allowed row identifiers");
      var oldIds = comparisonIds(comparison.oldAllowedIds());
      var newIds = comparisonIds(comparison.newAllowedIds());
      if (!oldIds.equals(newIds)) throw conflict("HTTP comparison has extra or missing allowed rows");
      required.remove(List.of(comparison.subjectKind(), comparison.subject(), comparison.resource(), comparison.action(), comparison.operation()));
      normalized.add(new Comparison(comparison.subjectKind(), comparison.subject(), comparison.resource(), comparison.action(),
          comparison.operation(), oldIds, newIds, comparison.oldStatus(), comparison.newStatus()));
    }
    if (!required.isEmpty()) throw conflict("HTTP coverage missing subject/default/resource/action/operation cells");
    return normalized;
  }
  private static boolean allowedOutcome(int status) {
    if (status >= 200 && status <= 299) return true;
    if (status == 403 || status == 404) return false;
    // 401 proves invalid credentials, not a policy deny. Redirects, malformed requests,
    // business conflicts and dependency errors are inconclusive fixture/runtime failures.
    throw invalid("HTTP status is not a successful operation or controlled 403/404 denial");
  }
  private static List<String> comparisonIds(List<String> ids) {
    if (ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 200)) throw invalid("Concrete allowed row identifiers required");
    return ids.stream().sorted().toList();
  }
  private static SummerException invalid(String message) { return SummerException.withMessage(PermissionErrorCode.INVALID, message); }
  private static SummerException conflict(String message) { return SummerException.withMessage(PermissionErrorCode.CONFLICT, message); }
}
