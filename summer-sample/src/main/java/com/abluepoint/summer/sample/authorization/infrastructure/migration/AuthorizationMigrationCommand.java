package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
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

/** Explicit local operator command. Normal application startup never runs migration work. */
@Component
@ConditionalOnProperty(name = "summer.sample.authorization.migration.command")
public class AuthorizationMigrationCommand implements ApplicationRunner {
  private static final String PREFIX = "summer.sample.authorization.migration.";
  private final AuthorizationMigrationService migration;
  private final ObjectMapper json;
  private final Environment environment;

  public AuthorizationMigrationCommand(
      AuthorizationMigrationService migration, ObjectMapper json, Environment environment) {
    this.migration = migration;
    this.json = json;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    Path output = Path.of(environment.getRequiredProperty(PREFIX + "report-file")).toAbsolutePath();
    if (Files.exists(output))
      throw new IllegalArgumentException("Migration report file must be new");
    Map<String, Object> report;
    switch (environment.getRequiredProperty(PREFIX + "command")) {
      case "capture" -> {
        Domain domain =
            new Domain(
                environment.getRequiredProperty(PREFIX + "domain-kind"),
                environment.getRequiredProperty(PREFIX + "domain-id"));
        String exported = environment.getProperty(PREFIX + "runtime-export-file");
        var runtime =
            exported == null
                ? RuntimeExport.empty()
                : json.readValue(Files.readString(Path.of(exported)), RuntimeExport.class);
        report = migration.capture(domain, runtime);
      }
      case "stage" ->
          report =
              migration.stage(
                  environment.getRequiredProperty(PREFIX + "snapshot-id"),
                  environment.getProperty(PREFIX + "batch-size", Integer.class, 100),
                  environment.getRequiredProperty(PREFIX + "actor"));
      case "stage-frozen" ->
          report =
              migration.stageFrozen(
                  environment.getRequiredProperty(PREFIX + "snapshot-id"),
                  environment.getRequiredProperty(PREFIX + "source-fingerprint"),
                  environment.getProperty(PREFIX + "batch-size", Integer.class, 100),
                  environment.getRequiredProperty(PREFIX + "actor"));
      case "frozen-input" ->
          report =
              Map.of(
                  "frozenInput",
                  migration.readFrozenInput(
                      environment.getRequiredProperty(PREFIX + "snapshot-id")));
      case "report" ->
          report = migration.report(environment.getRequiredProperty(PREFIX + "snapshot-id"));
      default -> throw new IllegalArgumentException("Unknown migration command");
    }
    Files.writeString(
        output,
        json.writeValueAsString(report),
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
  }
}
