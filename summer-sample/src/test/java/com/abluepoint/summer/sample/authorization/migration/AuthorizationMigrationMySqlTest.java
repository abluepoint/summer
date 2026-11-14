package com.abluepoint.summer.sample.authorization.migration;

import static org.assertj.core.api.Assertions.assertThat;

import acceptance.perm12.MigrationArtifactHarness;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in real MySQL ledger protocol. The launcher provisions and owns the database/Redis DB. */
@EnabledIfEnvironmentVariable(named = "SUMMER_PERM12_MYSQL", matches = "true")
class AuthorizationMigrationMySqlTest extends MigrationArtifactHarness {


  private Domain enrolledDomain(String id) {
    var domain = Domain.enterprise(id);
    // Explicit test enrollment of a new empty legacy domain, never capture self-healing.
    db.update("insert into summer_authorization_domain values (?,'LEGACY',0)", domain.key());
    return domain;
  }

  @Test
  void rejectsFrozenHashFormatPersistentFactAndTargetIncarnationDrift() {
    var domain = enrolledDomain("migration-frozen-guard-test");
    var capture = captureFromBridge(domain, RuntimeExport.empty());
    String id = capture.get("snapshotId").toString();
    String fingerprint = capture.get("sourceFingerprint").toString();
    var frozen = migration.readFrozenInput(id);
    assertThat(frozen.sourceFingerprint()).isEqualTo(fingerprint);
    assertThatThrownBy(() -> migration.stageFrozen(id, "0".repeat(64), 1, "migration-operator"))
        .hasMessageContaining("fingerprint mismatch");
    String evidence = frozen.evidenceJson();
    String unsupported = evidence.replace("summer-legacy-source/1", "summer-legacy-source/999");
    db.update(
        "update summer_authorization_migration_snapshot set evidence_json=?,source_fingerprint=?"
            + " where snapshot_id=?",
        unsupported,
        com.abluepoint.summer.sample.authorization.infrastructure.migration
            .LegacyAuthorizationReader.digest(unsupported),
        id);
    try {
      assertThatThrownBy(() -> migration.readFrozenInput(id))
          .hasMessageContaining("Unsupported frozen input format");
    } finally {
      db.update(
          "update summer_authorization_migration_snapshot set evidence_json=?,source_fingerprint=?"
              + " where snapshot_id=?",
          evidence,
          fingerprint,
          id);
    }
    db.update(
        "insert into"
            + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes,bundle_id)"
            + " values('migration-frozen-drift',?,'drift','drift',false,'ACTIVE',1,current_timestamp,current_timestamp,'','',null)",
        domain.id());
    try {
      assertThatThrownBy(() -> migration.stageFrozen(id, fingerprint, 1, "migration-operator"))
          .hasMessageContaining("persistent legacy facts changed");
    } finally {
      db.update("delete from summer_authorization_role where role_id='migration-frozen-drift'");
    }
    String resource =
        frozen.plan().permissions().stream()
            .filter(p -> p.type().equals("INTERFACE"))
            .findFirst()
            .orElseThrow()
            .resourceId();
    db.update("update summer_interface_resource set active=false where resource_id=?", resource);
    assertThatThrownBy(() -> migration.stageFrozen(id, fingerprint, 1, "migration-operator"))
        .isInstanceOf(com.abluepoint.summer.common.error.SummerException.class);
    assertThat(
            db.queryForObject(
                "select count(*) from summer_authorization_migration_step where snapshot_id=? and"
                    + " applied=true",
                Integer.class,
                id))
        .isZero();
    assertThat(
            db.queryForObject(
                "select count(*) from summer_permission where domain_key=?",
                Integer.class,
                domain.key()))
        .isZero();
  }

  @Test
  void readsFrozenInputWithoutReopeningRemovedLegacyTables() {
    var domain = enrolledDomain("migration-frozen-read-test");
    var capture = captureFromBridge(domain, RuntimeExport.empty());
    String id = capture.get("snapshotId").toString();
    db.execute("rename table summer_authorization_role to summer_perm12_frozen_role_backup");
    try {
      assertThat(migration.readFrozenInput(id).sourceFingerprint())
          .isEqualTo(capture.get("sourceFingerprint"));
    } finally {
      db.execute("rename table summer_perm12_frozen_role_backup to summer_authorization_role");
    }
  }

  @Test
  void resumesTheSamePersistedFrozenInputWithoutRecapturingItsLegacyCodes() {
    var domain = enrolledDomain("migration-frozen-resume-test");
    var capture = captureFromBridge(domain, RuntimeExport.empty());
    String id = capture.get("snapshotId").toString();
    continueFrozen(id, 1, "migration-operator");
    var resumed =
        migration.stageFrozen(
            id, capture.get("sourceFingerprint").toString(), 10000, "migration-operator");
    assertThat(resumed.get("status")).isEqualTo("STAGED");
    long revision = ((Number) resumed.get("expectedRevision")).longValue();
    var replay =
        migration.stageFrozen(
            id, capture.get("sourceFingerprint").toString(), 10000, "migration-operator");
    assertThat(replay.get("expectedRevision")).isEqualTo(revision);
    assertThat(
            db.queryForObject(
                "select engine_mode from summer_authorization_domain where domain_key=?",
                String.class,
                domain.key()))
        .isEqualTo("LEGACY");
  }

  @Test
  void neverRecreatesLegacyWhenModeStateIsMissingAndManagedFactsSurvive() {
    var domain = enrolledDomain("migration-lost-state-test");
    var original = migration.capture(domain, RuntimeExport.empty());
    String originalId = original.get("snapshotId").toString();
    String originalEvidence = migration.readDataInput(originalId).evidenceJson();
    db.update(
        "update summer_authorization_domain set engine_mode='MANAGED',revision=revision+1 where"
            + " domain_key=?",
        domain.key());
    db.update("insert into summer_permission_user values (?,?,1)", domain.key(), "surviving-user");
    db.update("delete from summer_authorization_domain where domain_key=?", domain.key());
    try {
      assertThatThrownBy(() -> migration.capture(domain, RuntimeExport.empty()))
          .hasMessageContaining("explicit LEGACY");
      assertThat(migration.readDataInput(originalId).evidenceJson()).isEqualTo(originalEvidence);
      assertThat(
              db.queryForObject(
                  "select count(*) from summer_authorization_migration_snapshot where domain_key=?",
                  Integer.class,
                  domain.key()))
          .isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "select count(*) from summer_authorization_domain where domain_key=?",
                  Integer.class,
                  domain.key()))
          .isZero();
      assertThat(
              db.queryForObject(
                  "select version from summer_permission_user where domain_key=? and username=?",
                  Long.class,
                  domain.key(),
                  "surviving-user"))
          .isEqualTo(1L);
    } finally {
      db.update("delete from summer_permission_user where domain_key=?", domain.key());
      db.update("delete from summer_authorization_domain where domain_key=?", domain.key());
    }
  }

  @Test
  void inventoriesPlatformSourcesWithoutRelabelingAnEnterpriseNamedPlatform() {
    String id = "migration-enterprise-platform-role";
    db.update(
        "insert into"
            + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes,bundle_id)"
            + " values(?,?,?,?,false,'ACTIVE',1,current_timestamp,current_timestamp,'','',null)",
        id,
        "platform",
        "Enterprise platform role",
        "migration-enterprise-platform");
    try {
      var capture = reader.read(Domain.platform(), 0, RuntimeExport.empty());
      assertThat(capture.snapshot().roles()).isEmpty();
      assertThat(capture.snapshot().users()).isEmpty();
      assertThat(capture.snapshot().unresolved())
          .contains("PLATFORM_MEMBERSHIP_LIFECYCLE_REQUIRES_EQUIVALENCE");
      assertThat(capture.evidenceJson()).contains("membershipScopedRoles", id, "enterprise_id");
    } finally {
      db.update("delete from summer_authorization_role where role_id=?", id);
    }
  }

  @Test
  void persistsAnIdempotentDryRunWithoutChangingEngineMode() {
    var domain = enrolledDomain("migration-ledger-test");
    var first = migration.capture(domain, RuntimeExport.empty());
    var second = migration.capture(domain, RuntimeExport.empty());
    assertThat(second.get("snapshotId")).isEqualTo(first.get("snapshotId"));
    var input = migration.readDataInput(first.get("snapshotId").toString());
    assertThat(input.domain()).isEqualTo(domain);
    assertThat(input.sourceFingerprint()).isEqualTo(first.get("sourceFingerprint"));
    assertThat(input.evidenceJson())
        .contains("organizationUnits", "organizationMemberships", "organizationContexts");
    assertThat(
            db.queryForObject(
                "select engine_mode from summer_authorization_domain where domain_key=?",
                String.class,
                domain.key()))
        .isEqualTo("LEGACY");
    assertThat(migration.report(first.get("snapshotId").toString()))
        .containsKeys("coverage", "blockers", "steps");
  }

  @Test
  void resumesStagingFromDurableStepsAndReplaysWithoutMoreAuditOrVersions() {
    var domain = enrolledDomain("migration-resume-test");
    var captured = captureFromBridge(domain, RuntimeExport.empty());
    String id = captured.get("snapshotId").toString();
    assertThat(
            db.queryForObject(
                "select count(*) from summer_interface_resource where active=true and"
                    + " source='RUNTIME'",
                Integer.class))
        .isZero();
    assertThat(captured.get("blockers"))
        .withFailMessage(
            "Unexpected blockers %s; catalog %s",
            captured.get("blockers"),
            db
                .queryForList(
                    "select source,normalized_path,handler_provenance from"
                        + " summer_interface_resource where active=true and configurable=true")
                .stream()
                .filter(row -> row.get("source").toString().equals("MVC") == false)
                .toList())
        .isEqualTo(java.util.List.of());
    continueFrozen(id, 1, "migration-operator");
    var partial = migration.report(id);
    assertThat(((java.util.Map<?, ?>) partial.get("coverage")).get("appliedSteps")).isEqualTo(1L);
    var resumed = continueFrozen(id, 10000, "migration-operator");
    assertThat(resumed.get("status")).isEqualTo("STAGED");
    long revision =
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key=?",
            Long.class,
            domain.key());
    int audits =
        db.queryForObject(
            "select count(*) from summer_authorization_audit where enterprise_id=?",
            Integer.class,
            domain.key());
    continueFrozen(id, 10000, "migration-operator");
    assertThat(
            db.queryForObject(
                "select revision from summer_authorization_domain where domain_key=?",
                Long.class,
                domain.key()))
        .isEqualTo(revision);
    assertThat(
            db.queryForObject(
                "select count(*) from summer_authorization_audit where enterprise_id=?",
                Integer.class,
                domain.key()))
        .isEqualTo(audits);
    assertThat(
            db.queryForObject(
                "select engine_mode from summer_authorization_domain where domain_key=?",
                String.class,
                domain.key()))
        .isEqualTo("LEGACY");
  }
}
