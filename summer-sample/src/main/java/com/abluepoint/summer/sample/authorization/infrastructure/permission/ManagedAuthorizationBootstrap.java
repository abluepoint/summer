package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.sample.authorization.*;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Explicit installation of fresh managed domains only. Never repairs revoked grants on restart. */
@Component
public class ManagedAuthorizationBootstrap {
  private final com.abluepoint.summer.sample.authorization.application.permission
          .PermissionResourceAssociations
      associations;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final ManagedInterfaceResources managedResources;
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;
  private final String domains;
  private final String username;

  ManagedAuthorizationBootstrap(
      com.abluepoint.summer.sample.authorization.application.permission
              .PermissionResourceAssociations
          associations,
      AuthorizationAuditStore audit,
      AuthorizationChangeEventService events,
      JdbcTemplate db,
      PlatformTransactionManager tm,
      ManagedInterfaceResources managedResources,
      ObjectProvider<RequestMappingHandlerMapping> mappings,
      @Value("${summer.sample.authorization.managed-bootstrap-domains:}") String domains,
      @Value("${summer.security.authorization-server.user.username:}") String username) {
    this.associations = associations;
    this.audit = audit;
    this.events = events;
    this.db = db;
    this.tx = new TransactionTemplate(tm);
    this.managedResources = managedResources;
    this.mappings = mappings;
    this.domains = domains;
    this.username = username;
  }

