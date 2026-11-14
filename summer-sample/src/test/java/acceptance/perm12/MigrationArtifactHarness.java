package acceptance.perm12;

import com.abluepoint.summer.sample.SummerSampleApplication;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService;
import com.abluepoint.summer.sample.authorization.application.permission.TrustedPermissionResourceResolver;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Protocol fixture only; the existing approved operator/fence/cutover lane is separate. */
public abstract class MigrationArtifactHarness {
  protected AuthorizationMigrationService migration, identities;
  protected AuthorizationDataMigrationService data;
  protected LegacyAuthorizationReader reader;
  protected JdbcTemplate db;
  protected ObjectMapper json;
  protected PlatformTransactionManager transactions;
  private ConfigurableApplicationContext target;
  private Integer targetPort;
  private final String contextId = UUID.randomUUID().toString();
  private boolean captured;
  private final Map<String, String> fingerprints = new HashMap<>();
  protected boolean fullRuntime() { return false; }

  @BeforeEach
  protected final void initOldSchema(TestInfo testInfo) throws Exception {
    var p = RunSettings.properties();
    String method = testInfo.getTestMethod().orElseThrow().getName();
    if (CURRENT_ONLY.contains(method)) { startTarget(); return; }
    // The resource owner provisions the fresh schema and namespaces outside this harness.
    oldPhase("init", null);
    var ds = new DriverManagerDataSource();
    ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
    ds.setUrl(p.get("spring.datasource.url").toString());
    ds.setUsername(p.get("spring.datasource.username").toString());
    ds.setPassword(p.get("spring.datasource.password").toString());
    db = new JdbcTemplate(ds);
    json = RunSettings.JSON;
  }

  @AfterEach
  protected final void closeTarget() {
    if (target != null) {
      target.close();
      recordTargetContext(true);
      target = null;
    }
  }

