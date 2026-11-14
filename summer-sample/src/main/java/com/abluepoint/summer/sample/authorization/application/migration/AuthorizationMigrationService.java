package com.abluepoint.summer.sample.authorization.application.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceAssociations;
import com.abluepoint.summer.sample.authorization.application.permission.TrustedPermissionResourceResolver;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationMigrationPlanner;
import com.abluepoint.summer.sample.authorization.domain.migration.LegacyAuthorizationSnapshot;
import com.abluepoint.summer.sample.authorization.domain.migration.MigrationPlan;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Internal operator boundary; deliberately has no HTTP controller and cannot select engine mode.
 */
@Service
public class AuthorizationMigrationService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final LegacyAuthorizationReader source;
  private final List<TrustedPermissionResourceResolver> resolvers;
  private final PermissionResourceAssociations associations;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;
  private final RuntimeMigrationResolution runtimeResolution;

  public AuthorizationMigrationService(
      JdbcTemplate db,
      ObjectMapper json,
      LegacyAuthorizationReader source,
      List<TrustedPermissionResourceResolver> resolvers,
      PermissionResourceAssociations associations,
      AuthorizationAuditStore audit,
      AuthorizationChangeEventService events,
      RuntimeMigrationResolution runtimeResolution) {
    this.db = db;
    this.json = json;
    this.source = source;
    this.resolvers = List.copyOf(resolvers);
    this.associations = associations;
    this.audit = audit;
    this.events = events;
    this.runtimeResolution = runtimeResolution;
  }

  private static SummerException conflict(String message) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT, message);
  }

  @Transactional
  public Map<String, Object> capture(Domain domain, RuntimeExport exported) {
    resolvers.forEach(r -> r.prepare(domain));
    var state = lockDomain(domain);
    var captured = source.read(domain, ((Number) state.get("revision")).longValue(), exported);
    var snapshot = captured.snapshot();
    if (!db.queryForList(
            "select snapshot_id from summer_authorization_migration_snapshot where snapshot_id=?",
            snapshot.snapshotId())
        .isEmpty()) return report(snapshot.snapshotId());
    var plan = new AuthorizationMigrationPlanner().plan(snapshot);
    var blockers = new ArrayList<>(plan.blockers());
    for (String table :
        List.of("summer_permission", "summer_permission_role", "summer_permission_user"))
      if (db.queryForObject(
              "select count(*) from " + table + " where domain_key=?", Integer.class, domain.key())
          > 0) blockers.add("EXISTING_TARGET_FACTS_REQUIRE_RECONCILIATION:" + table);
    if (!blockers.equals(plan.blockers()))
      plan =
          new MigrationPlan(
              plan.snapshotId(),
              plan.domainKey(),
              plan.sourceRevision(),
              plan.permissions(),
              plan.roles(),
              plan.assignments(),
              plan.mappings(),
              List.copyOf(blockers));
    db.update(
        "insert into"
            + " summer_authorization_migration_snapshot(snapshot_id,domain_key,source_revision,expected_revision,source_fingerprint,snapshot_json,evidence_json,plan_json,status)"
            + " values (?,?,?,?,?,?,?,?,?)",
        snapshot.snapshotId(),
        domain.key(),
        snapshot.revision(),
        snapshot.revision(),
        captured.fingerprint(),
        json.writeValueAsString(snapshot),
        captured.evidenceJson(),
        json.writeValueAsString(plan),
        hardBlocked(plan) ? "BLOCKED" : "PLANNED");
    int ordinal = 0;
    for (var mapping : plan.mappings())
      db.update(
          "insert into summer_authorization_migration_mapping values (?,?,?,?,?,?)",
          snapshot.snapshotId(),
          ordinal++,
          mapping.sourceKind(),
          mapping.sourceId(),
          mapping.targetKind(),
          mapping.targetId());
    for (var mapping : json.readTree(captured.evidenceJson()).path("exactResourceMappings"))
      db.update(
          "insert into summer_authorization_migration_mapping values (?,?,?,?,?,?)",
          snapshot.snapshotId(),
          ordinal++,
          "LEGACY_INTERFACE",
          mapping.path("legacyIdentity").asString(),
          mapping.path("status").asString().equals("EXACT") ? "RESOURCE" : "INVENTORY",
          mapping.path("resourceId").asString());
    for (var mapping : json.readTree(captured.evidenceJson()).path("runtimeResourceMappings"))
      db.update(
          "insert into summer_authorization_migration_mapping values (?,?,?,?,?,?)",
          snapshot.snapshotId(),
          ordinal++,
          "RUNTIME_OPERATION",
          mapping.path("resourceId").asString(),
          "INVENTORY",
          "BLOCKED_SIGNED_CLAIM_EQUIVALENCE");
    int step = 0;
    for (var p : plan.permissions()) addStep(snapshot.snapshotId(), step++, "PERMISSION", p.id());
    for (var r : plan.roles()) addStep(snapshot.snapshotId(), step++, "ROLE", r.id());
    for (var u : plan.assignments()) addStep(snapshot.snapshotId(), step++, "USER", u.subject());
    return report(snapshot.snapshotId());
  }

  /** Trusted operator continuation of an already captured immutable bridge input. */
  @Transactional
  public Map<String, Object> stageFrozen(
      String snapshotId, String expectedSourceFingerprint, int limit, String actor) {
    if (expectedSourceFingerprint == null || !expectedSourceFingerprint.matches("[0-9a-f]{64}"))
      throw SummerException.withMessage(
          PermissionErrorCode.INVALID, "Exact frozen source fingerprint required");
    return stageInternal(snapshotId, limit, actor, expectedSourceFingerprint);
  }

  @Transactional
  public Map<String, Object> stage(String snapshotId, int limit, String actor) {
    return stageInternal(snapshotId, limit, actor, null);
  }

  private Map<String, Object> stageInternal(
      String snapshotId, int limit, String actor, String frozenFingerprint) {
    if (limit < 1 || limit > 10000 || actor == null || actor.isBlank() || actor.length() > 128)
      throw SummerException.withMessage(
          PermissionErrorCode.INVALID, "Explicit operator and batch size 1–10000 required");
    var initial = snapshot(snapshotId, false);
    var frozen =
        json.readValue(initial.get("snapshot_json").toString(), LegacyAuthorizationSnapshot.class);
    Domain domain = new Domain(frozen.domainKind(), frozen.domainId());
    resolvers.forEach(r -> r.prepare(domain));
    var state = lockDomain(domain);
    var row = snapshot(snapshotId, true);
    var plan = json.readValue(row.get("plan_json").toString(), MigrationPlan.class);
    var effectiveInput = readDataInput(snapshotId);
    var effectivePlan = effectiveInput.plan();
    if (hardBlocked(effectivePlan)) throw conflict("Migration contains unresolved blockers; inspect report");
    runtimeResolution.requireNoTargetRuntimeAssociation(effectiveInput);
    if (((Number) state.get("revision")).longValue()
        != ((Number) row.get("expected_revision")).longValue())
      throw conflict("Authorization revision changed; a new dry-run is required");
    if (frozenFingerprint == null) {
      var exported =
          json.readValue(
              json.readTree(row.get("evidence_json").toString()).get("runtimeExport").toString(),
              RuntimeExport.class);
      var current = source.read(domain, ((Number) state.get("revision")).longValue(), exported);
      if (!row.get("source_fingerprint").equals(current.fingerprint()))
        throw conflict("Frozen legacy source changed; a new dry-run is required");
    } else {
      verifyFrozenEvidence(row, frozenFingerprint);
      if (!domain.kind().equals("ENTERPRISE"))
        throw conflict("Frozen continuation requires an enterprise bridge snapshot");
      var evidence = json.readTree(row.get("evidence_json").toString());
      if (!evidence
          .path("legacyFactsFingerprint")
          .asString()
          .equals(source.currentLegacyFactsFingerprint(domain)))
        throw conflict("Frozen persistent legacy facts changed; continuation rejected");
      // Validate every incarnation, including permissions applied by an earlier batch.
      for (var permission : plan.permissions())
        resolver(permission.type()).resolve(domain, permission.resourceId());
    }
    var steps =
        db.queryForList(
            "select * from summer_authorization_migration_step where snapshot_id=? and"
                + " applied=false order by step_no limit ? for update",
            snapshotId,
            limit);
    String group = AuthorizationMigrationPlanner.stableId("group", domain.key());
    for (var step : steps) {
      String target = step.get("target_id").toString();
      switch (step.get("kind").toString()) {
        case "PERMISSION" -> {
          var permission =
              plan.permissions().stream()
                  .filter(p -> p.id().equals(target))
                  .findFirst()
                  .orElseThrow();
          db.update(
              "insert into summer_permission_group values (?,?,null,'Migrated permissions',0,1) on"
                  + " duplicate key update group_id=group_id",
              domain.key(),
              group);
          db.update(
              "insert into summer_permission_identity values (?,?,?,?)",
              domain.key(),
              target,
              target,
              permission.type());
          db.update(
              "insert into summer_permission values (?,?,?,?,?,?,?,?,'ACTIVE',1)",
              domain.key(),
              target,
              target,
              permission.type() + " " + permission.resourceId(),
              "Frozen legacy migration; see snapshot " + snapshotId,
              permission.type(),
              permission.accessMode(),
              group);
          var resolver = resolver(permission.type());
          associations.bind(domain, target, resolver.resolve(domain, permission.resourceId()));
        }
        case "ROLE" -> {
          var role =
              plan.roles().stream().filter(r -> r.id().equals(target)).findFirst().orElseThrow();
          db.update(
              "insert into"
                  + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at)"
                  + " values (?,?,?,"
                  + " ?,false,'ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
              target,
              domain.id(),
              "Migrated " + target,
              domain.kind() + ":" + target);
          db.update("insert into summer_permission_role values (?,?)", domain.key(), target);
          for (String permission : role.permissionIds().stream().sorted().toList())
            db.update(
                "insert into summer_role_permission values (?,?,?)",
                domain.key(),
                target,
                permission);
        }
        case "USER" -> {
          var assignment =
              plan.assignments().stream()
                  .filter(u -> u.subject().equals(target))
                  .findFirst()
                  .orElseThrow();
          // Old role_ids and denied_capability_codes remain untouched throughout expansion.
          db.update("insert into summer_permission_user values (?,?,1)", domain.key(), target);
          db.update(
              "insert into summer_permission_user_role values (?,?,?)",
              domain.key(),
              target,
              assignment.roleId());
        }
        default -> throw conflict("Unknown migration ledger step");
      }
      String request = snapshotId + ":" + step.get("step_no");
      audit.mutation(
          domain.key(), target, "AUTHORIZATION_MIGRATION_" + step.get("kind"), request, actor);
      events.enqueue(domain, "MIGRATION", target, "AUTHORIZATION_MIGRATED", 1, request);
      long revision =
          db.queryForObject(
              "select revision from summer_authorization_domain where domain_key=?",
              Long.class,
              domain.key());
      db.update(
          "update summer_authorization_migration_step set applied=true,applied_revision=? where"
              + " snapshot_id=? and step_no=?",
          revision,
          snapshotId,
          step.get("step_no"));
      db.update(
          "update summer_authorization_migration_snapshot set expected_revision=?,status='STAGING'"
              + " where snapshot_id=?",
          revision,
          snapshotId);
    }
    if (db.queryForObject(
            "select count(*) from summer_authorization_migration_step where snapshot_id=? and"
                + " applied=false",
            Integer.class,
            snapshotId)
        == 0)
      db.update(
          "update summer_authorization_migration_snapshot set status=? where snapshot_id=?",
          plan.blockers().isEmpty() ? "STAGED" : "DATA_PENDING",
          snapshotId);
    return report(snapshotId);
  }

  /** Read the trusted persisted v1 source after live legacy annotation code has been removed. */
  @Transactional(readOnly = true)
  public DataInput readFrozenInput(String snapshotId) {
    var row = snapshot(snapshotId, false);
    verifyFrozenEvidence(row, row.get("source_fingerprint").toString());
    return readDataInput(snapshotId);
  }

  /** A separate derived proof cannot modify the original snapshot, mappings or blocker report. */
  @Transactional
  public Map<String, Object> resolveRuntime(String snapshotId, String actor) {
    var input = readFrozenInput(snapshotId);
    lockDomain(input.domain());
    if (!json.readTree(input.evidenceJson()).path("legacyFactsFingerprint").asString()
        .equals(source.currentLegacyFactsFingerprint(input.domain())))
      throw conflict("Frozen source changed before runtime resolution");
    return runtimeResolution.record(input, actor);
  }

  private void verifyFrozenEvidence(Map<String, Object> row, String expectedFingerprint) {
    String encoded = row.get("evidence_json").toString();
    if (!expectedFingerprint.equals(row.get("source_fingerprint"))
        || !expectedFingerprint.equals(LegacyAuthorizationReader.digest(encoded)))
      throw conflict("Frozen input fingerprint mismatch");
    var evidence = json.readTree(encoded);
    if (!LegacyAuthorizationReader.EVIDENCE_FORMAT.equals(
            evidence.path("evidenceFormat").asString())
        || !LegacyAuthorizationReader.MAPPING_FORMAT.equals(
            evidence.path("mappingFormat").asString()))
      throw conflict("Unsupported frozen input format");
    if (!evidence.path("captureCompatibility").path("legacyAnnotationAnchor").asBoolean()
        || !"summer-sample-legacy-bridge/1"
            .equals(evidence.path("captureCompatibility").path("sourceRecipe").asString()))
      throw conflict("Unsupported frozen source recipe");
    var frozen =
        json.readValue(row.get("snapshot_json").toString(), LegacyAuthorizationSnapshot.class);
    var plan = json.readValue(row.get("plan_json").toString(), MigrationPlan.class);
    String domain = frozen.domainKind() + ":" + frozen.domainId();
    String id = row.get("snapshot_id").toString();
    if (!domain.equals(row.get("domain_key"))
        || !domain.equals(plan.domainKey())
        || !id.equals(frozen.snapshotId())
        || !id.equals(plan.snapshotId())
        || !id.equals(
            AuthorizationMigrationPlanner.stableId("snapshot", domain, expectedFingerprint))
        || !evidence
            .path("sourceProjectionHash")
            .asString()
            .equals(source.projectionFingerprint(frozen))
        || !plan.equals(new AuthorizationMigrationPlanner().plan(frozen)))
      throw conflict("Frozen projection or mapping plan mismatch");
  }

  /** Immutable persisted input for the DATA continuation, never a fresh live-source read. */
  public record DataInput(
      String snapshotId,
      Domain domain,
      long sourceRevision,
      long expectedRevision,
      String sourceFingerprint,
      LegacyAuthorizationSnapshot source,
      MigrationPlan plan,
      String evidenceJson) {}

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public DataInput readDataInput(String snapshotId) {
    var row = snapshot(snapshotId, false);
    var source =
        json.readValue(row.get("snapshot_json").toString(), LegacyAuthorizationSnapshot.class);
    return runtimeResolution.effective(new DataInput(
        snapshotId,
        new Domain(source.domainKind(), source.domainId()),
        ((Number) row.get("source_revision")).longValue(),
        ((Number) row.get("expected_revision")).longValue(),
        row.get("source_fingerprint").toString(),
        source,
        json.readValue(row.get("plan_json").toString(), MigrationPlan.class),
        row.get("evidence_json").toString()));
  }

  public Map<String, Object> report(String snapshotId) {
    var row = snapshot(snapshotId, false);
    var plan = json.readValue(row.get("plan_json").toString(), MigrationPlan.class);
    var sourceSnapshot =
        json.readValue(row.get("snapshot_json").toString(), LegacyAuthorizationSnapshot.class);
    var steps =
        db.queryForList(
            "select step_no,kind,target_id,applied,applied_revision from"
                + " summer_authorization_migration_step where snapshot_id=? order by step_no",
            snapshotId);
    var report = new LinkedHashMap<String, Object>();
    report.put("snapshotId", snapshotId);
    report.put("domainKey", plan.domainKey());
    report.put("sourceFingerprint", row.get("source_fingerprint"));
    report.put("sourceRevision", row.get("source_revision"));
    report.put("expectedRevision", row.get("expected_revision"));
    report.put("status", row.get("status"));
    report.put("blockers", plan.blockers());
    report.put("steps", steps);
    report.put("plan", plan);
    report.put("source", sourceSnapshot);
    report.put(
        "sourceMappings",
        db.queryForList(
            "select source_kind,source_id,target_kind,target_id from"
                + " summer_authorization_migration_mapping where snapshot_id=? order by ordinal",
            snapshotId));
    var evidence = json.readTree(row.get("evidence_json").toString());
    report.put("resourceMappings", evidence.path("exactResourceMappings"));
    report.put("runtimeResourceMappings", evidence.path("runtimeResourceMappings"));
    long interfaceSources = 0;
    for (var resource : evidence.path("interfaces"))
      if (resource.path("active").asBoolean() && resource.path("configurable").asBoolean())
        interfaceSources++;
    long interfacePermissions =
        plan.permissions().stream().filter(p -> p.type().equals("INTERFACE")).count();
    long menuSources =
        sourceSnapshot.resources().stream().filter(r -> r.type().equals("MENU")).count();
    long menuPermissions = plan.permissions().stream().filter(p -> p.type().equals("MENU")).count();
    report.put(
        "resourceCoverage",
        Map.of(
            "activeInterfaceSources",
            interfaceSources,
            "plannedInterfacePermissions",
            interfacePermissions,
            "blockedInterfaceSources",
            interfaceSources - interfacePermissions,
            "interfaceMappingFraction",
            interfaceSources == 0 ? 1d : (double) interfacePermissions / interfaceSources,
            "inventoriedMenuSources",
            menuSources,
            "plannedMenuPermissions",
            menuPermissions,
            "menuMappingFraction",
            menuSources == 0 ? 1d : (double) menuPermissions / menuSources));
    Map<String, Object> inventory = new TreeMap<>();
    evidence
        .properties()
        .forEach(
            entry -> {
              if (entry.getValue().isArray())
                inventory.put(entry.getKey(), entry.getValue().size());
            });
    report.put("sourceInventory", inventory);
    report.put(
        "coverage",
        Map.of(
            "capabilities",
            sourceSnapshot.capabilities().size(),
            "roles",
            sourceSnapshot.roles().size(),
            "users",
            sourceSnapshot.users().size(),
            "resources",
            sourceSnapshot.resources().size(),
            "bundleVersions",
            sourceSnapshot.bundles().size(),
            "mappings",
            db.queryForObject(
                "select count(*) from summer_authorization_migration_mapping where snapshot_id=?",
                Integer.class,
                snapshotId),
            "appliedSteps",
            steps.stream().filter(s -> Boolean.TRUE.equals(s.get("applied"))).count()));
    report.put("dataConversionAccepted", false);
    report.put("cutoverAuthorized", false);
    return Collections.unmodifiableMap(report);
  }

  private TrustedPermissionResourceResolver resolver(String type) {
    return resolvers.stream()
        .filter(r -> r.type().equals(type))
        .findFirst()
        .orElseThrow(() -> conflict("Trusted resource resolver unavailable"));
  }

  private void addStep(String snapshotId, int ordinal, String kind, String target) {
    db.update(
        "insert into summer_authorization_migration_step(snapshot_id,step_no,kind,target_id) values"
            + " (?,?,?,?)",
        snapshotId,
        ordinal,
        kind,
        target);
  }

  private static boolean hardBlocked(MigrationPlan plan) {
    return plan.blockers().stream().anyMatch(b -> !b.startsWith("PENDING_DATA:"));
  }

  private Map<String, Object> lockDomain(Domain domain) {
    var rows =
        db.queryForList(
            "select * from summer_authorization_domain where domain_key=? for update",
            domain.key());
    if (rows.size() != 1
        || !"LEGACY".equals(rows.getFirst().get("engine_mode"))
        || !(rows.getFirst().get("revision") instanceof Number revision)
        || revision.longValue() < 0)
      throw conflict("Migration expansion requires an explicit LEGACY domain");
    return rows.getFirst();
  }

  private Map<String, Object> snapshot(String id, boolean lock) {
    var rows =
        db.queryForList(
            "select * from summer_authorization_migration_snapshot where snapshot_id=?"
                + (lock ? " for update" : ""),
            id);
    if (rows.size() != 1)
      throw SummerException.withMessage(
          PermissionErrorCode.NOT_FOUND, "Migration snapshot not found");
    return rows.getFirst();
  }
}