  private static String id(String key) {
    return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initialize() {
    if (domains.isBlank()) return;
    for (String key : domains.split(",")) {
      String[] parts = key.trim().split(":", 2);
      if (parts.length != 2)
        throw new IllegalArgumentException("Explicit managed domain key required");
      Domain d = new Domain(parts[0], parts[1]);
      tx.executeWithoutResult(status -> {
        var state = db.queryForList(
            "select engine_mode,revision from summer_authorization_domain where domain_key=? for update",
            d.key());
        if (state.size() != 1 || !"MANAGED".equals(state.getFirst().get("engine_mode"))
            || !(state.getFirst().get("revision") instanceof Number revision) || revision.longValue() < 0
            || db.queryForObject("select count(*) from summer_permission_bootstrap where domain_key=?",
                Integer.class, d.key()) != 1)
          throw new org.springframework.security.access.AccessDeniedException(
              "Managed domain requires controlled initialization or recovery");
      });
    }
  }

  /**
   * Preserved seed algorithm for the controlled installation gate. Ordinary startup never calls
   * this method. It is not itself a new-installation or disaster-recovery authorization boundary.
   */
  private void seedNewDomain(Domain d) {
    seedNewDomain(d, username);
  }

  /** The caller supplies provenance from an explicit installer or a new-enterprise transaction. */
  public boolean provision(Domain domain, String actor, String operationId, String provenanceHash,
      String requestId) {
    if (actor == null || actor.isBlank() || actor.length() > 128
        || operationId == null || !operationId.matches("[A-Za-z0-9._:-]{1,128}")
        || provenanceHash == null || !provenanceHash.matches("[0-9a-f]{64}")
        || requestId == null || requestId.isBlank() || requestId.length() > 128)
      throw new IllegalArgumentException("Exact installation identity and trusted provenance required");
    return Boolean.TRUE.equals(tx.execute(status -> {
      var receipt = db.queryForList(
          "select domain_key,actor,provenance_hash from summer_authorization_installation where operation_id=? for update",
          operationId);
      if (!receipt.isEmpty()) {
        var prior = receipt.getFirst();
        if (!domain.key().equals(prior.get("domain_key")) || !actor.equals(prior.get("actor"))
            || !provenanceHash.equals(prior.get("provenance_hash")))
          throw new IllegalArgumentException("Installation identity was used for another operation");
        requireInitialized(domain);
        return false;
      }
      for (String table : List.of("summer_authorization_domain", "summer_permission_bootstrap",
          "summer_permission_identity", "summer_permission_group", "summer_permission_user",
          "summer_permission", "summer_permission_role", "summer_role_permission", "summer_role_platform_permission",
          "summer_permission_user_role", "summer_permission_resource", "summer_permission_mutation",
          "summer_permission_resource_control", "summer_authorization_migration_snapshot")) {
        if (db.queryForObject("select count(*) from " + table + " where domain_key=?", Integer.class,
            domain.key()) != 0)
          throw new org.springframework.security.access.AccessDeniedException(
              "Existing authorization domain requires verified recovery or migration");
      }
      // Bare historical IDs are ambiguous across kinds. They block installation rather than
      // being interpreted as another domain or silently upgraded to a fresh MANAGED domain.
      if (domain.kind().equals("PLATFORM") && db.queryForObject(
          "select count(*) from summer_authorization_capability where scope='PLATFORM'",
          Integer.class) != 0)
        throw new org.springframework.security.access.AccessDeniedException(
            "Historical platform capabilities require verified migration or recovery");
      for (String table : List.of("summer_authorization_role", "summer_authorization_user_binding",
          "summer_authorization_capability", "summer_data_scope_policy", "summer_data_scope_binding",
          "summer_data_scope_policy_audit", "summer_authorization_audit",
          "summer_authorization_change_outbox")) {
        if (db.queryForObject("select count(*) from " + table + " where enterprise_id in (?,?)",
            Integer.class, domain.id(), domain.key()) != 0)
          throw new org.springframework.security.access.AccessDeniedException(
              "Historical authorization facts require verified migration or recovery");
      }
      if (db.queryForObject("select count(*) from summer_authorization_installation where domain_key=?",
          Integer.class, domain.key()) != 0)
        throw new org.springframework.security.access.AccessDeniedException("Domain was already initialized");
      db.update("insert into summer_authorization_installation(operation_id,domain_key,actor,provenance_hash,request_id) values (?,?,?,?,?)",
          operationId, domain.key(), actor, provenanceHash, requestId);
      seedNewDomain(domain, actor, requestId);
      return true;
    }));
  }

  private void requireInitialized(Domain domain) {
    var rows = db.queryForList(
        "select engine_mode,revision from summer_authorization_domain where domain_key=? for update",
        domain.key());
    if (rows.size() != 1 || !"MANAGED".equals(rows.getFirst().get("engine_mode"))
        || !(rows.getFirst().get("revision") instanceof Number revision) || revision.longValue() < 0
        || db.queryForObject("select count(*) from summer_permission_bootstrap where domain_key=?",
            Integer.class, domain.key()) != 1)
      throw new org.springframework.security.access.AccessDeniedException(
          "Managed domain requires controlled initialization or recovery");
  }

  private void seedNewDomain(Domain d, String actor) {
    seedNewDomain(d, actor, null);
  }

  private void seedNewDomain(Domain d, String actor, String requestId) {
      tx.executeWithoutResult(
          status -> {
            db.update(
                "insert into summer_authorization_domain(domain_key,engine_mode,revision) values"
                    + " (?,'MANAGED',0)",
                d.key());
            var locked =
                db.queryForList(
                    "select engine_mode from summer_authorization_domain where domain_key=? for"
                        + " update",
                    d.key());
            if (!"MANAGED".equals(locked.getFirst().get("engine_mode")))
              throw new IllegalStateException("Existing legacy domains require the migration gate");
            if (db.queryForObject(
                    "select count(*) from summer_permission_bootstrap where domain_key=?",
                    Integer.class,
                    d.key())
                > 0) return;
            if (db.queryForObject(
                    "select count(*) from summer_auth_user where username=? and active=true",
                    Integer.class,
                    actor)
                == 0) throw new IllegalStateException("Active bootstrap identity required");
            if (d.kind().equals("ENTERPRISE")
                && db.queryForObject(
                        "select count(*) from summer_enterprise_member where enterprise_id=? and"
                            + " subject=? and status='ACTIVE'",
                        Integer.class,
                        d.id(),
                        actor)
                    == 0) throw new IllegalStateException("Active bootstrap membership required");
            db.queryForList(
                "select revision from summer_authorization_domain where domain_key=? for update",
                d.key());
            String group = id(d.key() + ":management"), role = id(d.key() + ":administrator");
            db.update(
                "insert into summer_permission_group values (?,?,null,'管理',0,1)", d.key(), group);
            db.update(
                "insert into"
                    + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at)"
                    + " values (?,?,?,"
                    + " ?,true,'ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                role,
                d.id(),
                "权限管理员",
                d.kind() + ":permission-administrator");
            db.update("insert into summer_permission_role values (?,?)", d.key(), role);
            db.update("insert into summer_permission_user values (?,?,1)", d.key(), actor);
            db.update(
                "insert into summer_permission_user_role values (?,?,?)", d.key(), actor, role);
            if (Domain.platform().equals(d))
              db.update("insert into summer_platform_administrator(username,display_name) values (?,?)", actor, actor);
            for (var mapping : mappings.orderedStream().toList())
              for (var entry : mapping.getHandlerMethods().entrySet()) {
                String owner = entry.getValue().getBeanType().getName();
                if (owner.endsWith(".PlatformDirectoryController") || owner.endsWith(".FileRepositoryController") || owner.endsWith(".PlatformFileController") || owner.endsWith(".PlatformUserGroupController")) continue;
                if (!com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerRoutes.management(owner))
                  continue;
                var info = entry.getKey();
                if (info.getCustomCondition() != null)
                  throw new IllegalStateException("Unsupported bootstrap mapping");
                for (String path : info.getPatternValues()) {
                  String kind = com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerRoutes.domainKind(owner, path);
                  if (!kind.equals(d.kind())) continue;
                  for (var method : info.getMethodsCondition().getMethods()) {
                    var installedMapping = ManagedInterfaceResources.mvc(method.name(), path, entry.getValue(), info);
                    if (!installedMapping.configurable()) continue;
                    String mode =
                        path.endsWith("/authorization/status")
                            ? "PUBLIC"
                            : (path.endsWith("/authorization/profile")
                                || path.endsWith("/authorization/operations")
                                    || path.endsWith("/authorization/navigation")
                                    || (owner.equals("com.abluepoint.summer.sample.menu.MenuManifestController")
                                        && entry.getValue().getMethod().getName().equals("visible")
                                        && method.name().equals("GET")
                                        && path.equals("/api/v1/enterprises/{enterpriseId}/menu-manifest")))
                                ? "AUTHENTICATED"
                                : "AUTHORIZED";
                    String label =
                        mode.equals("AUTHORIZED")
                            ? (method.name().equals("GET") ? "management-read" : "management-write")
                            : mode;
                    String permission = id(d.key() + ":" + label);
                    if (db.queryForObject(
                            "select count(*) from summer_permission where domain_key=? and"
                                + " permission_id=?",
                            Integer.class,
                            d.key(),
                            permission)
                        == 0) {
                      db.update(
                          "insert into summer_permission_identity values (?,?,?,'INTERFACE')",
                          d.key(),
                          permission,
                          label);
                      db.update(
                          "insert into summer_permission values"
                              + " (?,?,?,?,null,'INTERFACE',?,?,'ACTIVE',1)",
                          d.key(),
                          permission,
                          label,
                          label.equals("management-read")
                              ? "查看权限与角色"
                              : label.equals("management-write")
                                  ? "管理权限与角色"
                                  : mode.equals("PUBLIC") ? "服务状态" : "身份与导航",
                          mode,
                          group);
                      if (mode.equals("AUTHORIZED"))
                        db.update(
                            "insert into summer_role_permission values (?,?,?)",
                            d.key(),
                            role,
                            permission);
                    }
                    var resource =
                        managedResources.registered(ManagedInterfaceResources.mvc(
                            method.name(), path, entry.getValue(), info));
                    // All writes take the domain lock; later interface/menu writers use this same
                    // table.
                    associations.bind(d, permission, resource);
                  }
                }
              }
            for (var page : com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerMenus.PAGES) {
              String menuId = page.id();
              String permission = id(d.key() + ":navigation:" + menuId);
              db.update(
                  "insert into summer_permission_identity values (?,?,?,'MENU')",
                  d.key(),
                  permission,
                  "management-navigation-" + menuId);
              db.update(
                  "insert into summer_permission values"
                      + " (?,?,?,?,null,'MENU','AUTHORIZED',?,'ACTIVE',1)",
                  d.key(),
                  permission,
                  "management-navigation-" + menuId,
                  page.title() + "导航",
                  group);
              db.update(
                  "insert into summer_role_permission values (?,?,?)", d.key(), role, permission);
              associations.bind(
                  d,
                  permission,
                  new com.abluepoint.summer.security.authorization.ResourceAuthorization.Resource(
                      menuId, "MENU", "MANAGEMENT"));
            }
            audit.mutation(d.key(), role, "MANAGED_INITIALIZED", requestId, actor);
            events.enqueue(d, "ROLE", role, "MANAGED_INITIALIZED", 1, requestId);
            db.update(
                "insert into summer_permission_bootstrap values (?,CURRENT_TIMESTAMP(6))", d.key());
          });
  }
}
