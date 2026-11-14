package com.abluepoint.summer.sample.authorization.migration;

import static org.assertj.core.api.Assertions.assertThat;

import acceptance.perm12.MigrationArtifactHarness;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@EnabledIfEnvironmentVariable(named = "SUMMER_PERM13_MYSQL", matches = "true")
class AuthorizationDataMigrationMySqlTest extends MigrationArtifactHarness {

  @Test
  void stagesExactDataPermissionsAndRollsBackFactsAuditOutboxAndCheckpointTogether() {
    var domain = Domain.enterprise("perm13-data-source");
    seedSource(domain, "data");
    for (String unit : List.of("perm13-A", "perm13-B"))
      db.update("insert into summer_organization_unit(unit_id,enterprise_id,unit_type,name,code,sort_order,status,created_at,updated_at) values (?,?,'DEPARTMENT',?,?,0,'ACTIVE',current_timestamp,current_timestamp)", unit, domain.id(), unit, unit);
    String tenant = json.writeValueAsString(List.of(Map.of("field", "TENANT")));
    String both = json.writeValueAsString(List.of(Map.of("field", "DEPARTMENT", "operator", "IN", "value", List.of("perm13-A", "perm13-B"))));
    String deny = json.writeValueAsString(List.of(Map.of("field", "DEPARTMENT", "value", "perm13-B")));
    db.update("insert into summer_data_scope_policy(policy_id,enterprise_id,resource,operator_name,rules_json,version,updated_at,conditions_json,checksum) values ('perm13-upper',?,'dictionaries','AND',?,1,current_timestamp,'null','frozen')", domain.id(), tenant);
    db.update("insert into summer_data_scope_binding values ('perm13-role-scope',?,'dictionaries','ROLE','perm13-data-role','AND',?,'null','frozen',1,current_timestamp)", domain.id(), both);
    db.update("insert into summer_data_scope_binding values ('perm13-user-deny',?,'dictionaries','USER','perm13-data-alice','AND',?,'null','frozen',1,current_timestamp)", domain.id(), deny);
    var captured = captureFromBridge(domain, RuntimeExport.empty());
    String id = captured.get("snapshotId").toString(), fingerprint = captured.get("sourceFingerprint").toString();
    identities.stageFrozen(id, fingerprint, 10000, "operator");
    var frozen = identities.readFrozenInput(id);
    var prepared = data.prepare(id, fingerprint, frozen.expectedRevision(), "operator");
    assertThat(prepared.get("status")).isEqualTo("PLANNED");
    var plan = data.dryRun(id);
    assertThat(plan.grants()).hasSize(4).allSatisfy(g -> {
      assertThat(g.preset()).isEqualTo("SPECIFIED_DEPARTMENTS");
      assertThat(g.departmentIds()).containsExactly("perm13-A");
    });
    long before = frozen.expectedRevision();
    int auditBefore = count("summer_authorization_audit", domain.key());
    int outboxBefore = count("summer_authorization_change_outbox", domain.key());
    var rollback = new TransactionTemplate(transactions);
    rollback.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    rollback.executeWithoutResult(tx -> {
      data.stage(id, fingerprint, prepared.get("planHash").toString(), before, 2, "operator");
      tx.setRollbackOnly();
    });
    assertThat(identities.readFrozenInput(id).expectedRevision()).isEqualTo(before);
    assertThat(count("summer_authorization_audit", domain.key())).isEqualTo(auditBefore);
    assertThat(count("summer_authorization_change_outbox", domain.key())).isEqualTo(outboxBefore);
    assertThat(db.queryForObject("select count(*) from summer_permission where domain_key=? and type='DATA'", Integer.class, domain.key())).isZero();
    assertThat(db.queryForObject("select count(*) from summer_authorization_data_migration_step where snapshot_id=? and applied=true", Integer.class, id)).isZero();
    var completed = data.stage(id, fingerprint, prepared.get("planHash").toString(), before, 10000, "operator");
    assertThat(completed.get("status")).isEqualTo("APPLIED");
    assertThat(completed.get("dataConversionAccepted")).isEqualTo(false);
    assertThat(db.queryForList("select department_id from summer_permission_selected_department where domain_key=?", String.class, domain.key()))
        .containsOnly("perm13-A").hasSize(4);
    long revision = ((Number) completed.get("expectedRevision")).longValue();
    identities.stageFrozen(id, fingerprint, 10000, "operator");
    assertThat(identities.readFrozenInput(id).expectedRevision()).isEqualTo(revision);
    assertThat(identities.readFrozenInput(id).source()).isEqualTo(frozen.source());
    assertThatThrownBy(() -> data.stage(id, fingerprint, "0".repeat(64), revision, 10000, "operator")).hasMessageContaining("planHash");
    assertThatThrownBy(() -> data.stage(id, fingerprint, prepared.get("planHash").toString(), revision - 1, 10000, "operator")).hasMessageContaining("expectedRevision");
    db.update("update summer_data_scope_policy set checksum='drift' where policy_id='perm13-upper'");
    assertThatThrownBy(() -> data.stage(id, fingerprint, prepared.get("planHash").toString(), revision, 10000, "operator")).hasMessageContaining("legacy facts changed");
  }

