package com.abluepoint.summer.sample.authorization.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.abluepoint.summer.sample.SummerSampleApplication;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Full installed production catalog: unproved historical credential upper bounds must block. */
@EnabledIfEnvironmentVariable(named = "SUMMER_PERM12_MYSQL", matches = "true")
@SpringBootTest(
    classes = SummerSampleApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationMigrationRuntimeMySqlTest {
  @Autowired AuthorizationMigrationService migration;
  @Autowired JdbcTemplate db;

  @Test
  void inventoriesExactInstalledRuntimeAndBlocksRatherThanSynthesizingAGrant() {
    var domain = Domain.enterprise("migration-runtime-block-test");
    db.update("insert into summer_authorization_domain values (?,'LEGACY',0)", domain.key());
    var report = migration.capture(domain, RuntimeExport.empty());
    var blockers = (List<?>) report.get("blockers");
    assertThat(blockers)
        .withFailMessage(
            "runtime blockers %s; rows %s",
            blockers,
            db.queryForList(
                "select"
                    + " application_id,source,http_method,normalized_path,conditions_json,handler_provenance,domain_kind,configurable"
                    + " from summer_interface_resource where active=true and source='RUNTIME'"))
        .hasSize(2);
    assertThat(blockers.stream().map(Object::toString))
        .withFailMessage(
            "runtime rows %s",
            db.queryForList(
                "select"
                    + " application_id,source,http_method,normalized_path,conditions_json,handler_provenance,domain_kind,configurable"
                    + " from summer_interface_resource where active=true and source='RUNTIME'"))
        .allMatch(b -> b.startsWith("RUNTIME_SIGNED_CLAIM_EQUIVALENCE_REQUIRED:"));
    var input = migration.readDataInput(report.get("snapshotId").toString());
    assertThat(input.evidenceJson())
        .contains(
            "runtimeResourceMappings",
            "realtimeAccess#read",
            "realtimeAccess#send",
            "enterprise.messages.read",
            "enterprise.messages.write");
    var runtimeIds =
        db.queryForList(
            "select resource_id from summer_interface_resource where active=true and"
                + " source='RUNTIME'",
            String.class);
    assertThat(runtimeIds).hasSize(2);
    assertThat(input.plan().permissions()).noneMatch(p -> runtimeIds.contains(p.resourceId()));
    assertThat(
            db.queryForObject(
                "select count(*) from summer_permission where domain_key=?",
                Integer.class,
                input.domain().key()))
        .isZero();
    assertThat(((Map<?, ?>) report.get("resourceCoverage")).get("blockedInterfaceSources"))
        .isEqualTo(2L);
    assertThat(
            ((Number) ((Map<?, ?>) report.get("resourceCoverage")).get("interfaceMappingFraction"))
                .doubleValue())
        .isLessThan(1d);
    assertThatThrownBy(() -> migration.stage(input.snapshotId(), 10000, "migration-operator"))
        .hasMessageContaining("unresolved blockers");
    assertThat(
            db.queryForObject(
                "select engine_mode from summer_authorization_domain where domain_key=?",
                String.class,
                input.domain().key()))
        .isEqualTo("LEGACY");
  }
}
