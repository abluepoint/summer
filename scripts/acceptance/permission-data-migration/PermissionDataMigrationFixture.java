package acceptance;

import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationDataMigrationService;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Acceptance-only source. Never included by the product's source sets or production startup. */
@Configuration(proxyBeanMethods = false)
@Import(PermissionMigrationFixture.class)
public class PermissionDataMigrationFixture {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final Environment environment;
  private final AuthorizationDataMigrationService migration;
  private final PlatformTransactionManager transactions;
  private final org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients;

  public PermissionDataMigrationFixture(JdbcTemplate db, ObjectMapper json, Environment environment,
      AuthorizationDataMigrationService migration, PlatformTransactionManager transactions,
      org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients) {
    this.db = db; this.json = json; this.environment = environment;
    this.migration = migration; this.transactions = transactions; this.clients = clients;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.beans.factory.ObjectProvider<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> mappings;

  /** Read-only installed MVC provenance, independent of lazy persistent interface-directory scans. */
  public static List<Map<String,Object>> installedDataConsumers(
      List<org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping> mappings) {
    var rows = new ArrayList<Map<String,Object>>();
    for (var mapping : mappings) mapping.getHandlerMethods().forEach((info, handler) -> {
      for (String path : info.getPatternValues()) {
        if (!(path.contains("/dictionaries") || path.contains("/system-parameters") || path.contains("/data-records/"))) continue;
        var methods = new TreeSet<String>();
        info.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
        if (methods.isEmpty()) methods.addAll(List.of("GET","HEAD","POST","PUT","PATCH","DELETE","OPTIONS","TRACE"));
        if (methods.contains("GET")) methods.add("HEAD");
        for (String method : methods) {
          var installed = com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources.mvc(method,path,handler,info);
          rows.add(Map.of("source","current-runtime-RequestMappingHandlerMapping","httpMethod",method,
              "normalizedPath",path,"handlerType",handler.getBeanType().getName(),
              "handlerMethod",handler.getMethod().getName(),"domainKind",installed.domainKind(),
              "configurable",installed.configurable()));
        }
      }
    });
    rows.sort(Comparator.comparing(row -> row.get("normalizedPath")+":"+row.get("httpMethod")+":"+row.get("handlerType")));
    return List.copyOf(rows);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ready() throws Exception {
    Path directory = Path.of(environment.getRequiredProperty("summer.perm13.fixture-directory"));
    Files.createDirectories(directory);
    var client = clients.findByClientId("summer-ui");
    clients.save(org.springframework.security.oauth2.server.authorization.client.RegisteredClient.from(client)
        .scopes(scopes -> scopes.addAll(List.of("platform.dictionary.read", "platform.dictionary.write",
            "platform.dictionary.create", "platform.dictionary.update", "platform.dictionary.publish",
            "platform.dictionary.disable", "platform.dictionary.rollback", "system.parameter.read",
            "system.parameter.write", "system.parameter.create", "system.parameter.update",
            "system.parameter.publish", "system.parameter.disable", "system.parameter.rollback",
            "enterprise.organization.read", "enterprise.members.read", "enterprise.data-scope.read", "enterprise.data-scope.write"))).build());
    db.execute("create table if not exists summer_perm13_fixture_control (control_id int primary key)");
    if (db.queryForObject("select count(*) from summer_perm13_fixture_control", Integer.class) == 0) seed();
    Thread.ofPlatform().daemon().name("permission-data-migration-acceptance-commands").start(() -> {
      String previous = "";
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Path input = directory.resolve("data-command.json");
          if (Files.exists(input)) {
            var command = json.readTree(Files.readString(input));
            String id = command.path("id").asString();
            if (!id.equals(previous)) {
              previous = id;
              Object result;
              try { result = execute(command, directory); }
              catch (Exception failure) {
                result = Map.of("errorType", failure.getClass().getSimpleName(),
                    "message", Objects.toString(failure.getMessage(), ""));
              }
              Files.writeString(directory.resolve(id + "-data-result.json"), json.writeValueAsString(result));
            }
          }
          Thread.sleep(100);
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (Exception failure) { break; }
      }
    });
    Files.writeString(directory.resolve("data-fixture-ready.json"), "{\"ready\":true}");
  }

  private Object execute(JsonNode command, Path directory) throws Exception {
    String action = command.path("action").asString(), snapshot = command.path("snapshotId").asString();
    String fingerprint = command.path("sourceFingerprint").asString(), hash = command.path("planHash").asString();
    long revision = command.path("expectedRevision").asLong();
    return switch (action) {
      case "installed-data-consumers" -> installedDataConsumers(mappings.orderedStream().toList());
      case "dry-run" -> migration.dryRun(snapshot);
      case "report" -> migration.report(snapshot);
      case "prepare" -> migration.prepare(snapshot, fingerprint, revision, "acceptance-upgrader");
      case "verify" -> migration.verify(snapshot, fingerprint, hash, revision,
          json.readValue(Files.readString(Path.of(command.path("evidenceFile").asString())),
              AuthorizationDataMigrationService.Verification.class), "acceptance-upgrader");
      case "stage" -> {
        if (command.path("hold").asBoolean(false)) {
          var transaction = new TransactionTemplate(transactions);
          transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
          transaction.executeWithoutResult(status -> {
            migration.stage(snapshot, fingerprint, hash, revision, command.path("limit").asInt(10000), "acceptance-upgrader");
            try {
              Files.writeString(directory.resolve(command.path("id").asString() + "-inside-data-transaction.json"), "{\"ready\":true}");
              new CountDownLatch(1).await(90, TimeUnit.SECONDS);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            status.setRollbackOnly();
          });
          yield Map.of("heldTransactionRolledBack", true);
        }
        yield migration.stage(snapshot, fingerprint, hash, revision, command.path("limit").asInt(10000), "acceptance-upgrader");
      }
      case "test-owner-facts" -> {
        int changed = 0;
        for (var row : command.path("rows")) {
          String key = row.path("key").asString();
          if (!key.startsWith("perm13-")) throw new IllegalArgumentException("Owned fixture business key required");
          String owner = row.path("owner").isNull() ? null : row.path("owner").asString();
          changed += db.update("update summer_dictionary_type set owner_id=? where tenant_id='perm12' and code=?", owner, key);
          changed += db.update("update summer_system_parameter set owner_id=? where tenant_id='perm12' and parameter_key=?", owner, key);
        }
        yield Map.of("ownedOwnerFactsChanged", changed);
      }
      default -> throw new IllegalArgumentException("Unknown DATA fixture command");
    };
  }

  private void seed() {
    String admin = environment.getRequiredProperty("summer.security.authorization-server.user.username");
    // Only first initialization of this newly owned fixture schema; never missing-mode recovery.
    db.update("insert into summer_authorization_domain values ('PLATFORM:platform','LEGACY',0)");
    db.update("insert into summer_authorization_domain values ('ENTERPRISE:ent-acme','LEGACY',0)");
    for (String unit : List.of("perm13-a", "perm13-b", "perm13-c", "perm13-a-child"))
      db.update("insert into summer_organization_unit(unit_id,enterprise_id,parent_id,unit_type,name,code,sort_order,status,created_at,updated_at) values (?,'perm12',?,'DEPARTMENT',?,?,0,'ACTIVE',current_timestamp,current_timestamp)",
          unit, unit.equals("perm13-a-child") ? "perm13-a" : null, unit, unit);
    for (String subject : List.of(admin, "perm12-alice", "perm12-bob", "perm12-carol")) {
      for (String unit : List.of("perm13-a", "perm13-c"))
        db.update("insert into summer_organization_membership(enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at) values ('perm12',?,?,?,current_timestamp,current_timestamp)",
            "member-" + subject, unit, unit.equals("perm13-a"));
      db.update("insert into summer_organization_context(subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,'perm12','perm13-a','fixture-context',current_timestamp,current_timestamp)", subject);
    }
    var capabilities = new ArrayList<String>();
    for (String code : List.of("platform.dictionary.read", "platform.dictionary.create", "platform.dictionary.update",
        "platform.dictionary.publish", "platform.dictionary.disable", "platform.dictionary.rollback",
        "system.parameter.read", "system.parameter.create", "system.parameter.update", "system.parameter.publish",
        "system.parameter.disable", "system.parameter.rollback", "enterprise.organization.read",
        "enterprise.members.read", "enterprise.data-scope.read", "enterprise.data-scope.write")) {
      String id = "perm13-cap-" + code;
      capabilities.add(id);
      db.update("insert into summer_authorization_capability values (?,'perm12','ENTERPRISE',?,?,'read','LOW','ACTIVE',1,current_timestamp,current_timestamp)", id, code, code);
    }
    for (String role : List.of("perm13-dept", "perm13-extra", "perm13-owner", "perm13-no-data"))
      db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes) values (?,'perm12',?,?,false,'ACTIVE',1,current_timestamp,current_timestamp,?,'')", role, role, role, String.join("\n", capabilities));
    String adminIds = db.queryForObject("select capability_ids from summer_authorization_role where role_id='fixture-admin'", String.class);
    db.update("update summer_authorization_role set capability_ids=? where role_id='fixture-admin'", adminIds + "\n" + String.join("\n", capabilities));
    for (var entry : Map.of("perm12-alice", List.of("perm13-dept", "perm13-extra"), "perm12-bob", List.of("perm13-owner"), "perm12-carol", List.of("perm13-no-data")).entrySet())
      db.update("insert into summer_authorization_user_binding values (?,'perm12',?,?,'[]',1,current_timestamp,current_timestamp)",
          "perm13-binding-" + entry.getKey(), entry.getKey(), json.writeValueAsString(entry.getValue()));
    String tenant = json.writeValueAsString(List.of(Map.of("field", "TENANT")));
    String departments = json.writeValueAsString(List.of(Map.of("field", "DEPARTMENT", "operator", "IN", "value", List.of("perm13-a", "perm13-b"))));
    String owner = json.writeValueAsString(List.of(Map.of("field", "OWNER")));
    String extra = json.writeValueAsString(List.of(Map.of("field", "DEPARTMENT", "value", "perm13-c")));
    String denied = json.writeValueAsString(List.of(Map.of("field", "DEPARTMENT", "operator", "IN", "value", List.of("perm13-b", "perm13-c"))));
    for (String resource : List.of("dictionaries", "system-parameters")) {
      db.update("insert into summer_data_scope_policy(policy_id,enterprise_id,resource,operator_name,rules_json,version,updated_at,conditions_json,checksum) values (?,'perm12',?,'AND',?,1,current_timestamp,'null','fixture-frozen')", "perm13-upper-" + resource, resource, tenant);
      for (var entry : Map.of("fixture-admin", tenant, "perm13-dept", departments, "perm13-extra", extra, "perm13-owner", owner).entrySet())
        db.update("insert into summer_data_scope_binding values (?,'perm12',?,'ROLE',?,'AND',?,'null','fixture-frozen',1,current_timestamp)",
            "perm13-scope-" + resource + "-" + entry.getKey(), resource, entry.getKey(), entry.getValue());
      db.update("insert into summer_data_scope_binding values (?,'perm12',?,'USER','perm12-alice','AND',?,'null','fixture-frozen',1,current_timestamp)", "perm13-deny-" + resource, resource, denied);
    }
    db.update("insert into summer_perm13_fixture_control values (1)");
  }
}