  /** The only capture seam: old child exits before target application starts. */
  @SuppressWarnings("unchecked")
  protected final Map<String, Object> captureFromBridge(Domain domain, RuntimeExport originalEmptyExport) {
    if (captured || target != null || !domain.kind().equals("ENTERPRISE"))
      throw new IllegalStateException("One enterprise bridge capture before target startup per case");
    Map<String, Object> capture = oldPhase("capture", domain.id());
    captured = true;
    String id = Objects.requireNonNull(capture.get("snapshotId"), "Old snapshot ID").toString();
    String fingerprint = Objects.requireNonNull(capture.get("sourceFingerprint"), "Old fingerprint").toString();
    fingerprints.put(id, fingerprint);
    var original = db.queryForMap(
        "select snapshot_json,evidence_json,plan_json from summer_authorization_migration_snapshot where snapshot_id=?", id);
    startTarget();
    var after = db.queryForMap(
        "select snapshot_json,evidence_json,plan_json from summer_authorization_migration_snapshot where snapshot_id=?", id);
    if (!original.equals(after)) throw new AssertionError("Target startup changed original persisted frozen input");
    var frozen = migration.readFrozenInput(id); // real target parser and supported-recipe guard
    if (!fingerprint.equals(frozen.sourceFingerprint())) throw new AssertionError("Target fingerprint mismatch");
    // Guard tests must not accidentally pass because an unrelated old resource was already retired.
    var resolvers = target.getBeansOfType(TrustedPermissionResourceResolver.class).values();
    resolvers.forEach(r -> r.prepare(domain));
    var failures = new ArrayList<Map<String, String>>();
    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
      db.queryForMap("select domain_key from summer_authorization_domain where domain_key=? for update", domain.key());
      for (var permission : frozen.plan().permissions()) {
        var resolver = resolvers.stream().filter(r -> r.type().equals(permission.type())).findFirst().orElse(null);
        try {
          if (resolver == null) throw new IllegalStateException("Missing target resolver");
          resolver.resolve(domain, permission.resourceId());
        } catch (RuntimeException failure) {
          failures.add(Map.of("type", permission.type(), "resourceId", permission.resourceId(),
              "exceptionClass", failure.getClass().getName()));
        }
      }
      tx.setRollbackOnly();
    });
    var binding = new LinkedHashMap<String, Object>();
    binding.put("snapshotId", id);
    binding.put("sourceFingerprint", fingerprint);
    binding.put("oldArtifactSha256", RunSettings.required("PERM12_OLD_ARTIFACT_SHA"));
    binding.put("targetArtifactSha256", RunSettings.required("PERM12_TARGET_ARTIFACT_SHA"));
    binding.put("targetWorkerPid", ProcessHandle.current().pid());
    binding.put("targetBoundPort", targetPort);
    binding.put("resourceOwner", RunSettings.required("PERM12_HARNESS_RESOURCE_OWNER"));
    binding.put("leaseId", RunSettings.required("PERM12_HARNESS_LEASE"));
    binding.put("originalFrozenColumnsUnchanged", true);
    binding.put("permissionCount", frozen.plan().permissions().size());
    binding.put("unresolvedTargetIncarnations", failures);
    try {
      json.writeValue(RunSettings.work().resolve("binding-" + UUID.randomUUID() + ".json").toFile(), binding);
    } catch (Exception failure) { throw new IllegalStateException("Cannot persist target binding evidence", failure); }
    if (!failures.isEmpty()) throw new AssertionError(
        "Cross-artifact fixture blocked: target cannot resolve original frozen permission incarnations; see binding evidence");
    return capture;
  }

  /** Explicit cross-artifact continuation; ordinary target stage would recapture retired codes. */
  protected final Map<String, Object> continueFrozen(String id, int limit, String actor) {
    String fingerprint = fingerprints.get(id);
    if (fingerprint == null) throw new IllegalStateException("No original old-artifact fingerprint");
    return migration.stageFrozen(id, fingerprint, limit, actor);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> oldPhase(String action, String domainId) {
    Path output = RunSettings.work().resolve("old-" + action + "-" + UUID.randomUUID() + ".json");
    var command = new ArrayList<>(List.of(RunSettings.required("PERM12_JAVA"), "-Xmx512m", "-cp",
        RunSettings.required("PERM12_OLD_CLASSPATH"), "acceptance.perm12.OldBridgeCaptureMain",
        action, output.toString(), Boolean.toString(fullRuntime())));
    if (domainId != null) command.add(domainId);
    Process child = null;
    java.time.Instant childStarted = null;
    RuntimeException primaryFailure = null;
    try {
      child = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD).start();
      childStarted = child.info().startInstant().orElseThrow();
      jsonForProcessRecord(action, child.pid(), false, null, childStarted, command);
      if (!child.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS))
        throw new IllegalStateException("Old artifact phase timed out");
      int exit = child.exitValue();
      jsonForProcessRecord(action, child.pid(), true, exit, childStarted, command);
      if (exit != 0) throw new IllegalStateException("Old artifact phase failed; inspect sanitized old phase report");
      Map<String, Object> report = RunSettings.JSON.readerFor(Map.class)
          .with(tools.jackson.databind.DeserializationFeature.USE_LONG_FOR_INTS).readValue(output.toFile());
      if (!"old-phase-complete".equals(report.get("status")) || !action.equals(report.get("phase"))
          || !Boolean.TRUE.equals(report.get("contextClosed"))
          || !RunSettings.required("PERM12_OLD_ARTIFACT_SHA").equals(report.get("producerArtifactSha256"))
          || !(report.get("pid") instanceof Number producerPid) || producerPid.longValue() != child.pid()
          || !(report.get("boundPort") instanceof Number producerPort) || producerPort.intValue() < 1 || producerPort.intValue() > 65535
          || !RunSettings.required("PERM12_HARNESS_LEASE").equals(report.get("leaseId"))
          || !RunSettings.required("PERM12_HARNESS_RESOURCE_OWNER").equals(report.get("resourceOwner")))
        throw new IllegalStateException("Old artifact phase report does not prove successful capture/init and close");
      return report;
    } catch (Exception failure) {
      primaryFailure = new IllegalStateException("Old artifact phase did not complete", failure);
      throw primaryFailure;
    } finally {
      if (child != null && child.isAlive()) {
        try {
          if (ownedChildAlive(child, childStarted, command)) child.destroy();
          if (!child.waitFor(10, TimeUnit.SECONDS)) {
            if (ownedChildAlive(child, childStarted, command)) child.destroyForcibly();
            if (!child.waitFor(10, TimeUnit.SECONDS)) throw new IllegalStateException("Owned old JVM remains alive");
          }
          jsonForProcessRecord(action, child.pid(), true, child.exitValue(), childStarted, command);
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          var releaseFailure = new IllegalStateException("Old child release interrupted", failure);
          if (primaryFailure != null) primaryFailure.addSuppressed(releaseFailure); else throw releaseFailure;
        } catch (RuntimeException releaseFailure) {
          if (primaryFailure != null) primaryFailure.addSuppressed(releaseFailure); else throw releaseFailure;
        }
      }
    }
  }

  private boolean ownedChildAlive(Process child, java.time.Instant started, List<String> command) {
    if (!child.isAlive()) return false;
    var info = child.info();
    if (started == null || !info.startInstant().orElseThrow().equals(started)
        || !Arrays.equals(info.arguments().orElseThrow(), command.subList(1, command.size()).toArray(String[]::new)))
      throw new IllegalStateException("Old child identity changed; unknown process preserved without signal");
    return true;
  }

  private void jsonForProcessRecord(String action, long pid, boolean stopped, Integer exit,
      java.time.Instant started, List<String> command) {
    var row = new LinkedHashMap<String, Object>();
    row.put("action", action); row.put("pid", pid); row.put("stopped", stopped); row.put("exitCode", exit);
    row.put("parentPid", ProcessHandle.current().pid());
    if (started != null) row.put("startedEpochSecond", started.getEpochSecond());
    row.put("argv", command);
    try {
      RunSettings.JSON.writeValue(RunSettings.work().resolve("old-process-" + pid + ".json").toFile(), row);
    } catch (Exception failure) { throw new IllegalStateException("Cannot persist owned process evidence", failure); }
  }

  private static final Set<String> CURRENT_ONLY = Set.of(
      "neverRecreatesLegacyWhenModeStateIsMissingAndManagedFactsSurvive",
      "inventoriesPlatformSourcesWithoutRelabelingAnEnterpriseNamedPlatform",
      "persistsAnIdempotentDryRunWithoutChangingEngineMode");

  private void startTarget() {
    var builder = new SpringApplicationBuilder(SummerSampleApplication.class);
    if (!fullRuntime()) builder.sources(MvcOnlyFixture.class);
    target = builder.run();
    targetPort = Integer.parseInt(target.getEnvironment().getRequiredProperty("local.server.port"));
    recordTargetContext(false);
    migration = identities = target.getBean(AuthorizationMigrationService.class);
    data = target.getBean(AuthorizationDataMigrationService.class);
    reader = target.getBean(LegacyAuthorizationReader.class);
    db = target.getBean(JdbcTemplate.class);
    json = target.getBean(ObjectMapper.class);
    transactions = target.getBean(PlatformTransactionManager.class);
  }

  private void recordTargetContext(boolean closed) {
    try {
      RunSettings.JSON.writeValue(RunSettings.work().resolve("target-context-" + contextId + ".json").toFile(), Map.of(
          "workerPid", ProcessHandle.current().pid(), "boundPort", targetPort,
          "springContextClosed", closed, "workerProcessStopped", false,
          "resourceOwner", RunSettings.required("PERM12_HARNESS_RESOURCE_OWNER"), "leaseId", RunSettings.required("PERM12_HARNESS_LEASE")));
    } catch (Exception failure) { throw new IllegalStateException("Cannot persist target context evidence", failure); }
  }
}
