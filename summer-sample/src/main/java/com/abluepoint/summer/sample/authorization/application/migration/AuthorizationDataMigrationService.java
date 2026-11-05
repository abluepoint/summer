package com.abluepoint.summer.sample.authorization.application.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry;
import com.abluepoint.summer.sample.authorization.application.data.PermissionDataDetails;
import com.abluepoint.summer.sample.authorization.application.permission.TrustedPermissionResourceResolver;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService.DataInput;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationDataMigrationPlan.*;
import com.abluepoint.summer.sample.authorization.domain.migration.AuthorizationMigrationPlanner;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Explicit operator DATA continuation; never changes engine mode. */
@Service
public class AuthorizationDataMigrationService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuthorizationMigrationService identities;
  private final LegacyAuthorizationReader legacy;
  private final List<TrustedPermissionResourceResolver> resolvers;
  private final AuthorizationDataMigrationPlanner planner;
  private final PermissionDataDetails details;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;

  public AuthorizationDataMigrationService(JdbcTemplate db, ObjectMapper json,
      AuthorizationMigrationService identities, LegacyAuthorizationReader legacy,
      List<TrustedPermissionResourceResolver> resolvers, DataResourceRegistry registry,
      PermissionDataDetails details, AuthorizationAuditStore audit, AuthorizationChangeEventService events) {
    this.db = db;
    this.json = json;
    this.identities = identities;
    this.legacy = legacy;
    this.resolvers = List.copyOf(resolvers);
    this.planner = new AuthorizationDataMigrationPlanner(json, registry);
    this.details = details;
    this.audit = audit;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public AuthorizationDataMigrationPlan dryRun(String snapshotId) {
    return planner.plan(identities.readFrozenInput(snapshotId));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> prepare(String snapshotId, String sourceFingerprint, long expectedRevision, String actor) {
    operator(actor);
    var input = lockedInput(snapshotId, sourceFingerprint, expectedRevision);
    var plan = planner.plan(input);
    String encoded = json.writeValueAsString(plan);
    String hash = LegacyAuthorizationReader.digest(encoded);
    var existing = db.queryForList("select * from summer_authorization_data_migration where snapshot_id=?", snapshotId);
    if (!existing.isEmpty()) {
      if (!hash.equals(existing.getFirst().get("plan_hash"))) throw conflict("DATA planHash conflict; immutable proof cannot be overwritten");
      return report(snapshotId);
    }
    db.update("insert into summer_authorization_data_migration(snapshot_id,source_fingerprint,plan_hash,proof_version,registry_fingerprint,derived_plan_json,status) values (?,?,?,?,?,?,?)",
        snapshotId, sourceFingerprint, hash, plan.proofVersion(), plan.registryFingerprint(), encoded,
        plan.blockers().isEmpty() ? "PLANNED" : "BLOCKED");
    int ordinal = 0;
    for (var grant : plan.grants()) addStep(snapshotId, ordinal++, "DATA_PERMISSION", grant.id());
    var roleIds = new TreeSet<String>();
    plan.vectors().forEach(v -> roleIds.add(v.targetRoleId()));
    for (String role : roleIds) addStep(snapshotId, ordinal++, "DATA_ROLE", role);
    for (var vector : plan.vectors()) addStep(snapshotId, ordinal++, "DATA_USER", vector.subject());
    audit.mutation(input.domain().key(), snapshotId, "AUTHORIZATION_DATA_PLAN", hash, actor);
    return report(snapshotId);
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> stage(String snapshotId, String sourceFingerprint, String planHash,
      long expectedRevision, int limit, String actor) {
    operator(actor);
    if (limit < 1 || limit > 10000) throw invalid("Batch size 1–10000 required");
    var input = lockedInput(snapshotId, sourceFingerprint, expectedRevision);
    var row = proofRow(snapshotId);
    var plan = checkedPlan(input, row);
    if (!Objects.equals(planHash, row.get("plan_hash"))) throw conflict("Exact DATA planHash required");
    if (!plan.equals(planner.plan(input))) throw conflict("Frozen DATA proof or registered semantics changed");
    if (!plan.blockers().isEmpty()) throw conflict("DATA contains unresolved blockers; inspect proof report");
    if (db.queryForObject("select count(*) from summer_authorization_migration_step where snapshot_id=? and applied=false", Integer.class, snapshotId) != 0)
      throw conflict("Original identity steps must complete before DATA writes");
    validateTargets(input.domain(), plan);
    var pending = db.queryForList("select * from summer_authorization_data_migration_step where snapshot_id=? and applied=false order by step_no limit ? for update", snapshotId, limit);
    for (var step : pending) {
      String kind = step.get("kind").toString(), target = step.get("target_id").toString();
      switch (kind) {
        case "DATA_PERMISSION" -> createPermission(input.domain(), plan, target);
        case "DATA_ROLE" -> createRole(input.domain(), plan, target);
        case "DATA_USER" -> replaceUser(input.domain(), plan, target);
        default -> throw conflict("Unknown DATA checkpoint kind");
      }
      String request = "data:" + snapshotId + ":" + step.get("step_no");
      audit.mutation(input.domain().key(), target, "AUTHORIZATION_" + kind, request, actor);
      events.enqueue(input.domain(), "DATA_MIGRATION", target, "AUTHORIZATION_DATA_MIGRATED", 1, request);
      long revision = db.queryForObject("select revision from summer_authorization_domain where domain_key=?", Long.class, input.domain().key());
      db.update("update summer_authorization_data_migration_step set applied=true,applied_revision=? where snapshot_id=? and step_no=?", revision, snapshotId, step.get("step_no"));
      db.update("update summer_authorization_migration_snapshot set expected_revision=? where snapshot_id=?", revision, snapshotId);
    }
    if (db.queryForObject("select count(*) from summer_authorization_data_migration_step where snapshot_id=? and applied=false", Integer.class, snapshotId) == 0
        && !"VERIFIED".equals(row.get("status")))
      db.update("update summer_authorization_data_migration set status='APPLIED' where snapshot_id=?", snapshotId);
    return report(snapshotId);
  }

  public record Comparison(String subjectKind, String subject, String resource, String action,
      String operation, List<String> oldAllowedIds, List<String> newAllowedIds,
      int oldStatus, int newStatus) {}
  public record Verification(String format, String sourceFingerprint, String planHash,
      String runtimeInputsFingerprint, List<Comparison> comparisons) {}

  /** Accepts a trusted operator's source-bound HTTP artifact, never an HTTP mapping upload. */
  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  public Map<String, Object> verify(String snapshotId, String sourceFingerprint, String planHash,
      long expectedRevision, Verification evidence, String actor) {
    operator(actor);
    var input = lockedInput(snapshotId, sourceFingerprint, expectedRevision);
    var row = proofRow(snapshotId);
    var plan = checkedPlan(input, row);
    if (!Objects.equals(row.get("plan_hash"), planHash) || !plan.equals(planner.plan(input)))
      throw conflict("Exact current DATA proof required");
    if (!plan.blockers().isEmpty() || !Set.of("APPLIED", "VERIFIED").contains(row.get("status")))
      throw conflict("All DATA steps and blockers must complete before verification");
    validateTargets(input.domain(), plan);
    var normalized = AuthorizationDataRuntimeEvidence.validate(plan, sourceFingerprint, planHash, evidence);
    normalized.sort(Comparator.comparing(json::writeValueAsString));
    String encoded = json.writeValueAsString(new Verification(evidence.format(), sourceFingerprint, planHash,
        evidence.runtimeInputsFingerprint(), List.copyOf(normalized)));
    if ("VERIFIED".equals(row.get("status"))) {
      if (!encoded.equals(row.get("verification_json"))) throw conflict("Runtime verification receipt conflict");
      return report(snapshotId);
    }
    db.update("update summer_authorization_data_migration set verification_json=?,status='VERIFIED' where snapshot_id=?", encoded, snapshotId);
    String request = "verify:" + snapshotId + ":" + LegacyAuthorizationReader.digest(encoded).substring(0, 16);
    audit.mutation(input.domain().key(), snapshotId, "AUTHORIZATION_DATA_VERIFIED", request, actor);
    events.enqueue(input.domain(), "DATA_MIGRATION", snapshotId, "AUTHORIZATION_DATA_VERIFIED", 1, request);
    long revision = db.queryForObject("select revision from summer_authorization_domain where domain_key=?", Long.class, input.domain().key());
    db.update("update summer_authorization_migration_snapshot set expected_revision=? where snapshot_id=?", revision, snapshotId);
    return report(snapshotId);
  }

  private Grant grant(AuthorizationDataMigrationPlan plan, String id) {
    return plan.grants().stream().filter(g -> g.id().equals(id)).findFirst().orElseThrow(() -> conflict("Unknown DATA grant"));
  }
  private AuthorizationDataMigrationPlan.Vector roleVector(AuthorizationDataMigrationPlan plan, String role) {
    return plan.vectors().stream().filter(v -> v.targetRoleId().equals(role)).findFirst().orElseThrow(() -> conflict("Unknown DATA vector"));
  }
  private static List<String> permissions(AuthorizationDataMigrationPlan.Vector vector) {
    var ids = new TreeSet<>(vector.interfaceMenuPermissionIds());
    ids.addAll(vector.dataPermissionIds());
    return List.copyOf(ids);
  }
  private static Map<String, Object> scope(Grant grant) {
    var result = new LinkedHashMap<String, Object>();
    result.put("dataResourceKey", grant.resource()); result.put("action", grant.action()); result.put("scopePreset", grant.preset());
    if (grant.preset().equals("SPECIFIED_DEPARTMENTS")) result.put("departmentIds", grant.departmentIds());
    return result;
  }

  private void createPermission(Domain domain, AuthorizationDataMigrationPlan plan, String id) {
    var grant = grant(plan, id);
    if (db.queryForObject("select count(*) from summer_permission_identity where domain_key=? and permission_id=?", Integer.class, domain.key(), id) != 0)
      throw conflict("Uncheckpointed DATA identity collision");
    String group = AuthorizationMigrationPlanner.stableId("group", domain.key());
    db.update("insert into summer_permission_group values (?,?,null,'Migrated permissions',0,1) on duplicate key update group_id=group_id", domain.key(), group);
    db.update("insert into summer_permission_identity values (?,?,?,'DATA')", domain.key(), id, id);
    db.update("insert into summer_permission values (?,?,?,?,?,'DATA','AUTHORIZED',?,'ACTIVE',1)", domain.key(), id, id,
        "Migrated " + grant.resource() + " " + grant.action() + " " + grant.preset(), "Frozen DATA proof " + plan.snapshotId(), group);
    details.replace(domain, id, details.validate(scope(grant)));
    mapping(plan.snapshotId(), "DATA_POLICY", grant.resource() + ":" + grant.action() + ":" + grant.preset(), "PERMISSION", id);
  }

  private void createRole(Domain domain, AuthorizationDataMigrationPlan plan, String id) {
    var vector = roleVector(plan, id);
    if (db.queryForObject("select count(*) from summer_authorization_role where role_id=?", Integer.class, id) != 0)
      throw conflict("Uncheckpointed DATA role collision");
    db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes,bundle_id) values (?,?,?, ?,false,'ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),'','',null)",
        id, domain.id(), "Migrated DATA " + id, domain.kind() + ":" + id);
    db.update("insert into summer_permission_role values (?,?)", domain.key(), id);
    for (String permission : permissions(vector))
      db.update("insert into summer_role_permission values (?,?,?)", domain.key(), id, permission);
    mapping(plan.snapshotId(), "DATA_VECTOR", vector.vectorHash(), "ROLE", id);
  }

  private void replaceUser(Domain domain, AuthorizationDataMigrationPlan plan, String subject) {
    var vector = plan.vectors().stream().filter(v -> v.subject().equals(subject)).findFirst().orElseThrow(() -> conflict("Unknown DATA subject"));
    if (db.update("delete from summer_permission_user_role where domain_key=? and username=? and role_id=?", domain.key(), subject, vector.originalRoleId()) != 1)
      throw conflict("Original staged assignment changed");
    db.update("insert into summer_permission_user_role values (?,?,?)", domain.key(), subject, vector.targetRoleId());
    if (db.update("update summer_permission_user set version=version+1 where domain_key=? and username=?", domain.key(), subject) != 1)
      throw conflict("Original staged user missing");
    mapping(plan.snapshotId(), "USER_DATA", subject, "ROLE", vector.targetRoleId());
    for (String oldRole : vector.sourceRoleIds()) mapping(plan.snapshotId(), "ROLE_DATA", oldRole, "ROLE", vector.targetRoleId());
  }

  private void mapping(String snapshot, String sourceKind, String sourceId, String targetKind, String targetId) {
    if (db.queryForObject("select count(*) from summer_authorization_migration_mapping where snapshot_id=? and source_kind=? and source_id=? and target_kind=? and target_id=?", Integer.class,
        snapshot, sourceKind, sourceId, targetKind, targetId) != 0) return;
    int ordinal = db.queryForObject("select coalesce(max(ordinal),-1)+1 from summer_authorization_migration_mapping where snapshot_id=?", Integer.class, snapshot);
    db.update("insert into summer_authorization_migration_mapping values (?,?,?,?,?,?)", snapshot, ordinal, sourceKind, sourceId, targetKind, targetId);
  }

  private void validateTargets(Domain domain, AuthorizationDataMigrationPlan plan) {
    var applied = db.queryForList("select kind,target_id from summer_authorization_data_migration_step where snapshot_id=? and applied=true", plan.snapshotId());
    var appliedUsers = new HashSet<String>();
    for (var step : applied) {
      String id = step.get("target_id").toString();
      switch (step.get("kind").toString()) {
        case "DATA_PERMISSION" -> {
          var permission = db.queryForList("select code,type,access_mode,status from summer_permission where domain_key=? and permission_id=?", domain.key(), id);
          if (permission.size() != 1 || !permission.getFirst().equals(Map.of("code", id, "type", "DATA", "access_mode", "AUTHORIZED", "status", "ACTIVE"))
              || !scope(grant(plan, id)).equals(details.get(domain, id))) throw conflict("Applied DATA permission drift");
        }
        case "DATA_ROLE" -> {
          var vector = roleVector(plan, id);
          var role = db.queryForList("select enterprise_id,status from summer_authorization_role where role_id=?", id);
          if (role.size() != 1 || !domain.id().equals(role.getFirst().get("enterprise_id")) || !"ACTIVE".equals(role.getFirst().get("status"))
              || db.queryForObject("select count(*) from summer_permission_role where domain_key=? and role_id=?", Integer.class, domain.key(), id) != 1
              || !permissions(vector).equals(db.queryForList("select permission_id from summer_role_permission where domain_key=? and role_id=? order by permission_id", String.class, domain.key(), id)))
            throw conflict("Applied DATA vector role drift");
        }
        case "DATA_USER" -> appliedUsers.add(id);
        default -> throw conflict("Unknown DATA checkpoint kind");
      }
    }
    for (var vector : plan.vectors()) {
      String expected = appliedUsers.contains(vector.subject()) ? vector.targetRoleId() : vector.originalRoleId();
      if (!List.of(expected).equals(db.queryForList("select role_id from summer_permission_user_role where domain_key=? and username=? order by role_id", String.class, domain.key(), vector.subject())))
        throw conflict("Staged subject assignment drift");
    }
  }

  /** History remains readable after legacy tables are contracted. No live legacy lookup here. */
  @Transactional(readOnly = true)
  public Map<String, Object> report(String snapshotId) {
    var input = identities.readFrozenInput(snapshotId);
    var row = proofRow(snapshotId);
    var plan = checkedPlan(input, row);
    var result = new LinkedHashMap<String, Object>();
    result.put("snapshotId", snapshotId);
    result.put("domainKey", input.domain().key());
    result.put("sourceFingerprint", input.sourceFingerprint());
    result.put("expectedRevision", input.expectedRevision());
    result.put("planHash", row.get("plan_hash"));
    result.put("status", row.get("status"));
    result.put("plan", plan);
    result.put("steps", db.queryForList("select step_no,kind,target_id,applied,applied_revision from summer_authorization_data_migration_step where snapshot_id=? order by step_no", snapshotId));
    result.put("dataConversionAccepted", "VERIFIED".equals(row.get("status")) && plan.blockers().isEmpty());
    result.put("cutoverAuthorized", false);
    result.put("verification", row.get("verification_json") == null ? null : json.readTree(row.get("verification_json").toString()));
    return Collections.unmodifiableMap(result);
  }

  private DataInput lockedInput(String id, String fingerprint, long expectedRevision) {
    if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}") || expectedRevision < 0)
      throw invalid("Exact frozen source fingerprint and nonnegative checkpoint revision required");
    var first = identities.readFrozenInput(id);
    Domain domain = first.domain();
    resolvers.forEach(r -> r.prepare(domain));
    var state = db.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=? for update", domain.key());
    if (state.size() != 1 || !"LEGACY".equals(state.getFirst().get("engine_mode"))
        || !(state.getFirst().get("revision") instanceof Number revision) || revision.longValue() < 0)
      throw conflict("DATA continuation requires an explicit existing LEGACY domain");
    var rows = db.queryForList("select snapshot_id from summer_authorization_migration_snapshot where snapshot_id=? and domain_key=? for update", id, domain.key());
    if (rows.size() != 1) throw conflict("Snapshot/domain identity mismatch");
    var input = identities.readFrozenInput(id);
    if (!input.domain().equals(domain) || !input.sourceFingerprint().equals(fingerprint)) throw conflict("Frozen source fingerprint mismatch");
    if (input.expectedRevision() != expectedRevision || revision.longValue() != expectedRevision)
      throw conflict("DATA expectedRevision changed; reread coordinated checkpoint");
    if (!domain.kind().equals("ENTERPRISE")) throw conflict("Enterprise DATA source required");
    if (!json.readTree(input.evidenceJson()).path("legacyFactsFingerprint").asString().equals(legacy.currentLegacyFactsFingerprint(domain)))
      throw conflict("Frozen persistent legacy facts changed");
    for (var permission : input.plan().permissions())
      resolvers.stream().filter(r -> r.type().equals(permission.type())).findFirst()
          .orElseThrow(() -> conflict("Trusted resource resolver unavailable"))
          .resolve(domain, permission.resourceId());
    return input;
  }

  private Map<String, Object> proofRow(String id) {
    var rows = db.queryForList("select * from summer_authorization_data_migration where snapshot_id=?", id);
    if (rows.size() != 1) throw SummerException.withMessage(PermissionErrorCode.NOT_FOUND, "DATA proof has not been prepared");
    return rows.getFirst();
  }

  private AuthorizationDataMigrationPlan checkedPlan(DataInput input, Map<String, Object> row) {
    String encoded = row.get("derived_plan_json").toString();
    if (!LegacyAuthorizationReader.digest(encoded).equals(row.get("plan_hash"))
        || !input.sourceFingerprint().equals(row.get("source_fingerprint"))) throw conflict("Persisted DATA proof hash mismatch");
    var plan = json.readValue(encoded, AuthorizationDataMigrationPlan.class);
    if (!plan.snapshotId().equals(input.snapshotId()) || !plan.domainKey().equals(input.domain().key())
        || !plan.sourceFingerprint().equals(input.sourceFingerprint())
        || !plan.proofVersion().equals(AuthorizationDataMigrationPlanner.VERSION)
        || !plan.proofVersion().equals(row.get("proof_version"))
        || !plan.registryFingerprint().equals(row.get("registry_fingerprint"))) throw conflict("Persisted DATA proof identity mismatch");
    return plan;
  }

  private void addStep(String id, int step, String kind, String target) {
    db.update("insert into summer_authorization_data_migration_step(snapshot_id,step_no,kind,target_id) values (?,?,?,?)", id, step, kind, target);
  }
  private static void operator(String actor) {
    if (actor == null || actor.isBlank() || actor.length() > 128) throw invalid("Explicit operator required");
  }
  private static SummerException invalid(String message) { return SummerException.withMessage(PermissionErrorCode.INVALID, message); }
  private static SummerException conflict(String message) { return SummerException.withMessage(PermissionErrorCode.CONFLICT, message); }
  }
