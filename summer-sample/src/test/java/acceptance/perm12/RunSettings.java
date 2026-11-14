package acceptance.perm12;

import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Prepared only. No defaults that silently create a database, grant or LEGACY domain. */
final class RunSettings {
  static final ObjectMapper JSON = new ObjectMapper();
  static Map<String, Object> properties() {
    if (!"canonical-12/1".equals(required("PERM12_HARNESS_RUNNER")))
      throw new IllegalArgumentException("Run the original three classes with scripts/acceptance/permission-migration/run_native_protocol.py");
    required("PERM12_HARNESS_RESOURCE_OWNER");
    String raw = required("SPRING_APPLICATION_JSON");
    @SuppressWarnings("unchecked")
    Map<String, Object> p = JSON.readValue(raw, Map.class);
    String lease = required("PERM12_HARNESS_LEASE");
    if (!lease.matches("[a-zA-Z0-9_-]{8,80}")) throw new IllegalArgumentException("Invalid lease ID");
    String jdbc = Objects.toString(p.get("spring.datasource.url"), "");
    if (!jdbc.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/summer_perm12_harness_[a-zA-Z0-9_]+(?:\\?.*)?"))
      throw new IllegalArgumentException("An explicitly leased local MySQL harness schema is required");
    String schema = required("PERM12_HARNESS_SCHEMA");
    String account = required("PERM12_HARNESS_ACCOUNT");
    String caseId = required("PERM12_HARNESS_CASE_ID");
    if (!schema.matches("summer_perm12_harness_[0-9a-f]{10}") || !schema.equals(account)
        || !account.equals(p.get("spring.datasource.username"))
        || !jdbc.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/" + schema + "(?:\\?.*)?")
        || !caseId.matches("com\\.abluepoint\\.summer\\.sample\\.authorization\\.migration\\.Authorization(?:DataMigration|Migration|MigrationRuntime)MySqlTest#[a-zA-Z0-9_]+"))
      throw new IllegalArgumentException("Worker must use its exact leased method/schema/account");
    for (String key : List.of("spring.datasource.username", "spring.datasource.password",
        "spring.data.redis.password", "summer.sample.security.master-key"))
      if (Objects.toString(p.get(key), "").isBlank()) throw new IllegalArgumentException("Missing property: " + key);
    if (!"127.0.0.1".equals(p.get("spring.data.redis.host"))
        || !"1".equals(Objects.toString(p.get("spring.data.redis.database"))))
      throw new IllegalArgumentException("Owned Redis DB1 is required");
    String prefix = required("PERM12_HARNESS_CASE_PREFIX");
    if (!prefix.matches("summer:perm12-harness:" + lease + ":case-(?:0[1-9]|1[0-2]):"))
      throw new IllegalArgumentException("Exact case Redis DB1 prefix is required");
    for (String key : List.of("spring.session.data.redis.namespace", "summer.sample.session-index.namespace"))
      if (!Objects.toString(p.get(key), "").startsWith(prefix))
        throw new IllegalArgumentException("Missing owned Redis namespace: " + key);
    if (!Boolean.FALSE.equals(p.get("summer.sample.authorization.legacy-scope-compatibility")))
      throw new IllegalArgumentException("Explicit legacy-scope-compatibility=false is required");
    if (!"".equals(p.get("summer.sample.authorization.managed-bootstrap-domains")))
      throw new IllegalArgumentException("Explicit empty managed bootstrap domains are required");
    if (!Boolean.TRUE.equals(p.get("spring.flyway.enabled")))
      throw new IllegalArgumentException("Historical MySQL Flyway migrations must be enabled");
    for (String key : List.of("summer.sample.authorization-events.scheduling-enabled",
        "summer.sample.parameter-events.scheduling-enabled", "spring.quartz.auto-startup"))
      if (!Boolean.FALSE.equals(p.get(key))) throw new IllegalArgumentException("Disable scheduling explicitly: " + key);
    if (!"0".equals(Objects.toString(p.get("server.port"))) || !"127.0.0.1".equals(p.get("server.address")))
      throw new IllegalArgumentException("Loopback ephemeral server port is required");
    Path work = Path.of(required("PERM12_HARNESS_RUN_DIR")).toAbsolutePath().normalize();
    if (!Files.isDirectory(work)) throw new IllegalArgumentException("An existing owner run directory is required");
    return p;
  }
  static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment key: " + name);
    return value;
  }
  static Path work() { return Path.of(required("PERM12_HARNESS_RUN_DIR")).toAbsolutePath().normalize(); }
  static String redact(String value) {
    String s = value;
    try {
      for (var e : properties().entrySet())
        if (e.getKey().matches("(?i).*(password|secret|master-key|token).*")) {
          String v = Objects.toString(e.getValue(), "");
          if (!v.isEmpty()) s = s.replace(v, "[redacted]");
        }
    } catch (RuntimeException ignored) { return "Diagnostic suppressed: invalid harness configuration"; }
    return s.replaceAll("(?i)([?&](?:code|state|access_token|refresh_token|id_token|client_secret)=)[^\\s&]+", "$1[redacted]");
  }
  private RunSettings() {}
}
