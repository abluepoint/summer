package com.abluepoint.summer.sample.authorization.application.permission;

import static com.abluepoint.summer.sample.authorization.application.permission.PlatformDepartmentScope.DOMAIN;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlatformDirectoryService {
  public record Person(String username, String displayName, boolean active) {}

  public record Affiliation(
      String username, String departmentId, boolean primary, boolean leader) {}

  public record DepartmentEdit(String name, String parentId, Integer sortOrder, Boolean enabled) {}

  public record MemberEdit(String action, String newPrimaryId) {}

  public record AdministratorRef(String username) {}
  public record GroupAdministrators(List<AdministratorRef> administrators) {}
  public record RosterGroup(String id, String name, String description, List<String> administrators) {}

  public record AddAdministrator(String username, String displayName) {}

  private final JdbcTemplate db;
  private final PermissionManagementGuard guard;
  private final AuthorizationChangeEventService events;
  private final AuthorizationAuditStore audit;
  private final RequestIdAccessor requests;
  private final HttpServletRequest request;
  private final ManagedPermissionAccess access;

  public PlatformDirectoryService(
      JdbcTemplate db,
      PermissionManagementGuard guard,
      AuthorizationChangeEventService events,
      AuthorizationAuditStore audit,
      RequestIdAccessor requests,
      HttpServletRequest request,
      ManagedPermissionAccess access) {
    this.db = db;
    this.guard = guard;
    this.events = events;
    this.audit = audit;
    this.requests = requests;
    this.request = request;
    this.access = access;
  }

  private static SummerException invalid(String msg) {
    return SummerException.withMessage(PermissionErrorCode.INVALID, msg);
  }

  private static SummerException conflict(String msg) {
    return SummerException.withMessage(PermissionErrorCode.CONFLICT, msg);
  }

  private static String text(String s, int max) {
    if (s == null || s.isBlank() || s.trim().length() > max) throw invalid("字段为空或过长");
    return s.trim();
  }

  long authorize(Authentication actor, String condition, boolean write) {
    var version =
        db.queryForObject(
            "select revision from summer_authorization_domain where domain_key=?"
                + (write ? " for update" : ""),
            Long.class,
            DOMAIN);
    guard.require(Domain.platform(), actor, write);
    if (db.queryForObject(
            "select count(*) from summer_platform_administrator where username=?",
            Integer.class,
            actor.getName())
        != 1) throw new AccessDeniedException("需要平台管理员身份");
    if (write && !("\"" + version + "\"").equals(condition)) throw conflict("目录或权限已变更，请刷新后重试");
    return version;
  }

  private PlatformDepartmentScope.Range scope(Authentication a, PlatformDepartmentScope directory) {
    return directory.range(
        a.getName(), (String) request.getAttribute(ManagedPermissionAccess.RESOURCE_ATTRIBUTE));
  }

  void changed(Authentication actor, String target, String action, long version) {
    String requestId = requests.currentRequestId();
    audit.mutation(DOMAIN, target, action, requestId, actor.getName());
    events.enqueue(Domain.platform(), "ORGANIZATION", target, action, version + 1, requestId);
  }

  private List<Person> people() {
    return db.query(
        "select a.username,a.display_name,u.active from summer_platform_administrator a join"
            + " summer_auth_user u on u.username=a.username order by a.display_name,a.username",
        (r, i) -> new Person(r.getString(1), r.getString(2), r.getBoolean(3)));
  }

  public Map<String, Object> roster(Authentication a) {
    long version = authorize(a, null, false);
    var result = new LinkedHashMap<String, Object>();
    result.put("version", version);
    result.put("people", people());
    var groups = new LinkedHashMap<String, RosterGroup>();
    db.query("""
        select g.id,g.name,g.description,a.username from summer_platform_user_group g
        left join summer_platform_group_administrator a on a.group_id=g.id order by g.name,g.id,a.username
        """, rs -> {
      String id = rs.getString(1);
      RosterGroup group = groups.get(id);
      if (group == null) {
        group = new RosterGroup(id, rs.getString(2), rs.getString(3), new ArrayList<>());
        groups.put(id, group);
      }
      String username = rs.getString(4);
      if (username != null) group.administrators().add(username);
    });
    result.put("groups", groups.values());
    if (access.delegated(Domain.platform(), a, true)
        && access.routeGrant(a, "PUT", "/api/v1/platform/users/{username}/roles")) {
      result.put(
          "roles",
          db.queryForList(
              """
              select r.role_id as roleId,r.name,r.status from summer_authorization_role r join summer_permission_role d
              on d.role_id=r.role_id where d.domain_key=? order by r.name,r.role_id
              """,
              DOMAIN));
      result.put("groupRoles", db.queryForList("""
          select distinct a.username,r.role_id as roleId from summer_platform_group_administrator a
          join summer_platform_group_role g on g.group_id=a.group_id
          join summer_permission_role d on d.role_id=g.role_id and d.domain_key=?
          join summer_authorization_role r on r.role_id=d.role_id and r.status='ACTIVE'
          order by a.username,r.role_id
          """, DOMAIN));
      result.put(
          "assignments",
          db.queryForList(
              """
              select u.username,u.version,r.role_id as roleId from summer_permission_user u
              join summer_platform_administrator a on a.username=u.username left join summer_permission_user_role r
              on r.domain_key=u.domain_key and r.username=u.username where u.domain_key=? order by u.username,r.role_id
              """,
              DOMAIN));
    }
    return result;
  }

  public void assignGroup(Authentication actor, String condition, String groupId, GroupAdministrators input) {
    long version = authorize(actor, condition, true);
    if (db.queryForObject("select count(*) from summer_platform_user_group where id=?", Integer.class, groupId) == 0)
      throw SummerException.withMessage(PermissionErrorCode.NOT_FOUND, "用户组不存在");
    Set<String> selected = new LinkedHashSet<>();
    if (input.administrators() != null)
      for (var person : input.administrators()) selected.add(text(person.username(), 128));

    var eligible = new HashSet<>(db.queryForList("""
        select a.username from summer_platform_administrator a join summer_auth_user u on u.username=a.username
        where u.active=true or exists(select 1 from summer_platform_group_administrator g
        where g.group_id=? and g.username=a.username)
        """, String.class, groupId));
    if (!eligible.containsAll(selected)) throw invalid("请选择平台名单中的启用管理员");
    db.update("delete from summer_platform_group_administrator where group_id=?", groupId);
    db.batchUpdate("insert into summer_platform_group_administrator(group_id,username) values (?,?)",
        selected, 200, (statement, username) -> {
          statement.setString(1, groupId);
          statement.setString(2, username);
        });
    changed(actor, groupId, "PLATFORM_GROUP_ADMINS_ASSIGNED", version);
  }

  public List<Person> candidates(
      Authentication a, String query, boolean personal, String departmentId) {
    authorize(a, null, false);
    if (!personal) scope(a, new PlatformDepartmentScope(db)).require(text(departmentId, 64));
    if (query.isBlank()) return List.of();
    String term = text(query, 200);
    String sql =
        personal
            ? """
            select u.username,u.username as display_name from summer_auth_user u where u.active=true
            and not exists(select 1 from summer_platform_administrator a where a.username=u.username)
            and locate(lower(?),lower(u.username))>0 order by u.username limit 50
            """
            : """
            select a.username,a.display_name from summer_platform_administrator a join summer_auth_user u on u.username=a.username
            where u.active=true and (locate(lower(?),lower(a.username))>0 or locate(lower(?),lower(a.display_name))>0)
            order by a.display_name,a.username limit 50
            """;
    return db.query(
        sql,
        (r, i) -> new Person(r.getString(1), r.getString(2), true),
        personal ? new Object[] {term} : new Object[] {term, term});
  }

  public void add(Authentication a, String condition, AddAdministrator input) {
    long v = authorize(a, condition, true);
    String user = text(input.username(), 128);
    String display =
        input.displayName() == null || input.displayName().isBlank()
            ? user
            : text(input.displayName(), 200);
    if (db.update(
            """
            insert into summer_platform_administrator(username,display_name) select username,? from summer_auth_user
            where username=? and active=true and not exists(select 1 from summer_platform_administrator where username=?)
            """,
            display,
            user,
            user)
        != 1) throw conflict("请选择尚未加入名单的启用账号");
    changed(a, user, "PLATFORM_ADMIN_ADDED", v);
  }

  public void remove(Authentication a, String condition, String user) {
    long v = authorize(a, condition, true);
    db.update("delete from summer_platform_group_administrator where username=?", user);
    db.update("delete from summer_platform_membership where username=?", user);
    if (db.update("delete from summer_platform_administrator where username=?", user) != 1)
      throw conflict("管理员已移出");
    db.update(
        "delete from summer_permission_user_role where domain_key=? and username=?", DOMAIN, user);
    db.update(
        "update summer_permission_user set version=version+1 where domain_key=? and username=?",
        DOMAIN,
        user);
    changed(a, user, "PLATFORM_ADMIN_REMOVED", v);
  }

  private List<Affiliation> affiliations() {
    return db.query(
        "select username,department_id,primary_department,leader from summer_platform_membership"
            + " order by username,department_id",
        (r, i) ->
            new Affiliation(r.getString(1), r.getString(2), r.getBoolean(3), r.getBoolean(4)));
  }

  public Map<String, Object> directory(Authentication a) {
    long version = authorize(a, null, false);
    var directory = new PlatformDepartmentScope(db);
    var range = scope(a, directory);
    var links = affiliations().stream().filter(m -> range.includes(m.departmentId())).toList();
    var names = new HashSet<String>();
    links.forEach(m -> names.add(m.username()));
    var visible = directory.tree.values().stream().filter(n -> range.includes(n.id())).toList();
    Map<String, PlatformDepartmentScope.Range> writable = new LinkedHashMap<>();
    var operations =
        Map.of(
            "POST /platform-departments",
            "create",
            "PUT /platform-departments/{id}",
            "edit",
            "DELETE /platform-departments/{id}",
            "delete",
            "PUT /platform-departments/{id}/members/{username}",
            "staff");
    if (access.delegated(Domain.platform(), a, true)) {
      var endpoints =
          db.queryForList(
              """
              select resource_id,http_method,normalized_path from summer_interface_resource
              where active=true and configurable=true and source='MVC' and domain_kind='PLATFORM'
              and handler_provenance like 'com.abluepoint.summer.sample.authorization.api.permission.PlatformDirectoryController@%'
              """);
      for (var endpoint : endpoints) {
        String resource = (String) endpoint.get("resource_id");
        String key =
            operations.get(
                endpoint.get("http_method")
                    + " "
                    + endpoint
                        .get("normalized_path")
                        .toString()
                        .substring("/api/v1/platform".length()));
        if (key != null && access.roleAllows(Domain.platform(), a, resource)) {
          var allowed = directory.range(a.getName(), resource);
          var ids = new HashSet<>(allowed.ids());
          ids.removeIf(id -> !range.includes(id));
          writable.put(key, new PlatformDepartmentScope.Range(allowed.all(), ids));
        }
      }
    }
    return Map.of(
        "version",
        version,
        "departments",
        visible,
        "all",
        range.all(),
        "memberships",
        links,
        "writable",
        writable,
        "people",
        people().stream().filter(p -> range.all() || names.contains(p.username())).toList());
  }

  public void saveDepartment(Authentication a, String condition, String id, DepartmentEdit edit) {
    long v = authorize(a, condition, true);
    var directory = new PlatformDepartmentScope(db);
    var range = scope(a, directory);
    var old = id == null ? null : directory.tree.get(id);
    if (id != null && old == null) throw invalid("部门不存在");
    String parent = edit.parentId();
    String name = text(edit.name(), 200);
    if (edit.sortOrder() == null || edit.sortOrder() < 0 || edit.enabled() == null)
      throw invalid("请填写排序和状态");
    if (old != null) range.require(id);
    if (old == null
        || !Objects.equals(old.parentId(), parent)
        || old.sortOrder() != edit.sortOrder()) {
      range.require(parent);
      if (old != null) range.require(old.parentId());
    }
    if (parent != null && (!directory.tree.containsKey(parent) || directory.under(parent, id)))
      throw invalid("无效的上级部门");
    if (parent != null
        && (old == null || !Objects.equals(parent, old.parentId()))
        && !directory.tree.get(parent).enabled()) throw conflict("停用部门不能接收子部门");
    if (old == null) {
      id = UUID.randomUUID().toString();
      db.update(
          "insert into summer_platform_department(id,parent_id,name,sort_order,enabled)"
              + " values(?,?,?,?,?)",
          id,
          parent,
          name,
          edit.sortOrder(),
          edit.enabled());
    } else
      db.update(
          "update summer_platform_department set name=?,parent_id=?,sort_order=?,enabled=? where"
              + " id=?",
          name,
          parent,
          edit.sortOrder(),
          edit.enabled(),
          id);
    changed(a, id, "PLATFORM_DEPARTMENT_SAVED", v);
  }

  private List<String> affected(String id) {
    return db.queryForList(
        """
        select p.name from summer_permission_selected_department d join summer_permission p
        on p.domain_key=d.domain_key and p.permission_id=d.permission_id where d.domain_key=? and d.department_id=? order by p.name
        """,
        String.class,
        DOMAIN,
        id);
  }

  public Map<String, Object> impact(Authentication a, String id) {
    long version = authorize(a, null, false);
    scope(a, new PlatformDepartmentScope(db)).require(id);
    return Map.of("version", version, "permissions", affected(id));
  }

  public void delete(Authentication a, String condition, String id) {
    long v = authorize(a, condition, true);
    var directory = new PlatformDepartmentScope(db);
    scope(a, directory).require(id);
    if (!directory.tree.containsKey(id)) throw invalid("部门不存在");
    if (directory.tree.values().stream().anyMatch(n -> id.equals(n.parentId()))
        || db.queryForObject(
                "select count(*) from summer_platform_membership where department_id=?",
                Integer.class,
                id)
            > 0) throw conflict("请先移走所有子部门并解除所有成员归属");
    db.update(
        """
        update summer_permission set version=version+1 where domain_key=? and permission_id in
        (select permission_id from summer_permission_selected_department where domain_key=? and department_id=?)
        """,
        DOMAIN,
        DOMAIN,
        id);
    db.update(
        "delete from summer_permission_selected_department where domain_key=? and department_id=?",
        DOMAIN,
        id);
    db.update("delete from summer_platform_department where id=?", id);
    changed(a, id, "PLATFORM_DEPARTMENT_DELETED", v);
  }

  public void member(Authentication a, String condition, String id, String user, MemberEdit edit) {
    long v = authorize(a, condition, true);
    var directory = new PlatformDepartmentScope(db);
    var range = scope(a, directory);
    range.require(id);
    var unit = directory.tree.get(id);
    if (unit == null) throw invalid("部门不存在");
    var rows =
        db.queryForList(
            "select department_id from summer_platform_membership where username=? order by"
                + " primary_department desc,department_id",
            String.class,
            user);
    String action = text(edit.action(), 20);
    if ("JOIN".equals(action)) {
      if (!unit.enabled()
          || rows.contains(id)
          || db.queryForObject(
                  """
                  select count(*) from summer_platform_administrator a join summer_auth_user u on u.username=a.username
                  where a.username=? and u.active=true
                  """,
                  Integer.class,
                  user)
              != 1) throw conflict("请选择可加入此部门的平台管理员");
      db.update(
          "insert into summer_platform_membership(username,department_id,primary_department,leader)"
              + " values(?,?,?,false)",
          user,
          id,
          rows.isEmpty());
    } else {
      if (!rows.contains(id)) throw invalid("该管理员不属于此部门");
      switch (action) {
        case "PRIMARY" -> {
          range.require(rows.getFirst());
          primary(user, id);
        }
        case "LEADER", "CLEAR_LEADER" -> {
          if ("LEADER".equals(action))
            db.update(
                "update summer_platform_membership set leader=false where department_id=?", id);
          db.update(
              "update summer_platform_membership set leader=? where department_id=? and username=?",
              "LEADER".equals(action),
              id,
              user);
        }
        case "TRANSFER" -> {
          String next = text(edit.newPrimaryId(), 64);
          range.require(next);
          var destination = directory.tree.get(next);
          if (id.equals(next) || destination == null || !destination.enabled())
            throw invalid("请选择启用的其他部门");
          db.update(
              "delete from summer_platform_membership where username=? and department_id=?",
              user,
              id);
          if (!rows.contains(next))
            db.update(
                "insert into"
                    + " summer_platform_membership(username,department_id,primary_department,leader)"
                    + " values(?,?,false,false)",
                user,
                next);
          if (rows.getFirst().equals(id)) primary(user, next);
        }
        case "LEAVE" -> {
          db.update(
              "delete from summer_platform_membership where username=? and department_id=?",
              user,
              id);
          if (rows.getFirst().equals(id) && rows.size() > 1) {
            String next = rows.size() == 2 ? rows.get(1) : edit.newPrimaryId();
            if (next == null || id.equals(next) || !rows.contains(next))
              throw invalid("请从剩余归属中选择新主部门");
            range.require(next);
            primary(user, next);
          }
        }
        default -> throw invalid("未知人员调配操作");
      }
    }
    changed(a, id, "PLATFORM_MEMBER_" + action, v);
  }

  private void primary(String user, String id) {
    db.update(
        "update summer_platform_membership set primary_department=false where username=? and"
            + " primary_department=true",
        user);
    db.update(
        "update summer_platform_membership set primary_department=true where username=? and"
            + " department_id=?",
        user,
        id);
  }
}
