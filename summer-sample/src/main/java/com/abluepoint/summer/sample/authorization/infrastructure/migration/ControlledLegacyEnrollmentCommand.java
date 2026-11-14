package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Disabled unless an operator explicitly selects inspect/enroll and new output paths. */
@Component
@ConditionalOnProperty("summer.sample.authorization.upgrade.command")
public class ControlledLegacyEnrollmentCommand implements ApplicationRunner {
  private static final String PREFIX = "summer.sample.authorization.upgrade.";
  private final ControlledLegacyEnrollment enrollment;
  private final ObjectMapper json;
  private final Environment environment;
  private final ControlledWriterFence fence;
  private final ControlledAuthorizationCutover cutover;
  private final com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService migration;

  public ControlledLegacyEnrollmentCommand(ControlledLegacyEnrollment enrollment,
      ObjectMapper json, Environment environment, ControlledWriterFence fence,
      com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService migration,
      ControlledAuthorizationCutover cutover) {
    this.enrollment = enrollment;
    this.json = json;
    this.environment = environment;
    this.fence = fence;
    this.migration = migration;
    this.cutover = cutover;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    Path output = Path.of(environment.getRequiredProperty(PREFIX + "report-file")).toAbsolutePath();
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalArgumentException("New upgrade report path required");
    Path artifact = Path.of(environment.getRequiredProperty(PREFIX + "bridge-artifact"));
    Map<String, Object> report;
    switch (environment.getRequiredProperty(PREFIX + "command")) {
      case "inspect" -> report = enrollment.inspect(artifact);
      case "enroll" -> {
        Path approval = Path.of(environment.getRequiredProperty(PREFIX + "approval-file"));
        if (!Files.isRegularFile(approval, LinkOption.NOFOLLOW_LINKS) || Files.size(approval) > 262144)
          throw new IllegalArgumentException("Protected bridge approval file required");
        var permissions = Files.getPosixFilePermissions(approval);
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)
            || permissions.contains(PosixFilePermission.OTHERS_WRITE))
          throw new IllegalArgumentException("Bridge approval must not be writable by other users");
        byte[] bytes = Files.readAllBytes(approval);
        String hash = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!hash.equals(environment.getRequiredProperty(PREFIX + "approval-sha256")))
          throw new IllegalArgumentException("Pinned bridge approval fingerprint required");
        report = enrollment.enroll(artifact,
            json.readValue(bytes, ControlledLegacyEnrollment.Approval.class), hash);
      }
      case "fence" -> {
        byte[] bytes=protectedApproval();
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        report=fence.fence(json.readValue(bytes,ControlledWriterFence.Approval.class),hash,
            Path.of(environment.getRequiredProperty(PREFIX+"execution-receipt-file")));
      }
      case "verify-fence" -> report=Map.of("fence",fence.requireSnapshot(
          environment.getRequiredProperty(PREFIX+"snapshot-id")),"cutoverAuthorized",false);
      case "inspect-cutover" -> report=cutover.inspect(environment.getRequiredProperty(PREFIX+"snapshot-id"));
      case "cutover-approved" -> {
        byte[] bytes=protectedApproval();
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var approval=json.readValue(bytes,ControlledAuthorizationCutover.Approval.class);
        report=cutover.execute(approval,hash,
            Files.readAllBytes(Path.of(environment.getRequiredProperty(PREFIX+"restore-receipt-file"))),
            approval.purpose().equals("PRODUCTION_CUTOVER")?Files.readAllBytes(Path.of(
                environment.getRequiredProperty(PREFIX+"comparison-inputs-file"))):null);
      }
      case "stage-approved" -> {
        byte[] bytes=protectedApproval();
        var approved=json.readTree(bytes);
        if(!approved.path("format").asString().equals("summer-managed-upgrade-stage/1")
            || !approved.path("purpose").asString().equals("STAGE_FROZEN_ENTERPRISE"))
          throw new IllegalArgumentException("Explicit frozen staging approval required");
        String snapshot=approved.path("snapshotId").asString();
        String fingerprint=approved.path("sourceFingerprint").asString();
        String actor=approved.path("actor").asString();
        if(!cutover.actualArtifactHash().equals(approved.path("targetArtifactSha256").asString()))
          throw new IllegalArgumentException("Exact executing target artifact approval required before staging");
        fence.requireSnapshot(snapshot);
        if(!migration.readFrozenInput(snapshot).sourceFingerprint().equals(fingerprint))
          throw new IllegalArgumentException("Staging approval fingerprint mismatch");
        var resolution=migration.resolveRuntime(snapshot,actor);
        int batches=environment.getProperty(PREFIX+"maximum-batches",Integer.class,1);
        int size=environment.getProperty(PREFIX+"batch-size",Integer.class,100);
        if(batches<1||batches>10000)throw new IllegalArgumentException("Bounded staging batches required");
        Map<String,Object> checkpoint=Map.of();
        for(int batch=0;batch<batches;batch++) {
          fence.requireSnapshot(snapshot);
          checkpoint=migration.stageFrozen(snapshot,fingerprint,size,actor);
          if(java.util.Set.of("STAGED","DATA_PENDING").contains(checkpoint.get("status")))break;
        }
        report=Map.of("runtimeResolution",resolution,"checkpoint",checkpoint,"cutoverAuthorized",false);
      }
      default -> throw new IllegalArgumentException("Unknown controlled upgrade command");
    }
    Files.writeString(output, json.writeValueAsString(report), StandardOpenOption.CREATE_NEW);
  }

  private byte[] protectedApproval() throws Exception {
    Path approval=Path.of(environment.getRequiredProperty(PREFIX+"approval-file"));
    if(!Files.isRegularFile(approval,LinkOption.NOFOLLOW_LINKS)||Files.size(approval)>262144)
      throw new IllegalArgumentException("Protected upgrade approval file required");
    var permissions=Files.getPosixFilePermissions(approval);
    if(permissions.contains(PosixFilePermission.GROUP_WRITE)||permissions.contains(PosixFilePermission.OTHERS_WRITE))
      throw new IllegalArgumentException("Upgrade approval must not be writable by other users");
    byte[] bytes=Files.readAllBytes(approval);
    String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    if(!hash.equals(environment.getRequiredProperty(PREFIX+"approval-sha256")))
      throw new IllegalArgumentException("Pinned upgrade approval fingerprint required");
    return bytes;
  }
}
