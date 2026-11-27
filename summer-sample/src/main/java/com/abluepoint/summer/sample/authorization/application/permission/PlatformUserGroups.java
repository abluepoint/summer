package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlatformUserGroups {
  public record Role(String roleId) {}
  public record Edit(String operation, String name, String description, List<Role> roles) {}
  public record Group(String id, String name, String description, List<String> roles) {}

  // Retired V200 membership records never enter the runtime grant relation.
  static final String EFFECTIVE_ROLES = """
      (select domain_key,username,role_id from summer_permission_user_role
       union select 'PLATFORM:platform',a.username,r.role_id
       from summer_platform_group_administrator a join summer_platform_group_role r on r.group_id=a.group_id)
      """;

  private final JdbcTemplate db;
  private final PlatformDirectoryService directory;

  public PlatformUserGroups(JdbcTemplate db, PlatformDirectoryService directory) {
    this.db = db;
    this.directory = directory;
  }

  private static final String ROLES = """
      select r.role_id,r.name,r.status from summer_authorization_role r join summer_permission_role d
      on d.role_id=r.role_id where d.domain_key='PLATFORM:platform'
      """;

  public Map<String,Object> list(Authentication actor) {
    long version = directory.authorize(actor, null, false);
    var groups = new LinkedHashMap<String,Group>();
    db.query("select id,name,description from summer_platform_user_group order by name,id", rs -> {
      String id = rs.getString(1);
      groups.put(id, new Group(id, rs.getString(2), rs.getString(3), new ArrayList<>()));
    });
    db.query("select group_id,role_id from summer_platform_group_role order by role_id",
        rs -> { groups.get(rs.getString(1)).roles().add(rs.getString(2)); });
    return Map.of("version", version, "groups", groups.values(),
        "roles", db.query(ROLES + " order by r.name,r.role_id", (rs,i) ->
            Map.of("id",rs.getString(1),"name",rs.getString(2),"active","ACTIVE".equals(rs.getString(3)))));
  }

  public String save(Authentication actor, String condition, String id, Edit input) {
    long version = directory.authorize(actor, condition, true);
    boolean create = id == null;
    if (create) id = UUID.randomUUID().toString();
    else requireGroup(id);

    // Older clients submit group details and roles together.
    String operation = input.operation();
    String event;
    if (operation == null) {
      saveDetails(id, create, input);
      replaceRoles(id, input.roles());
      event = create ? "PLATFORM_GROUP_CREATED" : "PLATFORM_GROUP_UPDATED";
    } else {
      if (create) throw invalid("绑定操作需要已存在的用户组");
      event = switch (operation) {
        case "DETAILS" -> {
          if (input.roles() != null) throw invalid("编辑用户组仅接受名称和说明");
          saveDetails(id, false, input);
          yield "PLATFORM_GROUP_UPDATED";
        }
        case "ROLES" -> {
          if (input.name() != null || input.description() != null)
            throw invalid("绑定角色仅接受角色列表");
          replaceRoles(id, input.roles());
          yield "PLATFORM_GROUP_ROLES_UPDATED";
        }
        default -> throw invalid("未知的用户组操作");
      };
    }
    directory.changed(actor, id, event, version);
    return id;
  }

  private void saveDetails(String id, boolean create, Edit input) {
    if (input.name() == null || input.name().isBlank()) throw invalid("请输入用户组名称");
    String name = input.name().strip();
    String description = input.description() == null ? null : input.description().strip();
    if (db.queryForObject("select count(*) from summer_platform_user_group where name=? and id<>?",
        Integer.class, name, id) > 0) throw invalid("用户组名称已存在");
    if (create) {
      db.update("insert into summer_platform_user_group(id,name,description) values (?,?,?)",
          id, name, description == null ? "" : description);
    } else {
      db.update("update summer_platform_user_group set name=?,description=coalesce(?,description) where id=?",
          name, description, id);
    }
  }

  private void replaceRoles(String id, List<Role> selected) {
    List<Role> roles = selected == null ? List.of() : selected;
    var eligibleRoles = new HashSet<>(db.query(ROLES + " and (r.status='ACTIVE' or exists (select 1 from summer_platform_group_role g where g.group_id=? and g.role_id=r.role_id))",
        (rs,i) -> rs.getString(1), id));
    if (!roles.stream().allMatch(r -> eligibleRoles.contains(r.roleId()))) throw invalid("请选择有效的平台角色");
    db.update("delete from summer_platform_group_role where group_id=?", id);
    for (Role role : new LinkedHashSet<>(roles))
      db.update("insert into summer_platform_group_role(group_id,role_id) values (?,?)", id, role.roleId());
  }

  public void delete(Authentication actor, String condition, String id) {
    long version = directory.authorize(actor,condition,true);
    requireGroup(id);
    db.update("delete from summer_platform_group_administrator where group_id=?", id);
    db.update("delete from summer_platform_group_role where group_id=?", id);
    db.update("delete from summer_platform_user_group where id=?",id);
    directory.changed(actor,id,"PLATFORM_GROUP_DELETED",version);
  }

  private void requireGroup(String id) {
    if (db.queryForObject("select count(*) from summer_platform_user_group where id=?",Integer.class,id) == 0)
      throw SummerException.withMessage(PermissionErrorCode.NOT_FOUND,"用户组不存在");
  }

  private static SummerException invalid(String message) {
    return SummerException.withMessage(PermissionErrorCode.INVALID,message);
  }
}
