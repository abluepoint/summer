package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.data.PermissionDataDetails;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * All graph writes take the domain row first, then current rows. No JVM lock or RR snapshot checks.
 */
@Service
public class PermissionService {
    private final JdbcTemplate db;
    private final PermissionDataDetails dataDetails;
    private final ObjectMapper json;
    private final AuthorizationAuditStore audit;
    private final AuthorizationChangeEventService events;

    public PermissionService(
            JdbcTemplate db,
            ObjectMapper json,
            AuthorizationAuditStore audit,
            AuthorizationChangeEventService events,
            PermissionDataDetails dataDetails) {
        this.dataDetails = dataDetails;
        this.db = db;
        this.json = json;
        this.audit = audit;
        this.events = events;
    }

    public record Result(Object body, int status, long version) {}

    @Transactional(readOnly = true)
    public Result getDataScope(Domain d, String id) {
        return dataScopeResult(get(d, false, id));
    }

    @Transactional
    public Result replaceDataScope(
            Domain d,
            String actor,
            String id,
            Map<String, Object> body,
            String condition,
            String key,
            String requestId) {
        return dataScopeResult(
                mutate(
                        d,
                        actor,
                        false,
                        id,
                        "PATCH",
                        Map.of("dataScope", body),
                        condition,
                        key,
                        requestId));
    }

    private Result dataScopeResult(Result permission) {
        if (!(permission.body() instanceof Map<?, ?> body) || !"DATA".equals(body.get("type")))
            throw fail(400, "DATA permission required");
        return new Result(body.get("dataScope"), permission.status(), permission.version());
    }

    private static com.abluepoint.summer.common.error.SummerException fail(int s, String m) {
        return com.abluepoint.summer.common.error.SummerException.withMessage(
                switch (s) {
                    case 400 -> PermissionErrorCode.INVALID;
                    case 404 -> PermissionErrorCode.NOT_FOUND;
                    case 409 -> PermissionErrorCode.CONFLICT;
                    case 412 ->
                            com.abluepoint.summer.sample.error.SampleTransportErrorCode
                                    .PERMISSION_STALE;
                    case 428 ->
                            com.abluepoint.summer.sample.error.SampleTransportErrorCode
                                    .PERMISSION_CONDITION_REQUIRED;
                    default -> throw new IllegalArgumentException("Unsupported permission failure");
                },
                m);
    }

    private static String text(Map<String, Object> m, String key, int max, boolean required) {
        Object v = m.get(key);
        if (v == null) {
            if (required) throw fail(400, key + " is required");
            return null;
        }
        if (!(v instanceof String s) || s.isBlank() || s.length() > max)
            throw fail(400, "Invalid " + key);
        return s.trim();
    }

    private static int number(Object v) {
        if (!(v instanceof Number n) || n.doubleValue() != n.intValue())
            throw fail(400, "sortOrder must be an integer");
        return n.intValue();
    }

    private static void fields(Map<String, Object> m, Set<String> allowed) {
        if (m == null || !allowed.containsAll(m.keySet()))
            throw fail(400, "Unsupported input fields");
    }

    private static void etag(String h, long version) {
        if (h == null || h.isBlank()) throw fail(428, "If-Match is required");
        if (!h.matches("\"[0-9]+\"")) throw fail(400, "Invalid If-Match");
        if (!h.equals("\"" + version + "\"")) throw fail(412, "The resource has changed");
    }

    private List<Map<String, Object>> rows(Domain d, boolean group) {
        return db.queryForList(
                "select * from "
                        + (group ? "summer_permission_group" : "summer_permission")
                        + " where domain_key=?",
                d.key());
    }

    private Map<String, Object> row(Domain d, boolean group, String id) {
        var list =
                db.queryForList(
                        "select * from "
                                + (group ? "summer_permission_group" : "summer_permission")
                                + " where domain_key=? and "
                                + (group ? "group_id" : "permission_id")
                                + "=?",
                        d.key(),
                        id);
        if (list.isEmpty()) throw fail(404, "Resource not found");
        return list.getFirst();
    }

