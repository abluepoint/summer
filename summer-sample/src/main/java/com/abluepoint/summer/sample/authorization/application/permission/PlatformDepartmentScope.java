package com.abluepoint.summer.sample.authorization.application.permission;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** Resolves the current tree and the DATA grants attached to the requested interface. */
final class PlatformDepartmentScope {
  static final String DOMAIN = "PLATFORM:platform";

  record Department(String id, String parentId, String name, int sortOrder, boolean enabled) {}

  record Range(boolean all, Set<String> ids) {
    boolean includes(String id) {
      return all || id != null && ids.contains(id);
    }

    void require(String id) {
      if (!includes(id)) throw new AccessDeniedException("部门超出授权范围");
    }
  }

  final Map<String, Department> tree = new LinkedHashMap<>();
  private final JdbcTemplate db;

  PlatformDepartmentScope(JdbcTemplate db) {
    this.db = db;
    for (var node :
        db.query(
            "select * from summer_platform_department order by sort_order,name,id",
            (r, i) ->
                new Department(
                    r.getString("id"),
                    r.getString("parent_id"),
                    r.getString("name"),
                    r.getInt("sort_order"),
                    r.getBoolean("enabled")))) tree.put(node.id(), node);
  }

  boolean under(String id, String ancestor) {
    for (String at = id; at != null && tree.containsKey(at); at = tree.get(at).parentId())
      if (at.equals(ancestor)) return true;
    return false;
  }

  Range range(String actor, String resource) {
    var rows =
        db.queryForList(
            """
            select distinct s.scope_preset, dep.department_id
            from %s u
            join summer_authorization_role r on r.role_id=u.role_id and r.status='ACTIVE'
            join summer_role_permission rp on rp.role_id=u.role_id and rp.domain_key=u.domain_key
            join summer_permission p on p.domain_key=rp.domain_key and p.permission_id=rp.permission_id and p.status='ACTIVE'
            join summer_permission_data_scope s on s.domain_key=p.domain_key and s.permission_id=p.permission_id
            join summer_permission_data_interface di on di.domain_key=s.domain_key and di.permission_id=s.permission_id
            join summer_permission_resource x on x.domain_key=di.domain_key and x.permission_id=di.interface_permission_id
            join summer_permission ip on ip.domain_key=x.domain_key and ip.permission_id=x.permission_id and ip.status='ACTIVE'
            left join summer_permission_selected_department dep on dep.domain_key=s.domain_key and dep.permission_id=s.permission_id
            where u.domain_key=? and u.username=? and x.resource_type='INTERFACE' and x.resource_id=?
            and exists (select 1 from %s ui join summer_authorization_role ri
              on ri.role_id=ui.role_id and ri.status='ACTIVE' join summer_role_permission pi
              on pi.domain_key=ui.domain_key and pi.role_id=ui.role_id where ui.username=u.username
              and ui.domain_key=u.domain_key and pi.permission_id=ip.permission_id)
            """.formatted(PlatformUserGroups.EFFECTIVE_ROLES, PlatformUserGroups.EFFECTIVE_ROLES),
            DOMAIN,
            actor,
            resource);
    Set<String> ids = new HashSet<>();
    var primary =
        db.queryForList(
            "select department_id from summer_platform_membership where username=? and"
                + " primary_department=true",
            String.class,
            actor);
    for (var row : rows) {
      String preset = row.get("scope_preset").toString();
      if ("PLATFORM_ALL_DEPARTMENTS".equals(preset)) return new Range(true, Set.of());
      String root =
          switch (preset) {
            case "PLATFORM_SPECIFIED_SUBTREES" -> (String) row.get("department_id");
            case "PLATFORM_PRIMARY_SUBTREE" -> primary.isEmpty() ? null : primary.getFirst();
            default -> null;
          };
      if (root != null) for (var id : tree.keySet()) if (under(id, root)) ids.add(id);
    }
    return new Range(false, ids);
  }
}
