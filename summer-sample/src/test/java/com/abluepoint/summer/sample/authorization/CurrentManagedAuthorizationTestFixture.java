package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceAssociations;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Explicit fresh test facts; H2 schema or an owned, fully migrated native test schema. */
@TestConfiguration(proxyBeanMethods = false)
public class CurrentManagedAuthorizationTestFixture {
  @Autowired JdbcTemplate db;
  @Autowired ManagedAuthorizationBootstrap bootstrap;
  @Autowired ManagedInterfaceResources resources;
  @Autowired PermissionResourceAssociations associations;
  @Autowired org.springframework.beans.factory.ObjectProvider<RequestMappingHandlerMapping> mappings;
  @Autowired org.springframework.beans.factory.ObjectProvider<com.abluepoint.summer.sample.authorization.infrastructure.permission.InterfaceRuntimeAdapter> adapters;
  @Autowired javax.sql.DataSource source;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @Bean
  ApplicationRunner currentManagedSchema(JdbcTemplate db) {
    return args -> initializeSchema(db);
  }

  /** Schema only for explicit trusted fixture calls; installs no domain or grant. */
  public static void initializeSchema(JdbcTemplate db) throws java.io.IOException {
    try (var connection = java.util.Objects.requireNonNull(db.getDataSource()).getConnection()) {
      if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) {
        if (!connection.getMetaData().getURL().startsWith("jdbc:mysql:")
            || !com.abluepoint.summer.sample.testsupport.NativeDatabaseEvidence.claimed(connection.getCatalog()))
          throw new IllegalStateException("Current native fixture requires its explicitly claimed test schema");
        for (String table : List.of("summer_authorization_domain", "summer_authorization_migration_snapshot", "summer_authorization_installation", "summer_permission_bootstrap",
            "summer_permission", "summer_permission_resource", "summer_permission_data_scope",
            "summer_permission_selected_department", "summer_interface_resource", "summer_permission_user_role")) {
          try (var found = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            boolean exact = false;
            while (found.next()) if (table.equalsIgnoreCase(found.getString("TABLE_NAME"))) exact = true;
            if (!exact) throw new IllegalStateException("Native current fixture requires completed production migrations: " + table);
          }
        }
        return;
      }
    } catch (java.sql.SQLException failure) {
      throw new IllegalStateException("Cannot inspect current managed test datasource", failure);
    }
    for (String name : List.of("V60__permission_management.sql",
        "V70__role_permission_and_resource_associations.sql", "V80__permission_resource_controls.sql",
        "V81__managed_interface_lifecycle.sql", "V92__support_full_length_menu_resource_ids.sql",
        "V160__authorization_migration_ledger.sql", "V180__authorization_controlled_installation.sql")) {
      String sql = new String(new ClassPathResource("db/migration/mysql/" + name).getContentAsByteArray(), StandardCharsets.UTF_8);
      if (name.startsWith("V60")) sql = sql.substring(0, sql.indexOf("ALTER TABLE"));
      for (String statement : sql.replaceAll("(?m)--[^\\n]*", "").split(";"))
        if (!statement.isBlank()) {
          if (statement.strip().startsWith("INSERT INTO summer_interface_catalog_control"))
            db.update("insert into summer_interface_catalog_control(control_id,revision) select 1,0 where not exists(select 1 from summer_interface_catalog_control where control_id=1)");
          else db.execute(statement.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
        }
    }
    // Hibernate's test-created legacy table lacks the production V12 unique constraint.
    db.execute("create unique index if not exists fixture_current_role_code_unique on summer_authorization_role(enterprise_id,code)");
    db.update("insert into summer_menu_publication(publication_id,published,manifest_version) values ('global',false,0) on duplicate key update publication_id=publication_id");
    initializeDataSchema(db);
  }