  @Test
  void concurrentSameCheckpointHasOneAtomicWinnerAndOneStaleRevisionConflict() throws Exception {
    var domain = Domain.enterprise("perm13-concurrent-source");
    seedSource(domain, "concurrent");
    var captured = captureFromBridge(domain, RuntimeExport.empty());
    String id = captured.get("snapshotId").toString(), fingerprint = captured.get("sourceFingerprint").toString();
    identities.stageFrozen(id, fingerprint, 10000, "operator");
    var frozen = identities.readFrozenInput(id);
    var prepared = data.prepare(id, fingerprint, frozen.expectedRevision(), "operator");
    int auditBefore = count("summer_authorization_audit", domain.key());
    int outboxBefore = count("summer_authorization_change_outbox", domain.key());
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<String> attempt = () -> {
        start.await();
        try {
          return data.stage(id, fingerprint, prepared.get("planHash").toString(), frozen.expectedRevision(), 10000, "operator").get("status").toString();
        } catch (com.abluepoint.summer.common.error.SummerException stale) {
          assertThat(stale).hasMessageContaining("expectedRevision");
          return "CONFLICT";
        }
      };
      var first = workers.submit(attempt);
      var second = workers.submit(attempt);
      start.countDown();
      assertThat(List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS), second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("APPLIED", "CONFLICT");
    }
    int steps = db.queryForObject("select count(*) from summer_authorization_data_migration_step where snapshot_id=? and applied=true", Integer.class, id);
    assertThat(steps).isEqualTo(2);
    assertThat(count("summer_authorization_audit", domain.key())).isEqualTo(auditBefore + steps);
    assertThat(count("summer_authorization_change_outbox", domain.key())).isEqualTo(outboxBefore + steps);
    assertThat(identities.readFrozenInput(id).expectedRevision()).isEqualTo(frozen.expectedRevision() + steps);
    var sourceUser = identities.readFrozenInput(id).source().users().getFirst();
    assertThat(db.queryForObject("select count(*) from summer_permission_user_role where domain_key=? and username=?", Integer.class, domain.key(), sourceUser.subject())).isEqualTo(1);
  }

  private int count(String table, String domainKey) {
    if (!List.of("summer_authorization_audit", "summer_authorization_change_outbox").contains(table))
      throw new IllegalArgumentException("Known evidence table required");
    return db.queryForObject("select count(*) from " + table + " where enterprise_id=?", Integer.class, domainKey);
  }

  private void seedSource(Domain domain, String suffix) {
    db.update("insert into summer_enterprise(enterprise_id,name,code,status,created_at,updated_at,review_status,version) values (?,?,?,'ACTIVE',current_timestamp,current_timestamp,'APPROVED',1)", domain.id(), domain.id(), domain.id());
    db.update("insert into summer_auth_user(username,password_hash,role,active) values (?,'fixture-not-used-for-login','USER',true)", "perm13-" + suffix + "-alice");
    db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values (?,?,?,?,'','ACTIVE',1,current_timestamp,current_timestamp)", domain.id(), "perm13-" + suffix + "-member", "perm13-" + suffix + "-alice", "Fixture member");
    db.update("insert into summer_authorization_domain values (?,'LEGACY',0)", domain.key());
    db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes) values (?,?,'Old role',?,false,'ACTIVE',1,current_timestamp,current_timestamp,'','')", "perm13-" + suffix + "-role", domain.id(), "perm13-" + suffix + "-role");
    db.update("insert into summer_authorization_user_binding values (?,?,?,?,'[]',1,current_timestamp,current_timestamp)", "perm13-" + suffix + "-binding", domain.id(), "perm13-" + suffix + "-alice", "[\"perm13-" + suffix + "-role\"]");
  }

  @Test
  void resumesDataVectorReplacementAndReplaysWithoutAdvancingAuthority() {
    var domain = Domain.enterprise("perm13-resume-source");
    db.update("insert into summer_authorization_domain values (?,'LEGACY',0)", domain.key());
    db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes) values ('perm13-old-role',?,'Old role','perm13-old-role',false,'ACTIVE',1,current_timestamp,current_timestamp,'','')", domain.id());
    db.update("insert into summer_authorization_user_binding values ('perm13-binding',?,'perm13-alice','[\"perm13-old-role\"]','[]',1,current_timestamp,current_timestamp)", domain.id());
    var captured = captureFromBridge(domain, RuntimeExport.empty());
    String id = captured.get("snapshotId").toString();
    String fingerprint = captured.get("sourceFingerprint").toString();
    identities.stageFrozen(id, fingerprint, 10000, "perm13-operator");
    var frozen = identities.readFrozenInput(id);
    var prepared = data.prepare(id, fingerprint, frozen.expectedRevision(), "perm13-operator");
    String hash = prepared.get("planHash").toString();
    var first = data.stage(id, fingerprint, hash, frozen.expectedRevision(), 1, "perm13-operator");
    assertThat(first.get("status")).isEqualTo("PLANNED");
    var finished = data.stage(id, fingerprint, hash, ((Number) first.get("expectedRevision")).longValue(), 10000, "perm13-operator");
    assertThat(finished.get("status")).isEqualTo("APPLIED");
    var revision = finished.get("expectedRevision");
    var audits = db.queryForObject("select count(*) from summer_authorization_audit where enterprise_id=? and action like 'AUTHORIZATION_DATA_%'", Integer.class, domain.key());
    var replay = data.stage(id, fingerprint, hash, ((Number) revision).longValue(), 10000, "perm13-operator");
    assertThat(replay.get("expectedRevision")).isEqualTo(revision);
    assertThat(db.queryForObject("select count(*) from summer_authorization_audit where enterprise_id=? and action like 'AUTHORIZATION_DATA_%'", Integer.class, domain.key())).isEqualTo(audits);
    assertThat(db.queryForObject("select role_ids from summer_authorization_user_binding where binding_id='perm13-binding'", String.class)).isEqualTo("[\"perm13-old-role\"]");
    assertThat(identities.readFrozenInput(id).source()).isEqualTo(frozen.source());
    // Synthetic input tests the operator evidence contract only, not an HTTP acceptance batch.
    var comparisons = data.dryRun(id).items().stream().filter(i -> i.status().equals("PROVED"))
        .flatMap(i -> (i.action().equals("read") ? List.of("list", "total", "detail") : List.of("write")).stream()
            .map(operation -> new AuthorizationDataMigrationService.Comparison(i.subjectKind(), i.subject(), i.resource(), i.action(),
                operation, List.of(), List.of(), 403, 404))).toList();
    var evidence = new AuthorizationDataMigrationService.Verification("summer-data-http-comparison/1", fingerprint,
        hash, "1".repeat(64), comparisons);
    var verified = data.verify(id, fingerprint, hash, ((Number) revision).longValue(), evidence, "perm13-operator");
    assertThat(verified.get("dataConversionAccepted")).isEqualTo(true);
    assertThat(verified.get("cutoverAuthorized")).isEqualTo(false);
  }

  @Test
  void preservesImmutableSourceAndUsesSeparateForeignKeyCheckpoint() {
    var domain = Domain.enterprise("perm13-empty-source");
    db.update("insert into summer_authorization_domain values (?,'LEGACY',0)", domain.key());
    var captured = captureFromBridge(domain, RuntimeExport.empty());
    String id = captured.get("snapshotId").toString();
    var frozen = identities.readFrozenInput(id);
    var before = db.queryForMap("select snapshot_json,evidence_json,plan_json from summer_authorization_migration_snapshot where snapshot_id=?", id);
    var prepared = data.prepare(id, frozen.sourceFingerprint(), frozen.expectedRevision(), "perm13-operator");
    assertThat(prepared.get("status")).isEqualTo("PLANNED");
    assertThat(db.queryForMap("select snapshot_json,evidence_json,plan_json from summer_authorization_migration_snapshot where snapshot_id=?", id)).isEqualTo(before);
    assertThat(db.queryForObject("select count(*) from summer_authorization_migration_step where snapshot_id=? and kind not in ('PERMISSION','ROLE','USER')", Integer.class, id)).isZero();
    assertThat(db.queryForObject("select count(*) from summer_authorization_data_migration where snapshot_id=?", Integer.class, id)).isEqualTo(1);
  }
}