    private Map<String, Object> view(Map<String, Object> r, boolean g) {
        var v = new LinkedHashMap<String, Object>();
        v.put(g ? "groupId" : "permissionId", r.get(g ? "group_id" : "permission_id"));
        v.put("name", r.get("name"));
        v.put("version", ((Number) r.get("version")).longValue());
        if (g) {
            v.put("parentGroupId", r.get("parent_group_id"));
            v.put("sortOrder", r.get("sort_order"));
        } else {
            for (String k : List.of("code", "description", "type", "status")) v.put(k, r.get(k));
            v.put("accessMode", r.get("access_mode"));
            v.put("groupId", r.get("group_id"));
            if ("DATA".equals(r.get("type")))
                v.put(
                        "dataScope",
                        dataDetails.get(
                                new Domain(
                                        r.get("domain_key").toString().split(":", 2)[0],
                                        r.get("domain_key").toString().split(":", 2)[1]),
                                r.get("permission_id").toString()));
        }
        return v;
    }

    @Transactional(readOnly = true)
    public Object list(Domain d, boolean group, Map<String, String> q) {
        if (!Set.of(
                        "page",
                        "size",
                        "name",
                        "type",
                        "status",
                        "accessMode",
                        "groupId",
                        "includeDescendants")
                .containsAll(q.keySet())) throw fail(400, "Unsupported query");
        int page = integer(q.getOrDefault("page", "1")),
                size = integer(q.getOrDefault("size", "20"));
        if (page < 1 || size < 1 || size > 200) throw fail(400, "Invalid page or size");
        if (q.containsKey("includeDescendants")
                && !Set.of("true", "false").contains(q.get("includeDescendants")))
            throw fail(400, "Invalid includeDescendants");
        for (String k : List.of("type", "status", "accessMode"))
            if (q.containsKey(k)) choice(k, q.get(k));
        var all = rows(d, group);
        Set<String> groups = new HashSet<>();
        String selected = q.get("groupId");
        if (selected != null) {
            row(d, true, selected);
            groups.add(selected);
            if ("true".equals(q.get("includeDescendants"))) {
                boolean added;
                do {
                    added = false;
                    for (var r : rows(d, true))
                        if (groups.contains(r.get("parent_group_id")))
                            added |= groups.add(r.get("group_id").toString());
                } while (added);
            }
        }
        var filtered =
                all.stream()
                        .filter(
                                r ->
                                        !q.containsKey("name")
                                                || r.get("name")
                                                        .toString()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(
                                                                q.get("name")
                                                                        .toLowerCase(Locale.ROOT)))
                        .filter(
                                r ->
                                        group
                                                || ((selected == null
                                                                || groups.contains(
                                                                        r.get("group_id")))
                                                        && List.of("type", "status", "accessMode")
                                                                .stream()
                                                                .allMatch(
                                                                        k ->
                                                                                !q.containsKey(k)
                                                                                        || q.get(k)
                                                                                                .equals(
                                                                                                        r
                                                                                                                .get(
                                                                                                                        k
                                                                                                                                        .equals(
                                                                                                                                                "accessMode")
                                                                                                                                ? "access_mode"
                                                                                                                                : k)))))
                        .sorted(
                                Comparator.<Map<String, Object>, Integer>comparing(
                                                r ->
                                                        group
                                                                ? ((Number) r.get("sort_order"))
                                                                        .intValue()
                                                                : 0)
                                        .thenComparing(
                                                r ->
                                                        r.get(group ? "group_id" : "permission_id")
                                                                .toString()))
                        .toList();
        long offset = (long) (page - 1) * size;
        return Map.of(
                "items",
                filtered.stream().skip(offset).limit(size).map(r -> view(r, group)).toList(),
                "total",
                filtered.size(),
                "page",
                page,
                "size",
                size);
    }

    private static int integer(String s) {
        try {
            return Integer.parseInt(s);
        } catch (RuntimeException e) {
            throw fail(400, "Invalid integer");
        }
    }

    @Transactional(readOnly = true)
    public Result get(Domain d, boolean group, String id) {
        var r = view(row(d, group, id), group);
        return new Result(r, 200, ((Number) r.get("version")).longValue());
    }

    private static String choice(String key, String value) {
        Set<String> values =
                switch (key) {
                    case "type" -> Set.of("INTERFACE", "MENU", "DATA");
                    case "accessMode" -> Set.of("PUBLIC", "AUTHENTICATED", "AUTHORIZED");
                    default -> Set.of("ACTIVE", "DISABLED");
                };
        if (!values.contains(value)) throw fail(400, "Invalid " + key);
        return value;
    }

    private Object canonical(Object value) {
        if (value instanceof Map<?, ?> m) {
            var result = new TreeMap<String, Object>();
            m.forEach((k, v) -> result.put(k.toString(), canonical(v)));
            return result;
        }
        if (value instanceof List<?> l) return l.stream().map(this::canonical).toList();
        return value;
    }