  /** Idempotent schema only: no DATA permission, actor, role or business grant is installed. */
  public static void initializeDataSchema(JdbcTemplate db) throws java.io.IOException {
    for (String name : List.of("V100__permission_data_details.sql", "V110__permission_selected_departments.sql")) {
      String sql = new String(new ClassPathResource("db/migration/mysql/" + name).getContentAsByteArray(), StandardCharsets.UTF_8);
      for (String statement : sql.replaceAll("(?m)--[^\\n]*", "").split(";"))
        if (!statement.isBlank()) db.execute(statement.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS "));
    }
  }

  @org.springframework.core.annotation.Order(1)
  @EventListener(ApplicationReadyEvent.class)
  public void install() throws Exception {
    initializeSchema(db);
    install(Domain.platform());
    install(Domain.enterprise("ent-acme"));
  }

  public void install(Domain domain) throws Exception {
    var modes = db.queryForList("select engine_mode from summer_authorization_domain where domain_key=?", String.class, domain.key());
    if (!modes.isEmpty()) {
      if (modes.size() != 1 || !"MANAGED".equals(modes.getFirst())
          || db.queryForObject("select count(*) from summer_permission_bootstrap where domain_key=?", Integer.class, domain.key()) != 1)
        throw new IllegalStateException("Fixture cannot convert an existing legacy domain");
      return;
    }
    var seed = ManagedAuthorizationBootstrap.class.getDeclaredMethod("seedNewDomain", Domain.class);
    seed.setAccessible(true);
    try { seed.invoke(bootstrap, domain); }
    catch (java.lang.reflect.InvocationTargetException e) { throw new IllegalStateException("Fresh test installation failed", e.getCause()); }
  }

  public void activeUser(Domain domain, String subject) {
    if (db.queryForObject("select count(*) from summer_auth_user where username=?", Integer.class, subject) == 0)
      db.update("insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at) values (?,'!unusable-current-fixture-login','USER',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", subject);
    if (domain.kind().equals("ENTERPRISE") && db.queryForObject("select count(*) from summer_enterprise_member where enterprise_id=? and subject=?", Integer.class, domain.id(), subject) == 0)
      db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values (?,?,?,? ,?,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",domain.id(),UUID.randomUUID().toString(),subject,subject,subject+"@example.test");
  }

  /** Grant only installed handlers carrying one of these exact delegated scopes. */
  public String grant(Domain domain, String subject, String... scopes) {
    activeUser(domain, subject);
    Set<String> accepted = Set.of(scopes);
    return grantHandlers(domain, subject, (handler, path, method) -> {
      var pre = handler.getMethodAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
      return pre != null && accepted.stream().anyMatch(s -> pre.value().contains("'SCOPE_" + s + "'"));
    });
  }

  /** Revoke only this fixture role; preserve every other actor/owner association. */
  public void revoke(Domain domain, String subject, String roleId) {
    new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
      db.update("delete from summer_permission_user_role where domain_key=? and username=? and role_id=?", domain.key(), subject, roleId);
      db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", domain.key());
    });
  }

  /** Grant exact current resources; caller deliberately withholds raw scope to exercise service denial. */
  public void reachServiceWithoutDelegation(Domain domain, String subject, HandlerSelection selection) {
    grantHandlers(domain, subject, selection);
  }

  public interface HandlerSelection {
    boolean matches(org.springframework.web.method.HandlerMethod handler, String path, String method);
  }

  public String grantHandlers(Domain domain, String subject, HandlerSelection selection) {
    return grantHandlers(domain, subject, null, selection);
  }

