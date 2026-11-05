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
      tx.executeWithoutResult(
          status -> {
            db.update(
                "insert into summer_authorization_domain(domain_key,engine_mode,revision) values"
                    + " (?,'MANAGED',0) on duplicate key update domain_key=domain_key",
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
                    username)
                == 0) throw new IllegalStateException("Active bootstrap identity required");
            if (d.kind().equals("ENTERPRISE")
                && db.queryForObject(
                        "select count(*) from summer_enterprise_member where enterprise_id=? and"
                            + " subject=? and status='ACTIVE'",
                        Integer.class,
                        d.id(),
                        username)
                    == 0) throw new IllegalStateException("Active bootstrap membership required");
            db.queryForList(
                "select revision from summer_authorization_domain where domain_key=? for update",
                d.key());
            String group = id(d.key() + ":management"), role = id(d.key() + ":administrator");
            db.update(
                "insert into summer_permission_group values (?,?,null,'管理',0,1)", d.key(), group);
            db.update(
                "insert into"
                    + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,capability_ids,capability_codes,version,created_at,updated_at)"
                    + " values (?,?,?,"
                    + " ?,true,'ACTIVE','','',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                role,
                d.id(),
                "权限管理员",
                d.kind() + ":permission-administrator");
            db.update("insert into summer_permission_role values (?,?)", d.key(), role);
            db.update("insert into summer_permission_user values (?,?,1)", d.key(), username);
            db.update(
                "insert into summer_permission_user_role values (?,?,?)", d.key(), username, role);
            for (var mapping : mappings.orderedStream().toList())
              for (var entry : mapping.getHandlerMethods().entrySet()) {
                String owner = entry.getValue().getBeanType().getName();
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
            audit.mutation(d.key(), role, "MANAGED_INITIALIZED", null, username);
            events.enqueue(d, "ROLE", role, "MANAGED_INITIALIZED", 1, null);
            db.update(
                "insert into summer_permission_bootstrap values (?,CURRENT_TIMESTAMP(6))", d.key());
          });
  }
}
