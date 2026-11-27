package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.sample.authorization.application.permission.MenuPublicationState;
import com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerMenus;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceAssociations;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** One-time enrollment for a migrated managed domain. A restart never restores a removed grant. */
@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class PlatformDirectoryInstallation {
  private static final Domain PLATFORM = Domain.platform();
  private final JdbcTemplate db;
  private final ManagedInterfaceResources resources;
  private final PermissionResourceAssociations associations;
  private final MenuPublicationState publication;

  public PlatformDirectoryInstallation(
      JdbcTemplate db,
      ManagedInterfaceResources resources,
      PermissionResourceAssociations associations,
      MenuPublicationState publication) {
    this.db = db;
    this.resources = resources;
    this.associations = associations;
    this.publication = publication;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void install() {
    if (!lockManagedPlatform()) return;
    if (db.queryForObject(
                "select count(*) from summer_platform_directory_install where id=1", Integer.class)
            > 0) {
      if (db.queryForObject("select count(*) from summer_platform_directory_install where id=3", Integer.class) == 0) {
        resources.scan();
        installGroupAssignment();
      }
      return;
    }
    String role = administratorRole();
    // The scan commits in its own transaction. The subsequent binding query must see that commit.
    resources.scan();
    String group = "platform-directory";
    db.update(
        "insert into summer_permission_group values (?,?,null,'平台组织机构',0,1)",
        PLATFORM.key(),
        group);
    for (var entry :
        Map.of(
                "roster",
                "维护平台管理员名单",
                "view",
                "查看平台组织机构",
                "structure",
                "维护平台部门",
                "staff",
                "调配平台部门人员")
            .entrySet()) permission(entry.getKey(), entry.getValue(), "INTERFACE", role);
    permission("scope", "全部平台部门", "DATA", role);
    db.update(
        "insert into summer_permission_data_scope(domain_key,permission_id,scope_preset)"
            + " values(?,?,'PLATFORM_ALL_DEPARTMENTS')",
        PLATFORM.key(),
        "platform-directory-scope");
    for (String key : List.of("view", "structure", "staff"))
      db.update(
          "insert into"
              + " summer_permission_data_interface(domain_key,permission_id,interface_permission_id)"
              + " values(?,?,?)",
          PLATFORM.key(),
          "platform-directory-scope",
          "platform-directory-" + key);
    var endpoints =
        db.queryForList(
            """
            select http_method,normalized_path,resource_id from summer_interface_resource where active=true and configurable=true
            and domain_kind='PLATFORM' and application_id='application' and source='MVC' and handler_provenance like ?
            """,
            "com.abluepoint.summer.sample.authorization.api.permission.PlatformDirectoryController@%");
    for (var e : endpoints) {
      String method = e.get("http_method").toString(), path = e.get("normalized_path").toString();
      if (method.equals("HEAD") || path.equals("/api/v1/platform/administrators/user-groups/{id}")) continue;
      String key;
      if (path.startsWith("/api/v1/platform/administrators")) key = "roster";
      else if (path.endsWith("/candidates") || path.contains("/members/")) key = "staff";
      else if (method.equals("GET") && !path.endsWith("/deletion-impact")) key = "view";
      else key = "structure";
      var resource = new Resource(e.get("resource_id").toString(), "INTERFACE", "MVC");
      associations.bind(PLATFORM, "platform-directory-" + key, resource);
    }
    if (endpoints.isEmpty())
      throw new IllegalStateException("Platform directory endpoints missing");
    permission("organization-menu", "平台组织机构管理导航", "MENU", role);
    associations.bind(
        PLATFORM,
        "platform-directory-organization-menu",
        new Resource("platform/organization", "MENU", "MANAGEMENT"));
    publication.lock(PLATFORM);
    var state = publication.read();
    if (state.published()) {
      long next = state.manifestVersion() + 1;
      db.update(
          "update summer_menu_manifest_item set manifest_version=? where manifest_version=?",
          next,
          state.manifestVersion());
      menu("platform-management", null, "平台管理", 0, next);
      menu("platform/administrators", "platform-management", "平台管理员管理", 10, next);
      menu("platform/organization", "platform-management", "平台组织机构管理", 20, next);
      publication.published(next);
    }
    db.update(
        "update summer_authorization_domain set revision=revision+1 where domain_key=?",
        PLATFORM.key());
    db.update("insert into summer_platform_directory_install values (1)");
    installGroupAssignment();
  }

  private void installGroupAssignment() {
    String resourceId = db.queryForObject("""
        select resource_id from summer_interface_resource where active=true and configurable=true
        and domain_kind='PLATFORM' and application_id='application' and source='MVC' and http_method='PUT'
        and normalized_path='/api/v1/platform/administrators/user-groups/{id}'
        and handler_provenance like 'com.abluepoint.summer.sample.authorization.api.permission.PlatformDirectoryController@%'
        """, String.class);
    permission("group-assignment", "为平台用户组分配管理员", "INTERFACE", administratorRole());
    associations.bind(PLATFORM, "platform-directory-group-assignment", new Resource(resourceId, "INTERFACE", "MVC"));
    db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", PLATFORM.key());
    db.update("insert into summer_platform_directory_install values (3)");
  }

  private boolean lockManagedPlatform() {
    var mode =
        db.queryForList(
            "select engine_mode from summer_authorization_domain where domain_key=? for update",
            String.class,
            PLATFORM.key());
    return mode.equals(List.of("MANAGED"));
  }

  private String administratorRole() {
    var roles =
        db.queryForList(
            """
            select r.role_id from summer_authorization_role r join summer_permission_role d on d.role_id=r.role_id
            where d.domain_key=? and r.code='PLATFORM:permission-administrator'
            """,
            String.class,
            PLATFORM.key());
    // Recovered domains may have a different role inventory. Never substitute an arbitrary role.
    return roles.isEmpty() ? null : roles.getFirst();
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void installFileRepositories() {
    if (!lockManagedPlatform()) return;
    var installed = db.queryForList("select id from summer_file_repository_install", Integer.class);
    if (installed.contains(3)) return;
    if (installed.contains(1)) {
      resources.scan();
      if (!installed.contains(2) && db.queryForObject("select count(*) from summer_permission where domain_key=? and permission_id='file-repositories-manage'",
          Integer.class, PLATFORM.key()) > 0) {
        String resource = db.queryForObject("""
            select resource_id from summer_interface_resource where active=true and configurable=true
            and domain_kind='PLATFORM' and application_id='application' and source='MVC' and http_method='PUT'
            and normalized_path='/api/v1/platform/file-repositories/{id}/credentials' and handler_provenance like ?
            """, String.class, "com.abluepoint.summer.sample.authorization.api.permission.FileRepositoryController@%");
        associations.bind(PLATFORM, "file-repositories-manage", new Resource(resource, "INTERFACE", "MVC"));
        db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", PLATFORM.key());
      }
      if (!installed.contains(2)) db.update("insert into summer_file_repository_install values (2)");
      migrateFileStoreManagerBindings();
      db.update("insert into summer_file_repository_install values (3)");
      return;
    }
    installPlatformPage("file-repositories", ManagementConsumerMenus.FILE_REPOSITORIES.title(), "FileRepositoryController", 30);
    db.update("insert into summer_file_repository_install values (1),(2),(3)");
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void installFiles() {
    if (!lockManagedPlatform()) return;
    var installed = db.queryForList("select id from summer_file_repository_install", Integer.class);
    if (installed.contains(5)) return;
    if (!installed.contains(4)) {
      installPlatformPage("files", ManagementConsumerMenus.FILE_MANAGER.title(), "PlatformFileController", 40);
      db.update("insert into summer_file_repository_install values (4)");
    } else if (db.queryForObject("select count(*) from summer_permission where domain_key=? and permission_id='files-manage'",
        Integer.class, PLATFORM.key()) > 0) {
      resources.scan();
      String endpoint = db.queryForObject("""
          select resource_id from summer_interface_resource where active=true and configurable=true
          and domain_kind='PLATFORM' and application_id='application' and source='MVC' and http_method='DELETE'
          and normalized_path='/api/v1/platform/files/{id}/entries' and handler_provenance like ?
          """, String.class, "com.abluepoint.summer.sample.authorization.api.permission.PlatformFileController@%");
      associations.bind(PLATFORM, "files-manage", new Resource(endpoint, "INTERFACE", "MVC"));
      db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", PLATFORM.key());
    }
    db.update("insert into summer_file_repository_install values (5)");
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void installUserGroups() {
    if (!lockManagedPlatform() || db.queryForObject(
        "select count(*) from summer_platform_directory_install where id=2", Integer.class) > 0) return;
    installPlatformPage("user-groups", "平台用户组管理", "PlatformUserGroupController", 25);
    db.update("insert into summer_platform_directory_install values (2)");
  }

  private void installPlatformPage(String group, String title, String controller, int sort) {
    String role = administratorRole();
    resources.scan();
    db.update("insert into summer_permission_group values (?,?,null,?,0,1)", PLATFORM.key(), group, title);
    addPermission(group, "view", "查看" + title, "INTERFACE", role);
    addPermission(group, "manage", "维护" + title, "INTERFACE", role);
    addPermission(group, "menu", title + "导航", "MENU", role);
    var endpoints = db.queryForList("""
        select http_method,resource_id from summer_interface_resource where active=true and configurable=true
        and domain_kind='PLATFORM' and application_id='application' and source='MVC' and handler_provenance like ?
        """, "com.abluepoint.summer.sample.authorization.api.permission." + controller + "@%");
    if (endpoints.isEmpty()) throw new IllegalStateException("Missing endpoints for " + controller);
    for (var row : endpoints) {
      String method = row.get("http_method").toString();
      if (method.equals("HEAD")) continue;
      associations.bind(PLATFORM, group + (method.equals("GET") ? "-view" : "-manage"),
          new Resource(row.get("resource_id").toString(), "INTERFACE", "MVC"));
    }
    String page = "platform/" + group;
    associations.bind(PLATFORM, group + "-menu", new Resource(page, "MENU", "MANAGEMENT"));
    publication.lock(PLATFORM);
    var state = publication.read();
    if (state.published()) {
      long next = state.manifestVersion() + 1;
      db.update("update summer_menu_manifest_item set manifest_version=? where manifest_version=?", next, state.manifestVersion());
      menu("platform-management", null, "平台管理", 0, next);
      String parent = ManagementConsumerMenus.platformParent(page);
      var fileDirectory = ManagementConsumerMenus.FILE_DIRECTORY;
      if (fileDirectory.id().equals(parent)) {
        // Siblings with equal sort order use menu ID as the tie-breaker.
        int platformOrder = db.queryForObject("""
            select coalesce(l.sort_order,m.sort_order) from summer_menu_manifest_item m
            left join summer_menu_domain_layout l on l.domain_key=? and l.menu_id=m.menu_id
            where m.menu_id='platform-management'
            """, Integer.class, PLATFORM.key());
        menu(fileDirectory.id(), null, fileDirectory.title(), platformOrder, next);
      }
      menu(page, parent, title, sort, next);
      publication.published(next);
    }
    db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", PLATFORM.key());
  }

  // Handler identities include generic return types. Transfer only existing bindings whose
  // immediately preceding handler differs solely by the storage API type rename.
  private void migrateFileStoreManagerBindings() {
    var bindings = db.queryForList("""
        select current_handler.resource_id, binding.permission_id
        from summer_interface_resource current_handler
        join summer_interface_resource prior on prior.locator_hash=current_handler.locator_hash
          and prior.generation=current_handler.generation-1 and prior.active=false
          and prior.configurable=true and prior.domain_kind=current_handler.domain_kind
        join summer_permission_resource binding on binding.resource_id=prior.resource_id
          and binding.resource_type='INTERFACE' and binding.domain_key=?
        where current_handler.active=true and current_handler.configurable=true
          and current_handler.source='MVC' and current_handler.application_id='application'
          and current_handler.domain_kind='PLATFORM'
          and current_handler.normalized_path='/api/v1/platform/file-repositories'
          and current_handler.http_method in ('GET','HEAD','POST')
          and current_handler.handler_provenance like 'com.abluepoint.summer.sample.authorization.api.permission.FileRepositoryController@fileRepositoryController#%'
          and prior.handler_provenance<>current_handler.handler_provenance
          and replace(prior.handler_provenance,'com.abluepoint.summer.sample.storage.FileManager$',
              'com.abluepoint.summer.sample.storage.FileStoreManager$')=current_handler.handler_provenance
        """, PLATFORM.key());
    for (var row : bindings)
      associations.bind(PLATFORM, row.get("permission_id").toString(),
          new Resource(row.get("resource_id").toString(), "INTERFACE", "MVC"));
    if (!bindings.isEmpty())
      db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", PLATFORM.key());
  }

  private void permission(String suffix, String name, String type, String role) {
    addPermission("platform-directory", suffix, name, type, role);
  }

  private void addPermission(String group, String suffix, String name, String type, String role) {
    String id = group + "-" + suffix;
    db.update(
        "insert into summer_permission_identity values(?,?,?,?)", PLATFORM.key(), id, id, type);
    db.update(
        "insert into summer_permission"
            + " values(?,?,?,?,null,?,'AUTHORIZED',?,'ACTIVE',1)",
        PLATFORM.key(),
        id,
        id,
        name,
        type, group);
    if (role != null)
      db.update("insert into summer_role_permission values(?,?,?)", PLATFORM.key(), role, id);
  }

  private void menu(String id, String parent, String title, int sort, long version) {
    db.update(
        """
        insert into summer_menu_manifest_item(menu_id,parent_menu_id,path,title_key,icon_key,sort_order,manifest_version,client_code,created_at,updated_at)
        values(?,?,?,?,'',?,?,'web',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
        on duplicate key update manifest_version=values(manifest_version)
        """,
        id,
        parent,
        "/" + id,
        title,
        sort,
        version);
  }
}
