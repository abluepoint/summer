package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Local operator command only; normal startup has no migration or enrollment side effects. */
@Component
@ConditionalOnProperty(name = "summer.sample.authorization.data-migration.command")
public class AuthorizationDataMigrationCommand implements ApplicationRunner {
  private static final String PREFIX = "summer.sample.authorization.data-migration.";
  private final AuthorizationDataMigrationService migration;
  private final ObjectMapper json;
  private final Environment environment;
  private final ControlledWriterFence fence;
  private final ControlledAuthorizationCutover cutover;

  public AuthorizationDataMigrationCommand(AuthorizationDataMigrationService migration,
      ObjectMapper json, Environment environment, ControlledWriterFence fence,
      ControlledAuthorizationCutover cutover) {
    this.migration = migration; this.json = json; this.environment = environment;
    this.fence = fence; this.cutover = cutover;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    var output = Path.of(environment.getRequiredProperty(PREFIX + "report-file")).toAbsolutePath();
    if (Files.exists(output)) throw new IllegalArgumentException("DATA report file must be new");
    String id = environment.getRequiredProperty(PREFIX + "snapshot-id");
    String command = environment.getRequiredProperty(PREFIX + "command");
    if (java.util.Set.of("prepare", "stage", "verify").contains(command)) {
      if (!cutover.actualArtifactHash().equals(required("target-artifact-sha256")))
        throw new IllegalArgumentException("Exact executing target artifact approval required for DATA mutation");
      fence.requireSnapshot(id);
    }
    Map<String, Object> report = switch (command) {
      case "dry-run" -> Map.of("plan", migration.dryRun(id));
      case "report" -> migration.report(id);
      case "prepare" -> migration.prepare(id, required("source-fingerprint"), revision(), required("actor"));
      case "stage" -> migration.stage(id, required("source-fingerprint"), required("plan-hash"),
          revision(), environment.getProperty(PREFIX + "batch-size", Integer.class, 100), required("actor"));
      case "verify" -> migration.verify(id, required("source-fingerprint"), required("plan-hash"), revision(),
          json.readValue(Files.readString(Path.of(required("runtime-evidence-file"))), AuthorizationDataMigrationService.Verification.class), required("actor"));
      default -> throw new IllegalArgumentException("Unknown DATA migration command");
    };
    Files.writeString(output, json.writeValueAsString(report), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
  }
  private String required(String key) { return environment.getRequiredProperty(PREFIX + key); }
  private long revision() { return environment.getRequiredProperty(PREFIX + "expected-revision", Long.class); }
}
