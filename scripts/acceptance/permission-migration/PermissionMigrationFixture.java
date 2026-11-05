package acceptance;

import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.infrastructure.migration.LegacyAuthorizationReader.RuntimeExport;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** Acceptance-only source, compiled solely by the task's explicit runtime init script. */
@Configuration(proxyBeanMethods = false)
public class PermissionMigrationFixture {

  /** Controlled MVC/menu ledger fixture: the realtime subsystem is not installed at all. */
  @Bean
  static org.springframework.beans.factory.config.BeanFactoryPostProcessor optionalMvcOnlyFixture(
      Environment env) {
    return factory -> {
      if (!env.getProperty("summer.perm12.fixture.exclude-realtime", Boolean.class, false)) return;
      var registry = (org.springframework.beans.factory.support.BeanDefinitionRegistry) factory;
      var removed = new HashSet<String>();
      boolean changed;
      do {
        changed = false;
        for (String name : registry.getBeanDefinitionNames()) {
          var definition = registry.getBeanDefinition(name);
          String owner = definition.getBeanClassName();
          if ((owner != null && owner.startsWith("com.abluepoint.summer.sample.realtime."))
              || removed.contains(definition.getFactoryBeanName())) {
            registry.removeBeanDefinition(name);
            removed.add(name);
            changed = true;
          }
        }
      } while (changed);
    };
  }

  @RestController
  @PreAuthorize("isAuthenticated()")
  public static class Probe {
    @ResourcePermission("migration.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/migration-probe/read")
    public Map<String, Object> read() {
      return Map.of("operation", "read");
    }

    @ResourcePermission("migration.alias")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/migration-probe/alias")
    public Map<String, Object> alias() {
      return Map.of("operation", "alias");
    }

    @ResourcePermission("migration.write")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/migration-probe/write")
    public Map<String, Object> write() {
      return Map.of("operation", "write");
    }
  }