    private String fingerprint(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            json.writeValueAsString(
                                                            canonical(Arrays.asList(values)))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public Result mutate(
            Domain d,
            String actor,
            boolean group,
            String id,
            String method,
            Map<String, Object> body,
            String condition,
            String key,
            String requestId) {
        try {
            return performMutation(d, actor, group, id, method, body, condition, key, requestId);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw com.abluepoint.summer.common.error.SummerException.withMessage(
                    PermissionErrorCode.CONFLICT,
                    "Resource is still referenced or conflicts with a persistent constraint",
                    e);
        }
    }

    private Result performMutation(
            Domain d,
            String actor,
            boolean group,
            String id,
            String method,
            Map<String, Object> body,
            String condition,
            String key,
            String requestId) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw fail(400, "Idempotency-Key is required (1-128 safe characters)");
        // This shared lock protocol is also the serialization point for later association writers.
        db.update(
                "insert into summer_authorization_domain(domain_key,engine_mode,revision) values"
                    + " (?,'LEGACY',0) on duplicate key update domain_key=domain_key",
                d.key());
        db.queryForList(
                "select revision from summer_authorization_domain where domain_key=? for update",
                d.key());
        String operation =
                (group ? "groups" : "permissions")
                        + ":"
                        + method
                        + ":"
                        + (id == null ? "collection" : id);
        String fp = fingerprint(method, id, body, condition);
        var prior =
                db.queryForList(
                        "select * from summer_permission_mutation where domain_key=? and actor=?"
                            + " and operation_key=? and idempotency_key=?",
                        d.key(),
                        actor,
                        operation,
                        key);
        if (!prior.isEmpty()) {
            var p = prior.getFirst();
            if (!fp.equals(p.get("fingerprint")))
                throw fail(409, "Idempotency key reused with a different request");
            return new Result(
                    json.readValue(p.get("response_json").toString(), Map.class),
                    ((Number) p.get("response_status")).intValue(),
                    ((Number) p.get("response_version")).longValue());
        }
        boolean create = method.equals("POST"), delete = method.equals("DELETE");
        Map<String, Object> old = create ? null : row(d, group, id);
        long version = create ? 1 : ((Number) old.get("version")).longValue() + 1;
        if (!create) etag(condition, version - 1);
        if (create) id = UUID.randomUUID().toString();
        Map<String, Object> result;
        if (delete) {
            if (group) {
                if (db.queryForObject(
                                        "select count(*) from summer_permission_group where"
                                            + " domain_key=? and parent_group_id=?",
                                        Integer.class,
                                        d.key(),
                                        id)
                                > 0
                        || db.queryForObject(
                                        "select count(*) from summer_permission where domain_key=?"
                                            + " and group_id=?",
                                        Integer.class,
                                        d.key(),
                                        id)
                                > 0) throw fail(409, "Group is not empty");
                db.update(
                        "delete from summer_permission_group where domain_key=? and group_id=?",
                        d.key(),
                        id);
            } else {
                db.update(
                        "delete from summer_permission where domain_key=? and permission_id=?",
                        d.key(),
                        id);
            }
            result =
                    Map.of(
                            "deleted",
                            true,
                            group ? "groupId" : "permissionId",
                            id,
                            "version",
                            version);
        } else if (group) {
            fields(body, Set.of("name", "parentGroupId", "sortOrder"));
            String name =
                    body.containsKey("name")
                            ? text(body, "name", 200, true)
                            : create ? text(body, "name", 200, true) : old.get("name").toString();
            String parent =
                    body.containsKey("parentGroupId")
                            ? text(body, "parentGroupId", 96, false)
                            : create ? null : (String) old.get("parent_group_id");
            int sort =
                    body.containsKey("sortOrder")
                            ? number(body.get("sortOrder"))
                            : create ? 0 : ((Number) old.get("sort_order")).intValue();
            Set<String> visited = new HashSet<>();
            String current = parent;
            while (current != null) {
                if (current.equals(id) || !visited.add(current)) throw fail(409, "Group cycle");
                current = (String) row(d, true, current).get("parent_group_id");
            }
            if (create)
                db.update(
                        "insert into summer_permission_group values (?,?,?,?,?,?)",
                        d.key(),
                        id,
                        parent,
                        name,
                        sort,
                        version);
            else
                db.update(
                        "update summer_permission_group set"
                            + " parent_group_id=?,name=?,sort_order=?,version=? where domain_key=?"
                            + " and group_id=?",
                        parent,
                        name,
                        sort,
                        version,
                        d.key(),
                        id);
            result = view(row(d, true, id), true);
        } else {
            fields(
                    body,
                    create
                            ? Set.of(
                                    "code",
                                    "name",
                                    "description",
                                    "type",
                                    "accessMode",
                                    "groupId",
                                    "status",
                                    "dataScope")
                            : Set.of(
                                    "name",
                                    "description",
                                    "accessMode",
                                    "groupId",
                                    "status",
                                    "dataScope"));
            String name =
                    body.containsKey("name") || create
                            ? text(body, "name", 200, true)
                            : old.get("name").toString();
            String code = create ? text(body, "code", 128, true) : old.get("code").toString();
            if (!code.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw fail(400, "Invalid code");
            String type =
                    create
                            ? choice("type", text(body, "type", 16, true))
                            : old.get("type").toString();
            String mode =
                    body.containsKey("accessMode")
                            ? choice("accessMode", text(body, "accessMode", 20, true))
                            : create ? "AUTHORIZED" : old.get("access_mode").toString();
            String status =
                    body.containsKey("status")
                            ? choice("status", text(body, "status", 16, true))
                            : create ? "ACTIVE" : old.get("status").toString();
            String gid =
                    body.containsKey("groupId") || create
                            ? text(body, "groupId", 96, true)
                            : old.get("group_id").toString();
            row(d, true, gid);
            String description =
                    body.containsKey("description")
                            ? text(body, "description", 2000, false)
                            : create ? null : (String) old.get("description");
            Map<String, Object> detail =
                    body.containsKey("dataScope")
                            ? dataDetails.validate(body.get("dataScope"))
                            : create ? null : type.equals("DATA") ? dataDetails.get(d, id) : null;
            if (!type.equals("DATA") && body.containsKey("dataScope"))
                throw fail(400, "Only DATA permissions have dataScope");
            if (type.equals("DATA") && status.equals("ACTIVE") && detail == null)
                throw fail(400, "Enabled DATA requires complete details");
            if (!create
                    && db.queryForObject(
                                    "select count(*) from summer_permission_resource mine join"
                                        + " summer_permission_resource other on"
                                        + " other.domain_key=mine.domain_key and"
                                        + " other.resource_type=mine.resource_type and"
                                        + " other.resource_id=mine.resource_id join"
                                        + " summer_permission p on p.domain_key=other.domain_key"
                                        + " and p.permission_id=other.permission_id where"
                                        + " mine.domain_key=? and mine.permission_id=? and"
                                        + " p.permission_id<>? and p.access_mode<>?",
                                    Integer.class,
                                    d.key(),
                                    id,
                                    id,
                                    mode)
                            > 0)
                throw fail(409, "All permissions of a resource must retain the same access mode");
            if (create) {
                if (db.queryForObject(
                                "select count(*) from summer_permission_identity where domain_key=?"
                                    + " and code=?",
                                Integer.class,
                                d.key(),
                                code)
                        > 0) throw fail(409, "Permission code already used");
                db.update(
                        "insert into summer_permission_identity values (?,?,?,?)",
                        d.key(),
                        id,
                        code,
                        type);
                db.update(
                        "insert into"
                            + " summer_permission(domain_key,permission_id,code,name,description,type,access_mode,group_id,status,version)"
                            + " values (?,?,?,?,?,?,?,?,?,?)",
                        d.key(),
                        id,
                        code,
                        name,
                        description,
                        type,
                        mode,
                        gid,
                        status,
                        version);
            } else
                db.update(
                        "update summer_permission set"
                            + " name=?,description=?,access_mode=?,group_id=?,status=?,version=?"
                            + " where domain_key=? and permission_id=?",
                        name,
                        description,
                        mode,
                        gid,
                        status,
                        version,
                        d.key(),
                        id);
            if (detail != null) dataDetails.replace(d, id, detail);
            result = view(row(d, false, id), false);
        }
        audit.mutation(
                d.key(), id, "PERMISSION_" + (group ? "GROUP_" : "") + method, requestId, actor);
        events.enqueue(
                d,
                group ? "PERMISSION_GROUP" : "PERMISSION",
                id,
                "PERMISSION_MANAGEMENT_CHANGED",
                version,
                requestId);
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
        return new Result(result, status, version);
    }
}