  /** An explicit immutable code supports same-code, different-domain negative cases. */
  public String grantHandlers(Domain domain, String subject, String code, HandlerSelection selection) {
    resources.scan();
    var selected = new ArrayList<com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping>();
    var extensions = adapters.orderedStream().flatMap(adapter -> adapter.mappings().stream()).toList();
    for (var mapping : mappings.orderedStream().toList())
      for (var entry : mapping.getHandlerMethods().entrySet())
        for (String path : entry.getKey().getPatternValues())
          for (var method : entry.getKey().getMethodsCondition().getMethods())
            for (String verb : method.name().equals("GET") ? List.of("GET", "HEAD") : List.of(method.name())) {
            if (!selection.matches(entry.getValue(), path, verb)) continue;
            if (domain.kind().equals("PLATFORM") && path.startsWith("/api/v1/enterprises/")) continue;
            if (domain.kind().equals("ENTERPRISE") && path.startsWith("/api/v1/platform/")) continue;
            var mvc = ManagedInterfaceResources.mvc(verb, path, entry.getValue(), entry.getKey());
            var exact = extensions.stream().filter(candidate -> candidate.method().equals(mvc.method())
                && candidate.path().equals(mvc.path()) && candidate.conditions().equals(mvc.conditions())
                && candidate.handler().equals(mvc.handler())).toList();
            if (exact.size() > 1) throw new IllegalStateException("Ambiguous installed test resource");
            selected.add(exact.isEmpty() ? mvc : exact.getFirst());
          }
    return new org.springframework.transaction.support.TransactionTemplate(transactions)
        .execute(tx -> grantInTransaction(domain, subject, selected.stream().distinct().toList(), code));
  }

  /** Explicit adapter mappings, including MONITORING, RUNTIME and test-only exact handlers. */
  public String grantInstalled(Domain domain, String subject,
      java.util.function.Predicate<com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping> selection) {
    resources.scan();
    var selected = adapters.orderedStream().flatMap(adapter -> adapter.mappings().stream())
        .filter(selection).distinct().toList();
    return new org.springframework.transaction.support.TransactionTemplate(transactions)
        .execute(tx -> grantInTransaction(domain, subject, selected, null));
  }

  private String grantInTransaction(Domain domain, String subject,
      List<com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping> selected, String code) {
    activeUser(domain, subject);
    String id = "fixture-" + UUID.randomUUID();
    String immutableCode = code == null ? id : code;
    db.update("insert into summer_permission_group values (?,?,null,'Test',0,1)",domain.key(),id);
    db.update("insert into summer_permission_identity values (?,?,?,'INTERFACE')",domain.key(),id,immutableCode);
    db.update("insert into summer_permission values (?,?,?,?,null,'INTERFACE','AUTHORIZED',?,'ACTIVE',1)",domain.key(),id,immutableCode,"Exact test interfaces",id);
    db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at) values (?,?,?, ?,false,'ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,domain.id(),"Test role",domain.kind()+":"+id);
    db.update("insert into summer_permission_role values (?,?)",domain.key(),id);
    db.update("insert into summer_role_permission values (?,?,?)",domain.key(),id,id);
    if (db.queryForObject("select count(*) from summer_permission_user where domain_key=? and username=?",Integer.class,domain.key(),subject)==0)
      db.update("insert into summer_permission_user values (?,?,1)",domain.key(),subject);
    db.update("insert into summer_permission_user_role values (?,?,?)",domain.key(),subject,id);
    int bound=0;
    for (var mapping : selected) {
            if (!mapping.configurable() || !mapping.domainKind().equals(domain.kind()))
              throw new IllegalArgumentException("Selected test resource must be configurable in the exact domain");
            var resource=resources.registered(mapping);
            var modes = db.queryForList("select distinct p.access_mode from summer_permission_resource x join summer_permission p on p.domain_key=x.domain_key and p.permission_id=x.permission_id where x.domain_key=? and x.resource_type='INTERFACE' and x.resource_id=?", String.class, domain.key(), resource.id());
            // Existing PUBLIC/AUTHENTICATED resources keep their installed mode and need no fixture grant.
            if (modes.stream().anyMatch(mode -> !mode.equals("AUTHORIZED"))) continue;
            associations.bind(domain,id,resource); bound++;
          }
    if (bound==0) throw new IllegalArgumentException("No exact installed handlers selected for test grant");
    db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", domain.key());
    return id;
  }
}
