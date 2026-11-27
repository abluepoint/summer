package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Explicit offline enrollment of an approved bridge. Missing mode never implies LEGACY. */
@Service
public class ControlledLegacyEnrollment {
  public record Approval(String format, String purpose, String operationId, String actor,
      String bridgeSha256, String schemaFingerprint, List<Domain> domains) {}

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final ObjectProvider<Flyway> flyway;
  private final AuthorizationAuditStore audit;

  public ControlledLegacyEnrollment(JdbcTemplate db, ObjectMapper json,
      ObjectProvider<Flyway> flyway, AuthorizationAuditStore audit) {
    this.db = db;
    this.json = json;
    this.flyway = flyway;
    this.audit = audit;
  }

  /** Read-only discovery output is reviewed before producing a separately pinned approval. */
  @Transactional(readOnly = true)
  public Map<String, Object> inspect(Path bridge) throws IOException {
    String hash = trustedBridge(bridge);
    validateSchema(hash);
    return Map.of("format", "summer-bridge-inspection/1", "bridgeSha256", hash,
        "schemaFingerprint", schemaFingerprint(), "mutation", false);
  }

  @Transactional
  public Map<String, Object> enroll(Path bridge, Approval approval, String approvalHash) throws IOException {
    validateApproval(approval, approvalHash);
    String actualBridge = trustedBridge(bridge);
    if (!actualBridge.equals(approval.bridgeSha256())) throw conflict("Bridge approval mismatch");
    validateSchema(actualBridge);
    if (!schemaFingerprint().equals(approval.schemaFingerprint()))
      throw conflict("Approved source schema changed");
    var outcomes = new TreeMap<String, String>();
    // Stable lock order for multi-domain enrollment. The approved list is never inferred from rows.
    for (Domain domain : approval.domains().stream().sorted(Comparator.comparing(Domain::key)).toList()) {
      var receipt = db.queryForList("select * from summer_authorization_bridge_enrollment where domain_key=? for update", domain.key());
      if (!receipt.isEmpty()) {
        var prior = receipt.getFirst();
        if (!approvalHash.equals(prior.get("approval_sha256"))
            || !approval.operationId().equals(prior.get("operation_id"))
            || !actualBridge.equals(prior.get("bridge_sha256")))
          throw conflict("Domain has a different bridge enrollment");
        var mode = db.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=? for update", domain.key());
        if (mode.size() != 1 || !Set.of("LEGACY", "MANAGED").contains(mode.getFirst().get("engine_mode"))
            || !(mode.getFirst().get("revision") instanceof Number revision)
            || revision.longValue() < ((Number) prior.get("enrolled_revision")).longValue())
          throw conflict("Enrolled domain requires verified recovery");
        outcomes.put(domain.key(), "ALREADY_ENROLLED");
        continue;
      }
      for (String table : List.of("summer_permission_bootstrap", "summer_permission_identity",
          "summer_permission_group", "summer_permission", "summer_permission_role",
          "summer_permission_user", "summer_permission_user_role", "summer_role_permission", "summer_role_platform_permission",
          "summer_permission_resource", "summer_permission_mutation", "summer_permission_resource_control",
          "summer_authorization_migration_snapshot", "summer_authorization_installation"))
        if (db.queryForObject("select count(*) from " + table + " where domain_key=?", Integer.class, domain.key()) != 0)
          throw conflict("Existing target facts cannot be enrolled as an old bridge");
      if (domain.kind().equals("ENTERPRISE")) {
        if (db.queryForObject("select count(*) from summer_enterprise where enterprise_id=?", Integer.class, domain.id()) != 1
            || db.queryForObject("select count(*) from summer_authorization_role where enterprise_id=?", Integer.class, domain.id()) == 0)
          throw conflict("Approved enterprise must have historical identity and role facts");
      } else if (db.queryForObject("select count(*) from summer_authorization_capability where scope='PLATFORM'", Integer.class) == 0)
        throw conflict("Approved platform must have historical capability facts");
      var state = db.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=? for update", domain.key());
      if (state.isEmpty()) {
        // Only this explicit, artifact/schema/approval-bound operator action can create LEGACY.
        db.update("insert into summer_authorization_domain(domain_key,engine_mode,revision) values (?,'LEGACY',0)", domain.key());
        state = db.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=? for update", domain.key());
      }
      if (state.size() != 1 || !"LEGACY".equals(state.getFirst().get("engine_mode"))
          || !(state.getFirst().get("revision") instanceof Number revision) || revision.longValue() < 0)
        throw conflict("Only an approved old LEGACY domain may be enrolled");
      String request = "enrollment-" + UUID.randomUUID();
      db.update("insert into summer_authorization_bridge_enrollment(domain_key,operation_id,actor,bridge_sha256,schema_fingerprint,approval_sha256,enrolled_revision,request_id) values (?,?,?,?,?,?,?,?)",
          domain.key(), approval.operationId(), approval.actor(), actualBridge,
          approval.schemaFingerprint(), approvalHash, revision.longValue(), request);
      audit.mutation(domain.key(), domain.key(), "AUTHORIZATION_BRIDGE_ENROLLED", request, approval.actor());
      outcomes.put(domain.key(), "ENROLLED");
    }
    return Map.of("format", "summer-bridge-enrollment/1", "domains", outcomes,
        "approvalSha256", approvalHash, "bridgeSha256", actualBridge,
        "schemaFingerprint", approval.schemaFingerprint(), "cutoverAuthorized", false);
  }

