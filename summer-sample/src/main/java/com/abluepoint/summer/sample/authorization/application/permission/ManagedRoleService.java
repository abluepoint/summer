package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Role writes share the permission domain lock and persistent receipt protocol. */
@Service
public class ManagedRoleService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;

  public ManagedRoleService(
      JdbcTemplate db,
      ObjectMapper json,
      AuthorizationAuditStore audit,
      AuthorizationChangeEventService events) {
    this.db = db;
    this.json = json;
    this.audit = audit;
    this.events = events;
  }

  private static SummerException fail(PermissionErrorCode c, String m) {
    return SummerException.withMessage(c, m);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(Domain d, Map<String, String> query) {
    if (!Set.of("page", "size").containsAll(query.keySet()))
      throw fail(PermissionErrorCode.INVALID, "Unsupported role query");
    int page = pageNumber(query.getOrDefault("page", "1")),
        size = pageNumber(query.getOrDefault("size", "20"));
    if (page < 1 || size < 1 || size > 200)
      throw fail(PermissionErrorCode.INVALID, "Invalid page or size");
    long total =
        db.queryForObject(
            "select count(*) from summer_permission_role where domain_key=?", Long.class, d.key());
    var items =
        db
            .queryForList(
                "select r.role_id from summer_authorization_role r join summer_permission_role p on"
                    + " p.role_id=r.role_id where p.domain_key=? order by r.role_id limit ? offset"
                    + " ?",
                d.key(),
                size,
                (long) (page - 1) * size)
            .stream()
            .map(row -> role(d, row.get("role_id").toString()))
            .toList();
    return Map.of("items", items, "total", total, "page", page, "size", size);
  }

  private static int pageNumber(String value) {
    try {
      if (!value.matches("[0-9]{1,10}")) throw new NumberFormatException();
      return Integer.parseInt(value);
    } catch (NumberFormatException invalid) {
      throw fail(PermissionErrorCode.INVALID, "Invalid page or size");
    }
  }

  public Map<String, Object> role(Domain d, String id) {
    var rows =
        db.queryForList(
            "select r.* from summer_authorization_role r join summer_permission_role p on"
                + " p.role_id=r.role_id where p.domain_key=? and r.role_id=?",
            d.key(),
            id);
    if (rows.isEmpty()) throw fail(PermissionErrorCode.NOT_FOUND, "Role not found");
    var r = rows.getFirst();
    var out = new LinkedHashMap<String, Object>();
    out.put("roleId", id);
    out.put("name", r.get("name"));
    out.put("code", r.get("code").toString().substring(d.kind().length() + 1));
    out.put("status", r.get("status"));
    out.put("version", r.get("version"));
    out.put(
        "permissionIds",
        db.queryForList(
            "select permission_id from summer_role_permission where domain_key=? and role_id=?"
                + " order by permission_id",
            String.class,
            d.key(),
            id));
    return out;
  }

  public Map<String, Object> user(Domain d, String username) {
    var v =
        db.queryForList(
            "select version from summer_permission_user where domain_key=? and username=?",
            Long.class,
            d.key(),
            username);
    var roles =
        db.queryForList(
            "select role_id from summer_permission_user_role where domain_key=? and username=?"
                + " order by role_id",
            String.class,
            d.key(),
            username);
    var sources =
        db.queryForList(
            "select p.permission_id as permissionId,p.name as permissionName,r.role_id as"
                + " roleId,r.name as roleName from summer_permission_user_role u join"
                + " summer_authorization_role r on r.role_id=u.role_id join summer_role_permission"
                + " x on x.domain_key=u.domain_key and x.role_id=u.role_id join summer_permission p"
                + " on p.domain_key=x.domain_key and p.permission_id=x.permission_id where"
                + " u.domain_key=? and u.username=? and r.status='ACTIVE' and p.status='ACTIVE'"
                + " order by p.permission_id,r.role_id",
            d.key(),
            username);
    return Map.of(
        "username",
        username,
        "roleIds",
        roles,
        "sources",
        sources,
        "version",
        v.isEmpty() ? 0L : v.getFirst());
  }

  private static List<String> ids(Object value) {
    if (!(value instanceof List<?> list)
        || list.size() > 10000
        || list.stream().anyMatch(x -> !(x instanceof String s) || s.isBlank() || s.length() > 128))
      throw fail(PermissionErrorCode.INVALID, "An explicit ID list is required");
    return list.stream().map(Object::toString).distinct().sorted().toList();
  }

  private static String text(Object value, int max) {
    if (!(value instanceof String s) || s.isBlank() || s.length() > max)
      throw fail(PermissionErrorCode.INVALID, "Invalid role text");
    return s.trim();
  }

  private static void condition(String h, long v) {
    if (h == null)
      throw SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.PERMISSION_CONDITION_REQUIRED,
          "If-Match is required");
    if (!h.matches("\"[0-9]+\"")) throw fail(PermissionErrorCode.INVALID, "Invalid If-Match");
    if (!h.equals("\"" + v + "\""))
      throw SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.PERMISSION_STALE,
          "The resource has changed");
  }

  @Transactional
  public PermissionService.Result mutate(
      Domain d,
      String actor,
      String target,
      String id,
      String method,
      Map<String, Object> body,
      String etag,
      String key,
      String requestId) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
      throw fail(PermissionErrorCode.INVALID, "Idempotency-Key required");
    var locked =
        db.queryForList(
            "select engine_mode from summer_authorization_domain where domain_key=? for update",
            d.key());
    if (locked.isEmpty() || !"MANAGED".equals(locked.getFirst().get("engine_mode")))
      throw fail(PermissionErrorCode.CONFLICT, "Managed domain required");
    String operation = "roles:" + target + ":" + method + ":" + (id == null ? "collection" : id);
    String fp;
    try {
      fp =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          json.writeValueAsString(
                                  Arrays.asList(method, id, new TreeMap<>(body), etag))
                              .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    var prior =
        db.queryForList(
            "select * from summer_permission_mutation where domain_key=? and actor=? and"
                + " operation_key=? and idempotency_key=?",
            d.key(),
            actor,
            operation,
            key);
    if (!prior.isEmpty()) {
      var p = prior.getFirst();
      if (!fp.equals(p.get("fingerprint")))
        throw fail(PermissionErrorCode.CONFLICT, "Idempotency key reused");
      return new PermissionService.Result(
          json.readValue(p.get("response_json").toString(), Map.class),
          ((Number) p.get("response_status")).intValue(),
          ((Number) p.get("response_version")).longValue());
    }
    boolean create = method.equals("POST");
    long version;
    Map<String, Object> result;
    try {
      if (target.equals("user")) {
        if (!body.keySet().equals(Set.of("roleIds")))
          throw fail(PermissionErrorCode.INVALID, "Only roleIds accepted");
        if (db.queryForObject(
                "select count(*) from summer_auth_user where username=? and active=true",
                Integer.class,
                id)
            == 0) throw fail(PermissionErrorCode.INVALID, "Active login identity required");
        var values = ids(body.get("roleIds"));
        if (d.kind().equals("ENTERPRISE")
            && db.queryForObject(
                    "select count(*) from summer_enterprise_member where enterprise_id=? and"
                        + " subject=?" + (values.isEmpty() ? "" : " and status='ACTIVE'"),
                    Integer.class,
                    d.id(),
                    id)
                == 0) throw fail(PermissionErrorCode.INVALID, "Active target membership required");
        var old = user(d, id);
        version = ((Number) old.get("version")).longValue();
        condition(etag, version);
        for (String role : values)
          if (!"ACTIVE".equals(role(d, role).get("status")))
            throw fail(PermissionErrorCode.INVALID, "Inactive role");
        db.update(
            "insert into summer_permission_user values (?,?,?) on duplicate key update"
                + " version=values(version)",
            d.key(),
            id,
            ++version);
        db.update(
            "delete from summer_permission_user_role where domain_key=? and username=?",
            d.key(),
            id);
        for (String role : values)
          db.update("insert into summer_permission_user_role values (?,?,?)", d.key(), id, role);
        result = user(d, id);
      } else {
        Set<String> allowed =
            target.equals("permissions")
                ? Set.of("permissionIds")
                : create ? Set.of("name", "code", "permissionIds") : Set.of("name", "status");
        if (!allowed.containsAll(body.keySet())
            || target.equals("permissions") && !body.keySet().equals(Set.of("permissionIds"))
            || !create && body.isEmpty())
          throw fail(PermissionErrorCode.INVALID, "Explicit supported role fields required");
        if (create) {
          id = UUID.randomUUID().toString();
          version = 1;
          db.update(
              "insert into"
                  + " summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at)"
                  + " values"
                  + " (?,?,?,?,false,'ACTIVE',1,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
              id,
              d.id(),
              text(body.get("name"), 200),
              d.kind() + ":" + text(body.get("code"), 96));
          db.update("insert into summer_permission_role values (?,?)", d.key(), id);
        } else {
          var old = role(d, id);
          version = ((Number) old.get("version")).longValue();
          condition(etag, version);
          String status =
              body.containsKey("status")
                  ? text(body.get("status"), 16)
                  : old.get("status").toString();
          if (!Set.of("ACTIVE", "DISABLED").contains(status))
            throw fail(PermissionErrorCode.INVALID, "Invalid role status");
          db.update(
              "update summer_authorization_role set"
                  + " name=?,status=?,version=?,updated_at=CURRENT_TIMESTAMP(6) where role_id=?",
              body.containsKey("name") ? text(body.get("name"), 200) : old.get("name"),
              status,
              ++version,
              id);
        }
        if (create || target.equals("permissions")) {
          var values = ids(body.getOrDefault("permissionIds", List.of()));
          for (String permission : values)
            if (db.queryForObject(
                    "select count(*) from summer_permission where domain_key=? and permission_id=?",
                    Integer.class,
                    d.key(),
                    permission)
                == 0) throw fail(PermissionErrorCode.INVALID, "Unknown permission in domain");
          db.update(
              "delete from summer_role_permission where domain_key=? and role_id=?", d.key(), id);
          for (String permission : values)
            db.update("insert into summer_role_permission values (?,?,?)", d.key(), id, permission);
        }
        result = role(d, id);
      }
    } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
      throw fail(PermissionErrorCode.CONFLICT, "Persistent role conflict");
    }
    audit.mutation(d.key(), id, "ROLE_" + target + "_" + method, requestId, actor);
    events.enqueue(d, "ROLE", id, "ROLE_CHANGED", version, requestId);
    int status = create ? 201 : 200;
    db.update(
        "insert into summer_permission_mutation values (?,?,?,?,?,?,?,?)",
        d.key(),
        actor,
        operation,
        key,
        fp,
        json.writeValueAsString(result),
        status,
        version);
    return new PermissionService.Result(result, status, version);
  }
}