  @Bean
  ApplicationRunner migrationFixture(
      JdbcTemplate db,
      PasswordEncoder encoder,
      Environment env,
      AuthorizationMigrationService migration,
      ObjectMapper json,
      PlatformTransactionManager transactions,
      @org.springframework.beans.factory.annotation.Qualifier("seedConfiguredClients")
          ApplicationRunner seedClients,
      org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
          clients) {
    return arguments -> {
      seedClients.run(arguments);
      Path directory = Path.of(env.getRequiredProperty("summer.perm12.fixture-directory"));
      Files.createDirectories(directory);
      var client = clients.findByClientId("summer-ui");
      clients.save(
          org.springframework.security.oauth2.server.authorization.client.RegisteredClient.from(
                  client)
              .scopes(
                  scopes ->
                      scopes.addAll(
                          List.of(
                              "enterprise.capabilities.read",
                              "enterprise.capabilities.write",
                              "enterprise.bundles.read",
                              "enterprise.bundles.write",
                              "enterprise.menu.write",
                              "platform.menu.write")))
              .build());
      db.execute(
          "create table if not exists summer_perm12_fixture_control (control_id int primary key)");
      if (db.queryForObject("select count(*) from summer_perm12_fixture_control", Integer.class)
          == 0) {
        String admin =
            env.getRequiredProperty("summer.security.authorization-server.user.username");
        String password = env.getRequiredProperty("summer.perm12.reader-password");
        db.update(
            "insert into"
                + " summer_enterprise(enterprise_id,name,code,status,created_at,updated_at,review_status,version)"
                + " values ('perm12','Migration"
                + " acceptance','perm12','ACTIVE',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),'APPROVED',1)");
        for (String user :
            List.of(admin, "perm12-alice", "perm12-bob", "perm12-carol", "perm12-inactive")) {
          if (!user.equals(admin))
            db.update(
                "insert into summer_auth_user(username,password_hash,role,active) values"
                    + " (?,?,'USER',true)",
                user,
                encoder.encode(password));
          db.update(
              "insert into"
                  + " summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at)"
                  + " values ('perm12',?,?,?,'',?,1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
              "member-" + user,
              user,
              user,
              user.equals("perm12-inactive") ? "INACTIVE" : "ACTIVE");
        }
        var ids = new ArrayList<String>();
        for (String code :
            List.of(
                "enterprise.roles.read",
                "enterprise.roles.write",
                "enterprise.capabilities.read",
                "enterprise.capabilities.write",
                "enterprise.bundles.read",
                "enterprise.bundles.write",
                "enterprise.menu.write")) {
          String id = "fixture-" + code;
          ids.add(id);
          db.update(
              "insert into summer_authorization_capability values"
                  + " (?,'perm12','ENTERPRISE',?,?,'read','LOW','ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
              id,
              code,
              code);
        }
        for (String code :
            List.of(
                "platform.capabilities.read",
                "platform.capabilities.write",
                "platform.menu.write")) {
          String id = "fixture-" + code;
          ids.add(id);
          db.update(
              "insert into summer_authorization_capability values"
                  + " (?,null,'PLATFORM',?,?,'read','LOW','ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
              id,
              code,
              code);
        }
        db.update(
            "insert into"
                + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes)"
                + " values ('fixture-admin','perm12','Fixture upgrade"
                + " operator','fixture-admin',false,'ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),?,'')",
            String.join("\n", ids));
        db.update(
            "insert into summer_authorization_user_binding values"
                + " ('fixture-admin-binding','perm12',?,?,"
                + " '[]',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
            admin,
            json.writeValueAsString(List.of("fixture-admin")));
        db.update(
            "insert into"
                + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at,capability_ids,capability_codes)"
                + " values ('fixture-platform','ent-acme','Fixture old platform"
                + " bridge','fixture-platform',false,'ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),?,'')",
            String.join(
                "\n", ids.stream().filter(id -> id.startsWith("fixture-platform.")).toList()));
        db.update(
            "insert into"
                + " summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at)"
                + " values"
                + " ('ent-acme',?,?,?,'','ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)) on"
                + " duplicate key update subject=subject",
            "member-" + admin,
            admin,
            admin);
        db.update(
            "insert into summer_authorization_user_binding values"
                + " ('fixture-platform-binding','ent-acme',?,?,"
                + " '[]',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
            admin,
            json.writeValueAsString(List.of("fixture-platform")));
        // This schema is newly provisioned and the fixture marker is absent: explicit
        // first enrollment, never recovery of missing production mode state.
        db.update(
            "insert into summer_authorization_domain values ('ENTERPRISE:perm12','LEGACY',0)");
        db.update("insert into summer_perm12_fixture_control values (1)");
      }
      var lostDomain = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
      Thread.ofPlatform()
          .daemon()
          .name("permission-migration-acceptance-commands")
          .start(
              () -> {
                String previous = "";
                while (!Thread.currentThread().isInterrupted()) {
                  try {
                    Path input = directory.resolve("command.json");
                    if (Files.exists(input)) {
                      var command = json.readTree(Files.readString(input));
                      String id = command.path("id").asString();
                      if (!id.equals(previous)) {
                        previous = id;
                        Map<String, Object> result;
                        try {
                          String action = command.path("action").asString();
                          String snapshot = command.path("snapshotId").asString("");
                          switch (action) {
                            case "capture" ->
                                result =
                                    migration.capture(
                                        Domain.enterprise("perm12"),
                                        json.readValue(
                                            Files.readString(
                                                Path.of(
                                                    command.path("runtimeExportFile").asString())),
                                            RuntimeExport.class));
                            case "report" -> result = migration.report(snapshot);
                            case "stage", "stage-frozen" -> {
                              int limit = command.path("limit").asInt(10000);
                              java.util.function.Supplier<Map<String, Object>> stageCall =
                                  () ->
                                      action.equals("stage-frozen")
                                          ? migration.stageFrozen(
                                              snapshot,
                                              command.path("sourceFingerprint").asString(),
                                              limit,
                                              "acceptance-upgrader")
                                          : migration.stage(snapshot, limit, "acceptance-upgrader");
                              if (command.path("hold").asBoolean(false)) {
                                new TransactionTemplate(transactions)
                                    .execute(
                                        status -> {
                                          stageCall.get();
                                          try {
                                            Files.writeString(
                                                directory.resolve(id + "-inside-transaction.json"),
                                                "{\"ready\":true}");
                                            new CountDownLatch(1).await(90, TimeUnit.SECONDS);
                                          } catch (Exception failure) {
                                            throw new IllegalStateException(failure);
                                          }
                                          status.setRollbackOnly();
                                          return null;
                                        });
                                result = Map.of("heldTransactionRolledBack", true);
                              } else result = stageCall.get();
                            }
                            case "test-lose-domain" -> {
                              var rows =
                                  db.queryForList(
                                      "select engine_mode,revision from summer_authorization_domain"
                                          + " where domain_key='ENTERPRISE:perm12'");
                              if (rows.size() != 1
                                  || !"MANAGED".equals(rows.getFirst().get("engine_mode"))
                                  || !lostDomain.compareAndSet(null, Map.copyOf(rows.getFirst())))
                                throw new IllegalStateException(
                                    "Owned MANAGED fixture mode required");
                              db.update(
                                  "delete from summer_authorization_domain where"
                                      + " domain_key='ENTERPRISE:perm12'");
                              result = Map.of("ownedModeTemporarilyMissing", true);
                            }
                            case "test-restore-domain" -> {
                              var saved = lostDomain.get();
                              if (saved == null
                                  || db.queryForObject(
                                          "select count(*) from summer_authorization_domain where"
                                              + " domain_key='ENTERPRISE:perm12'",
                                          Integer.class)
                                      != 0)
                                throw new IllegalStateException(
                                    "Exact owned missing fixture state required");
                              db.update(
                                  "insert into summer_authorization_domain values"
                                      + " ('ENTERPRISE:perm12',?,?)",
                                  saved.get("engine_mode"),
                                  saved.get("revision"));
                              lostDomain.set(null);
                              result = Map.of("originalOwnedModeRestored", true);
                            }
                            case "test-mode" -> {
                              // This temporary acceptance domain is never a production cutover.
                              db.update(
                                  "update summer_authorization_domain set"
                                      + " engine_mode='MANAGED',revision=revision+1 where"
                                      + " domain_key='ENTERPRISE:perm12'");
                              result = Map.of("isolatedAcceptanceMode", "MANAGED");
                            }
                            default -> throw new IllegalArgumentException("Unknown fixture action");
                          }
                        } catch (Exception failure) {
                          result =
                              Map.of(
                                  "errorType",
                                  failure.getClass().getSimpleName(),
                                  "message",
                                  Objects.toString(failure.getMessage(), ""));
                        }
                        Files.writeString(
                            directory.resolve(id + "-result.json"),
                            json.writeValueAsString(result));
                      }
                    }
                    Thread.sleep(100);
                  } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                  } catch (Exception ignored) {
                    break;
                  }
                }
              });
      Files.writeString(directory.resolve("fixture-ready.json"), "{\"ready\":true}");
    };
  }
}
