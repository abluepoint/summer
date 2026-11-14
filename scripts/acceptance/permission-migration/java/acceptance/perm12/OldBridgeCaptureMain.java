package acceptance.perm12;

import com.abluepoint.summer.sample.SummerSampleApplication;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Launched in a separate JVM with the complete pinned OLD artifact, never target classes. */
public final class OldBridgeCaptureMain {
  private static final String CONTEXT_ID = UUID.randomUUID().toString();
  public static void main(String[] args) throws Exception {
    Path output = Path.of(args[1]).toAbsolutePath().normalize();
    if (!output.startsWith(RunSettings.work()) || Files.exists(output))
      throw new IllegalArgumentException("Fresh owned output required");
    int exit = 1;
    Integer port = null;
    boolean closed = false;
    try {
      RunSettings.properties();
      boolean runtime = Boolean.parseBoolean(args[2]);
      var builder = new SpringApplicationBuilder(SummerSampleApplication.class);
      if (!runtime) builder.sources(MvcOnlyFixture.class);
      Map<String, Object> result = new LinkedHashMap<>();
      try (var context = builder.run()) {
        port = Integer.parseInt(context.getEnvironment().getRequiredProperty("local.server.port"));
        recordContext(port, false);
        if (args[0].equals("capture")) {
          var report = context.getBean(AuthorizationMigrationService.class)
              .capture(Domain.enterprise(args[3]), RuntimeExport.empty());
          for (String key : List.of("snapshotId", "sourceFingerprint", "blockers", "coverage", "resourceCoverage"))
            result.put(key, report.get(key));
        } else if (!args[0].equals("init")) throw new IllegalArgumentException("Unknown old phase");
        result.put("producerArtifactSha256", RunSettings.required("PERM12_OLD_ARTIFACT_SHA"));
        result.put("pid", ProcessHandle.current().pid());
        result.put("boundPort", port);
        result.put("bindAddress", "127.0.0.1");
        result.put("resourceOwner", RunSettings.required("PERM12_HARNESS_RESOURCE_OWNER"));
        result.put("leaseId", RunSettings.required("PERM12_HARNESS_LEASE"));
      }
      closed = true;
      recordContext(port, true);
      result.put("contextClosed", true);
      result.put("phase", args[0]);
      result.put("status", "old-phase-complete");
      RunSettings.JSON.writeValue(output.toFile(), result);
      exit = 0; // Success is assigned only after close and both evidence writes succeed.
    } catch (Throwable failure) {
      var detail = new java.io.StringWriter();
      failure.printStackTrace(new java.io.PrintWriter(detail));
      RunSettings.JSON.writeValue(output.toFile(), Map.of(
          "status", "old-phase-failed", "exceptionClass", failure.getClass().getName(),
          "sanitizedFailure", RunSettings.redact(detail.toString()), "pid", ProcessHandle.current().pid(),
          "resourceOwner", RunSettings.required("PERM12_HARNESS_RESOURCE_OWNER"), "contextClosed", closed));
    }
    System.exit(exit);
  }

  private static void recordContext(int port, boolean closed) {
    RunSettings.JSON.writeValue(RunSettings.work().resolve("old-context-" + ProcessHandle.current().pid() + "-" + CONTEXT_ID + ".json").toFile(), Map.of(
        "pid", ProcessHandle.current().pid(), "boundPort", port, "springContextClosed", closed,
        "processStopped", false, "resourceOwner", RunSettings.required("PERM12_HARNESS_RESOURCE_OWNER"),
        "leaseId", RunSettings.required("PERM12_HARNESS_LEASE")));
  }
}
