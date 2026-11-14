package com.abluepoint.summer.sample.authorization.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService.DataInput;
import com.abluepoint.summer.sample.authorization.application.migration.RuntimeMigrationResolution;
import com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot;
import com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot.Capability;
import com.abluepoint.summer.sample.authorization.domain.migration.MigrationPlan;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Pure policy boundaries; actual operator/ledger/transport behavior is accepted on real MySQL. */
class RuntimeMigrationResolutionTest {
  private final ObjectMapper json = new ObjectMapper();
  private final RuntimeMigrationResolution proof = new RuntimeMigrationResolution(new JdbcTemplate(), json);
  private static final String RULE = "nonexpired signed JWT + signed active tenant membership + exact signed legacy capability claim + exact scope + current membership + persisted legacy decision";

  private DataInput input(List<Capability> capabilities, String rule, List<MigrationPlan.Permission> permissions) {
    var mappings = new ArrayList<Map<String,Object>>();
    for (String operation : List.of("read", "send")) {
      var row = new TreeMap<String,Object>();
      row.put("resourceId", "runtime-" + operation);
      row.put("oldCheckedCode", operation.equals("read") ? "enterprise.messages.read" : "enterprise.messages.write");
      row.put("installedHandler", "com.abluepoint.summer.sample.realtime.RealtimeAccess@realtimeAccess#" + operation);
      row.put("source", "RUNTIME"); row.put("method", "WS"); row.put("path", "/ws/v1/messages");
      row.put("policyVersion", "legacy-realtime-exact-code-and-signed-claim-v1");
      row.put("oldRule", rule);
      row.put("conditions", Map.of("frame", operation.equals("read") ? List.of("ping","read") : List.of("ack","send")));
      mappings.add(row);
    }
    var blockers = List.of("PENDING_DATA:operator", "RUNTIME_SIGNED_CLAIM_EQUIVALENCE_REQUIRED:runtime-read", "RUNTIME_SIGNED_CLAIM_EQUIVALENCE_REQUIRED:runtime-send");
    var source = new LegacyAuthorizationSnapshot("snapshot", "ENTERPRISE", "proof14", 7,
        capabilities, List.of(new LegacyAuthorizationSnapshot.Role("role",true,Set.of("unrelated"),null)),
        List.of(new LegacyAuthorizationSnapshot.User("operator",true,Set.of("role"),Set.of(),"constraints")),
        List.of(),List.of(),blockers);
    var plan = new MigrationPlan("snapshot","ENTERPRISE:proof14",7,permissions,List.of(),List.of(),List.of(),blockers);
    return new DataInput("snapshot",Domain.enterprise("proof14"),7,7,"a".repeat(64),source,plan,
        json.writeValueAsString(Map.of("legacyScopeCompatibility",false,"runtimeResourceMappings",mappings)));
  }

  @Test void unrelatedExistingGrantsDoNotRequireRevocationOrCredentialReplacement() {
    var source = input(List.of(new Capability("unrelated","customer.read","customers",true)), RULE,List.of());
    var derived = proof.derive(source);
    assertEquals(2,((Map<?,?>)derived.get("resources")).size());
    assertEquals(false,derived.get("cutoverAuthorized"));
    assertEquals(3,source.plan().blockers().size());
    assertTrue(source.source().roles().getFirst().active());
  }

  @Test void evenInactiveRuntimeCapabilitiesKeepTheSignedClaimCounterexampleBlocked() {
    for (boolean active : List.of(false,true))
      assertThrows(SummerException.class,()->proof.derive(input(
          List.of(new Capability("message","enterprise.messages.read","messages",active)),RULE,List.of())));
  }

  @Test void resourceCodeAliasesCannotHideTheRuntimeCounterexample() {
    assertThrows(SummerException.class,()->proof.derive(input(
        List.of(new Capability("alias","arbitrary.alias","enterprise.messages.write",false)),RULE,List.of())));
  }

  @Test void targetRuntimePermissionInvalidatesTheAbsenceRecipe() {
    assertThrows(SummerException.class,()->proof.derive(input(List.of(),RULE,
        List.of(new MigrationPlan.Permission("permission","INTERFACE","runtime-read","AUTHORIZED")))));
  }

  @Test void requiresExplicitBooleanFalseCompatibilityEvidence() {
    var original = input(List.of(), RULE, List.of());
    for (String invalid : List.of("missing", "stringFalse", "null", "true")) {
      var evidence = (tools.jackson.databind.node.ObjectNode) json.readTree(original.evidenceJson());
      switch (invalid) {
        case "missing" -> evidence.remove("legacyScopeCompatibility");
        case "stringFalse" -> evidence.put("legacyScopeCompatibility", "false");
        case "null" -> evidence.putNull("legacyScopeCompatibility");
        case "true" -> evidence.put("legacyScopeCompatibility", true);
      }
      var changed = new DataInput(original.snapshotId(), original.domain(), original.sourceRevision(),
          original.expectedRevision(), original.sourceFingerprint(), original.source(), original.plan(),
          json.writeValueAsString(evidence));
      assertThrows(SummerException.class, () -> proof.derive(changed), invalid);
    }
  }

  @Test void rejectsRewrittenOldRuntimeRule() {
    assertThrows(SummerException.class,()->proof.derive(input(List.of(),"different credential semantics",List.of())));
  }

}
