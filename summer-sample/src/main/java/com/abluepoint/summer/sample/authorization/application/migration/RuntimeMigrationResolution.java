package com.abluepoint.summer.sample.authorization.application.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService.DataInput;
import com.abluepoint.summer.sample.authorization.domain.migration.MigrationPlan;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader;
import java.util.*;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * One narrow supported recipe: no message capability exists in the complete frozen catalog,
 * even disabled or aliased. Old persisted checks are then false for every credential. Target
 * runtime resources remain unassociated. Domains with any such capability stay BLOCKED.
 */
@Component
public class RuntimeMigrationResolution {
  public static final String POLICY = "summer-runtime-no-persistent-capability/1";
  private static final String BLOCKER = "RUNTIME_SIGNED_CLAIM_EQUIVALENCE_REQUIRED:";
  private static final String OLD_RULE = "nonexpired signed JWT + signed active tenant membership + exact signed legacy capability claim + exact scope + current membership + persisted legacy decision";
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public RuntimeMigrationResolution(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  /** Pure proof over the complete verified frozen source; never removes original blockers. */
  public Map<String, Object> derive(DataInput input) {
    if (!input.domain().kind().equals("ENTERPRISE") || input.domain().id().equals("ent-acme"))
      throw conflict("This runtime recipe supports only non-bootstrap enterprise domains");
    var evidence = json.readTree(input.evidenceJson());
    var compatibility = evidence.path("legacyScopeCompatibility");
    if (!compatibility.isBoolean() || compatibility.asBoolean())
      throw conflict("Explicit disabled legacy scope fallback evidence required");
    var mappings = evidence.path("runtimeResourceMappings");
    if (!mappings.isArray() || mappings.size() != 2)
      throw conflict("Exact complete production runtime inventory required");
    var operations = new TreeMap<String, Object>();
    var codes = new TreeSet<String>();
    for (var item : mappings) {
      String code = item.path("oldCheckedCode").asString();
      String operation = code.equals("enterprise.messages.read") ? "read"
          : code.equals("enterprise.messages.write") ? "send" : "";
      String resource = item.path("resourceId").asString();
      var frames = new TreeSet<String>();
      item.path("conditions").path("frame").forEach(value -> frames.add(value.asString()));
      var expectedFrames = operation.equals("read") ? Set.of("ping", "read") : Set.of("ack", "send");
      if (operation.isEmpty() || !codes.add(code) || resource.isBlank()
          || !item.path("installedHandler").asString().equals(
              "com.abluepoint.summer.sample.realtime.RealtimeAccess@realtimeAccess#" + operation)
          || !item.path("source").asString().equals("RUNTIME")
          || !item.path("method").asString().equals("WS")
          || !item.path("path").asString().equals("/ws/v1/messages")
          || !item.path("policyVersion").asString().equals("legacy-realtime-exact-code-and-signed-claim-v1")
          || !item.path("oldRule").asString().equals(OLD_RULE)
          || item.path("conditions").size() != 1 || !frames.equals(expectedFrames)
          || !input.source().unresolved().contains(BLOCKER + resource))
        throw conflict("Unsupported or changed runtime semantics");
      // Include inactive capabilities and resource-code aliases. No role, deny, reactivation,
      // bundle history, anonymous/future identity, raw scope or signed claim can turn this
      // absent persisted capability into ALLOW during the fenced migration window.
      if (input.source().capabilities().stream().anyMatch(capability ->
          code.equals(capability.code()) || code.equals(capability.resource())))
        throw conflict("Runtime signed-claim counterexample remains BLOCKED");
      if (input.plan().permissions().stream().anyMatch(permission -> resource.equals(permission.resourceId())))
        throw conflict("Target runtime grant is outside the absence recipe");
      if (operations.put(resource, Map.of("operation", operation, "oldCheckedCode", code,
          "oldPersistedDecision", "ALWAYS_FALSE", "targetRequirement", "NO_ASSOCIATION")) != null)
        throw conflict("Duplicate runtime resource identity");
    }
    return Map.of("format", "summer-runtime-migration-proof/1", "policyVersion", POLICY,
        "snapshotId", input.snapshotId(), "domainKey", input.domain().key(),
        "sourceFingerprint", input.sourceFingerprint(), "resources", operations,
        "credentialCoverage", "ALL_CURRENT_OLD_SIGNED_AND_FUTURE_UNBOUND_CREDENTIALS",
        "cutoverAuthorized", false);
  }

  /** Called only through an explicit local operator action after frozen-input validation. */
  public Map<String, Object> record(DataInput input, String actor) {
    if (actor == null || actor.isBlank() || actor.length() > 128)
      throw conflict("Explicit runtime proof operator required");
    if (db.queryForObject("select count(*) from summer_authorization_bridge_enrollment where domain_key=?",
        Integer.class, input.domain().key()) != 1)
      throw conflict("Trusted bridge enrollment required before runtime resolution");
    var proof = derive(input);
    String encoded = canonical(proof), hash = LegacyAuthorizationReader.digest(encoded);
    var existing = db.queryForList("select proof_hash from summer_authorization_runtime_migration_proof where snapshot_id=? for update", input.snapshotId());
    if (!existing.isEmpty()) {
      if (!hash.equals(existing.getFirst().get("proof_hash"))) throw conflict("Immutable runtime proof conflict");
    } else db.update("insert into summer_authorization_runtime_migration_proof(snapshot_id,source_fingerprint,policy_version,proof_hash,proof_json,actor) values (?,?,?,?,?,?)",
        input.snapshotId(), input.sourceFingerprint(), POLICY, hash, encoded, actor);
    validateNoTargetAssociation(input);
    return Map.of("proof", proof, "proofHash", hash, "originalPlanUnchanged", true,
        "cutoverAuthorized", false);
  }

  /** Old snapshots without this new, validated receipt retain every original blocker. */
  public DataInput effective(DataInput input) {
    boolean available = Boolean.TRUE.equals(db.execute((ConnectionCallback<Boolean>) connection -> {
      try (var tables = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
        while (tables.next()) if ("summer_authorization_runtime_migration_proof".equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
        return false;
      }
    }));
    if (!available) return input;
    var rows = db.queryForList("select source_fingerprint,policy_version,proof_hash,proof_json from summer_authorization_runtime_migration_proof where snapshot_id=?", input.snapshotId());
    if (rows.isEmpty()) return input;
    var row = rows.getFirst();
    var proof = derive(input);
    String encoded = canonical(proof);
    if (!input.sourceFingerprint().equals(row.get("source_fingerprint"))
        || !POLICY.equals(row.get("policy_version")) || !encoded.equals(row.get("proof_json"))
        || !LegacyAuthorizationReader.digest(encoded).equals(row.get("proof_hash")))
      throw conflict("Frozen runtime proof changed");
    var resolved = new HashSet<String>();
    json.valueToTree(proof).path("resources").properties().forEach(entry -> resolved.add(BLOCKER + entry.getKey()));
    var plan = input.plan();
    return new DataInput(input.snapshotId(), input.domain(), input.sourceRevision(), input.expectedRevision(),
        input.sourceFingerprint(), input.source(), new MigrationPlan(plan.snapshotId(), plan.domainKey(),
            plan.sourceRevision(), plan.permissions(), plan.roles(), plan.assignments(), plan.mappings(),
            plan.blockers().stream().filter(blocker -> !resolved.contains(blocker)).toList()), input.evidenceJson());
  }

  private void validateNoTargetAssociation(DataInput input) {
    for (var mapping : json.readTree(input.evidenceJson()).path("runtimeResourceMappings"))
      if (db.queryForObject("select count(*) from summer_permission_resource where domain_key=? and resource_id=?",
          Integer.class, input.domain().key(), mapping.path("resourceId").asString()) != 0)
        throw conflict("Runtime association invalidates the no-capability proof");
  }

  /** Rechecked while the domain is locked for staging/cutover; history remains readable later. */
  public void requireNoTargetRuntimeAssociation(DataInput input) {
    validateNoTargetAssociation(input);
  }

  private String canonical(Map<String, Object> proof) {
    return json.writeValueAsString(ordered(proof));
  }

  private Object ordered(Object value) {
    if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, Object>();
      map.forEach((key, item) -> sorted.put(key.toString(), ordered(item)));
      return sorted;
    }
    if (value instanceof List<?> list) return list.stream().map(this::ordered).toList();
    return value;
  }

  private static SummerException conflict(String message) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT, message);
  }
}