  private void validateApproval(Approval approval, String hash) {
    if (approval == null || !"summer-bridge-enrollment/1".equals(approval.format())
        || !"ENROLL_EXPLICIT_LEGACY".equals(approval.purpose())
        || approval.operationId() == null || !approval.operationId().matches("[A-Za-z0-9._:-]{1,128}")
        || approval.actor() == null || approval.actor().isBlank() || approval.actor().length() > 128
        || hash == null || !hash.matches("[0-9a-f]{64}")
        || approval.bridgeSha256() == null || !approval.bridgeSha256().matches("[0-9a-f]{64}")
        || approval.schemaFingerprint() == null || !approval.schemaFingerprint().matches("[0-9a-f]{64}")
        || approval.domains() == null || approval.domains().isEmpty() || approval.domains().size() > 1000
        || approval.domains().stream().anyMatch(Objects::isNull)
        || approval.domains().stream().map(Domain::key).distinct().count() != approval.domains().size())
      throw conflict("Exact controlled bridge approval and complete Domain list required");
  }

  private String trustedBridge(Path artifact) throws IOException {
    if (!Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS))
      throw conflict("Trusted local bridge artifact required");
    MessageDigest digest;
    try { digest = MessageDigest.getInstance("SHA-256"); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw conflict("SHA-256 unavailable"); }
    try (var input = Files.newInputStream(artifact)) {
      byte[] buffer = new byte[1024 * 1024];
      for (int length; (length = input.read(buffer)) != -1;) digest.update(buffer, 0, length);
    }
    String hash = HexFormat.of().formatHex(digest.digest());
    var allowed = json.readTree(new ClassPathResource("authorization/trusted-upgrade-bridges.json").getInputStream());
    for (var entry : allowed.path("bridges")) {
      if (!hash.equals(entry.path("artifactSha256").asString())) continue;
      try (var jar = new JarFile(artifact.toFile())) {
        for (var migration : entry.path("migrations").properties()) {
          var resource = jar.getJarEntry("BOOT-INF/classes/db/migration/mysql/" + migration.getKey());
          if (resource == null) throw conflict("Trusted bridge schema entry missing");
          try (var input = jar.getInputStream(resource)) {
            if (!migration.getValue().asString().equals(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))))
              throw conflict("Trusted bridge schema entry changed");
          }
          try (var input = new ClassPathResource("db/migration/mysql/" + migration.getKey()).getInputStream()) {
            if (!migration.getValue().asString().equals(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))))
              throw conflict("Target modified a trusted bridge schema entry");
          }
        }
      } catch (java.security.NoSuchAlgorithmException impossible) { throw conflict("SHA-256 unavailable"); }
      return hash;
    }
    throw conflict("Unknown bridge build; enrollment is blocked");
  }

  private void validateSchema(String bridgeHash) throws IOException {
    var validator = flyway.getIfAvailable();
    if (validator == null) throw conflict("Validated Flyway source history required");
    validator.validate();
    var approved = new HashSet<String>();
    var bridges = json.readTree(new ClassPathResource("authorization/trusted-upgrade-bridges.json").getInputStream());
    for (var bridge : bridges.path("bridges"))
      if (bridgeHash.equals(bridge.path("artifactSha256").asString()))
        bridge.path("migrations").properties().forEach(entry -> approved.add(entry.getKey()));
    var seen = new HashSet<String>();
    var history = db.queryForList("select version,script,success from flyway_schema_history where version is not null order by installed_rank");
    boolean bridgeHead = false;
    for (var row : history) {
      if (!Boolean.TRUE.equals(row.get("success"))) throw conflict("Failed schema history blocks enrollment");
      var version = MigrationVersion.fromVersion(row.get("version").toString());
      String script = row.get("script").toString();
      if (version.compareTo(MigrationVersion.fromVersion("180")) <= 0) {
        if (!approved.contains(script) || !seen.add(script))
          throw conflict("Source schema history is outside the trusted bridge allowlist");
      } else if (!Set.of("V181__authorization_upgrade_provenance.sql",
          "V182__authorization_runtime_migration_proof.sql", "V183__authorization_writer_fence.sql",
          "V184__authorization_controlled_cutover.sql",
          "V185__authorization_controlled_mode_recovery.sql",
          "V186__authorization_contract_preparation.sql",
          "V187__tenant_roles_use_platform_permissions.sql").contains(script))
        throw conflict("Unapproved schema expansion blocks enrollment");
      if (version.compareTo(MigrationVersion.fromVersion("180")) == 0) bridgeHead = true;
      if (version.compareTo(MigrationVersion.fromVersion("187")) > 0)
        throw conflict("Unsupported source schema version");
    }
    if (!bridgeHead || !seen.equals(approved))
      throw conflict("Complete supported bridge schema history required");
  }

  private String schemaFingerprint() {
    var input = new TreeMap<String, Object>();
    input.put("migrations", db.queryForList("select version,type,script,checksum,success from flyway_schema_history order by installed_rank"));
    input.put("columns", db.queryForList("select TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA from information_schema.columns where table_schema=database() and table_name like 'summer\\_%' order by TABLE_NAME,ORDINAL_POSITION"));
    return LegacyAuthorizationReader.digest(json.writeValueAsString(input));
  }

  private static SummerException conflict(String message) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT, message);
  }
}
