package com.abluepoint.summer.sample.authorization.migration;

import static org.assertj.core.api.Assertions.*;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService.Comparison;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService.Verification;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataRuntimeEvidence;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan.Item;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorizationDataRuntimeEvidenceTest {
  private final String fingerprint = "f".repeat(64), hash = "a".repeat(64);
  private AuthorizationDataMigrationPlan plan() {
    return new AuthorizationDataMigrationPlan("snapshot", "ENTERPRISE:tenant", fingerprint, "proof", "registry",
        List.of(), List.of(new Item("USER", "alice", "dictionaries", "update", "PROVED", List.of(), List.of(), "proof", Map.of())),
        List.of(), List.of(), List.of());
  }
  private Verification evidence(int before, int after, String operation, List<String> oldIds, List<String> newIds) {
    return new Verification("summer-data-http-comparison/1", fingerprint, hash, "b".repeat(64),
        List.of(new Comparison("USER", "alice", "dictionaries", "update", operation, oldIds, newIds, before, after)));
  }
  @Test void dependencyAuthenticationRedirectAndInvalidFixtureFailuresNeverProveAnEmptyDenySet() {
    for (int status : List.of(500, 503, 401, 400, 409, 302, 100))
      assertThatThrownBy(() -> AuthorizationDataRuntimeEvidence.validate(plan(), fingerprint, hash,
          evidence(status, status, "write", List.of(), List.of())))
          .as("HTTP %s must not count as authority evidence", status).hasMessageContaining("HTTP status");
  }
  @Test void successVersusDenialIsDifferentEvenWhenBothReceiptsHaveNoRowIds() {
    assertThatThrownBy(() -> AuthorizationDataRuntimeEvidence.validate(plan(), fingerprint, hash,
        evidence(200, 403, "write", List.of(), List.of()))).hasMessageContaining("HTTP outcome");
  }
  @Test void unsupportedOperationsCannotSupplementAnOtherwiseCompleteReceipt() {
    var good = evidence(403, 404, "write", List.of(), List.of());
    var cells = new ArrayList<>(good.comparisons());
    cells.addAll(evidence(403, 404, "export", List.of(), List.of()).comparisons());
    assertThatThrownBy(() -> AuthorizationDataRuntimeEvidence.validate(plan(), fingerprint, hash,
        new Verification(good.format(),fingerprint,hash,good.runtimeInputsFingerprint(),cells)))
        .hasMessageContaining("operation");
  }
  @Test void controlledDenialAndSuccessfulWriteOutcomesKeepTheirActualStatuses() {
    assertThat(AuthorizationDataRuntimeEvidence.validate(plan(), fingerprint, hash,
        evidence(403, 404, "write", List.of(), List.of())).getFirst().newStatus()).isEqualTo(404);
    assertThat(AuthorizationDataRuntimeEvidence.validate(plan(), fingerprint, hash,
        evidence(201, 200, "write", List.of("row"), List.of("row"))).getFirst().oldStatus()).isEqualTo(201);
  }
}
