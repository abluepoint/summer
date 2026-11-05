package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.authorization.infrastructure.data.ApplicationRecordRepository;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trusted application adapters consume one current authorization range before pagination. */
@Service
public class ApplicationQueryService {
    private static final String PROJECTION =
            "select record_id,applicant_id,created_by,department_id from summer_application_record"
                    + " where ";
    private static final String OPERATION_PROJECTION =
            "select record_id,applicant_id,created_by,department_id,note,record_version from"
                + " summer_application_record where ";
    private final DataReadService reads;
    private final NamedParameterJdbcTemplate db;
    private final ApplicationRecordRepository records;
    private final ManagedPermissionAccess access;
    private final VersionedTwoLevelCache cache;

    public ApplicationQueryService(
            DataReadService reads,
            NamedParameterJdbcTemplate db,
            ApplicationRecordRepository records,
            ManagedPermissionAccess access,
            VersionedTwoLevelCache cache) {
        this.reads = reads;
        this.db = db;
        this.records = records;
        this.access = access;
        this.cache = cache;
    }

    public Object list(
            Domain domain, String resource, Authentication actor, int page, int size, boolean jpa) {
        requireAdapter(resource);
        if (page < 1 || size < 1 || size > 100) throw invalid("Invalid page or size");
        return reads.read(
                domain,
                resource,
                "read",
                actor,
                scope -> {
                    if (jpa) {
                        var rows =
                                records.findAll(
                                        scope.specification(),
                                        PageRequest.of(page - 1, size, Sort.by("recordId")));
                        var items =
                                rows.stream()
                                        .map(
                                                x -> {
                                                    var v = new LinkedHashMap<String, Object>();
                                                    v.put("recordId", x.getRecordId());
                                                    v.put("applicantId", x.getApplicantId());
                                                    v.put("createdBy", x.getCreatedBy());
                                                    v.put("departmentId", x.getDepartmentId());
                                                    return v;
                                                })
                                        .toList();
                        return Map.of(
                                "items",
                                items,
                                "total",
                                rows.getTotalElements(),
                                "page",
                                page,
                                "size",
                                size);
                    }
                    var b = scope.bindings();
                    b.put("limit", size);
                    b.put("offset", ((long) page - 1) * size);
                    long total =
                            db.queryForObject(
                                    "select count(*) from summer_application_record where "
                                            + scope.predicate(),
                                    b,
                                    Long.class);
                    var items =
                            db.query(
                                    PROJECTION
                                            + scope.predicate()
                                            + " order by record_id limit :limit offset :offset",
                                    b,
                                    ApplicationQueryService::view);
                    return Map.of("items", items, "total", total, "page", page, "size", size);
                });
    }

    public Object detail(Domain domain, String resource, Authentication actor, String id) {
        requireAdapter(resource);
        return reads.read(
                domain,
                resource,
                "read",
                actor,
                scope -> {
                    var b = scope.bindings();
                    b.put("recordId", id);
                    var values =
                            db.query(
                                    PROJECTION
                                            + "record_id=:recordId and ("
                                            + scope.predicate()
                                            + ")",
                                    b,
                                    ApplicationQueryService::view);
                    if (values.isEmpty())
                        throw SummerException.withMessage(
                                PermissionErrorCode.NOT_FOUND, "Record not visible");
                    return values.getFirst();
                });
    }

    public Object preview(Domain domain, String resource, Map<String, Object> body) {
        requireAdapter(resource);
        if (!body.keySet().equals(Set.of("subject", "recordId", "action"))
                || !(body.get("subject") instanceof String subject)
                || subject.isBlank()
                || !(body.get("recordId") instanceof String id)
                || !(body.get("action") instanceof String action)) {
            throw invalid("Preview requires only a persisted subject, recordId and action");
        }
        var target = UsernamePasswordAuthenticationToken.authenticated(subject, "", List.of());
        return reads.read(
                domain,
                resource,
                action,
                target,
                scope -> {
                    // Platform subjects must exist locally; enterprise subjects can be persisted
                    // external members.
                    boolean persisted =
                            domain.kind().equals("ENTERPRISE")
                                    ? access.active(domain, target)
                                    : db.getJdbcTemplate()
                                                    .queryForObject(
                                                            "select count(*) from summer_auth_user"
                                                                    + " where username=? and"
                                                                    + " active=true",
                                                            Integer.class,
                                                            subject)
                                            > 0;
                    if (!persisted) throw invalid("Unknown active subject");
                    var b = scope.bindings();
                    b.put("recordId", id);
                    return Map.of(
                            "allowed",
                            db.queryForObject(
                                            "select count(*) from summer_application_record where"
                                                    + " record_id=:recordId and ("
                                                    + scope.predicate()
                                                    + ")",
                                            b,
                                            Long.class)
                                    > 0,
                            "revision",
                            scope.revision());
                });
    }

    public Object batchRead(
            Domain domain, String resource, Authentication actor, Map<String, Object> body) {
        requireAdapter(resource);
        if (!body.keySet().equals(Set.of("recordIds"))
                || !(body.get("recordIds") instanceof List<?> ids)
                || ids.isEmpty()
                || ids.size() > 100
                || ids.stream()
                        .anyMatch(
                                id ->
                                        !(id instanceof String value)
                                                || value.isBlank()
                                                || value.length() > 96))
            throw invalid("Batch read requires between 1 and 100 record IDs");
        return reads.read(
                domain,
                resource,
                "read",
                actor,
                scope -> {
                    var b = scope.bindings();
                    b.put("ids", ids);
                    return Map.of(
                            "items",
                            db.query(
                                    OPERATION_PROJECTION
                                            + "record_id in (:ids) and ("
                                            + scope.predicate()
                                            + ") order by record_id",
                                    b,
                                    ApplicationQueryService::operationView));
                });
    }

    public record Export(String csv, long total) {}

    public Export export(Domain domain, String resource, Authentication actor, int page, int size) {
        requireAdapter(resource);
        if (page < 1 || size < 1 || size > 1000) throw invalid("Invalid export page or size");
        return reads.read(
                domain,
                resource,
                "export",
                actor,
                scope -> {
                    var b = scope.bindings();
                    b.put("limit", size);
                    b.put("offset", ((long) page - 1) * size);
                    long total =
                            db.queryForObject(
                                    "select count(*) from summer_application_record where "
                                            + scope.predicate(),
                                    b,
                                    Long.class);
                    var rows =
                            db.query(
                                    OPERATION_PROJECTION
                                            + scope.predicate()
                                            + " order by record_id limit :limit offset :offset",
                                    b,
                                    ApplicationQueryService::operationView);
                    var csv =
                            new StringBuilder(
                                    "recordId,applicantId,createdBy,departmentId,note,version\r\n");
                    for (var row : rows) {
                        csv.append(
                                        String.join(
                                                ",",
                                                List.of(
                                                                "recordId",
                                                                "applicantId",
                                                                "createdBy",
                                                                "departmentId",
                                                                "note",
                                                                "version")
                                                        .stream()
                                                        .map(key -> csvCell(row.get(key)))
                                                        .toList()))
                                .append("\r\n");
                    }
                    return new Export(csv.toString(), total);
                });
    }

    /** Cached projections are usable only after the current scoped database read succeeds. */
    public Object cached(Domain domain, String resource, Authentication actor, String id) {
        requireAdapter(resource);
        return reads.read(
                domain,
                resource,
                "read",
                actor,
                scope -> {
                    var b = scope.bindings();
                    b.put("recordId", id);
                    var rows =
                            db.query(
                                    OPERATION_PROJECTION
                                            + "record_id=:recordId and ("
                                            + scope.predicate()
                                            + ")",
                                    b,
                                    ApplicationQueryService::operationView);
                    if (rows.isEmpty())
                        throw SummerException.withMessage(
                                PermissionErrorCode.NOT_FOUND, "Record not visible");
                    var row = rows.getFirst();
                    var current =
                            new Projection(
                                    (String) row.get("recordId"),
                                    (String) row.get("applicantId"),
                                    (String) row.get("createdBy"),
                                    (String) row.get("departmentId"),
                                    (String) row.get("note"),
                                    (Long) row.get("version"));
                    String key =
                            scope.revision()
                                    + ":"
                                    + org.springframework.util.DigestUtils.md5DigestAsHex(
                                            (scope.predicate()
                                                            + new java.util.TreeMap<>(
                                                                    scope.bindings())
                                                            + ":"
                                                            + (actor == null ? "" : actor.getName())
                                                            + ":"
                                                            + current)
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8));
                    try {
                        var cached =
                                cache.get(
                                        domain.key(),
                                        "data:applications:read",
                                        key,
                                        Projection.class,
                                        () -> current);
                        return current.equals(cached) ? cached : current;
                    } catch (RuntimeException unavailableCache) {
                        // Recovery uses only this already-authorized current projection, never an
                        // old allow.
                        return current;
                    }
                });
    }

    public record Projection(
            String recordId,
            String applicantId,
            String createdBy,
            String departmentId,
            String note,
            long version) {}

    private static String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static void requireAdapter(String resource) {
        if (!"applications".equals(resource))
            throw invalid("Application read adapter not registered for resource");
    }

    private static SummerException invalid(String message) {
        return SummerException.withMessage(PermissionErrorCode.INVALID, message);
    }

    private static Map<String, Object> operationView(ResultSet rs, int index) throws SQLException {
        var value = view(rs, index);
        value.put("note", rs.getString("note"));
        value.put("version", rs.getLong("record_version"));
        return value;
    }

    private static Map<String, Object> view(ResultSet rs, int index) throws SQLException {
        var v = new LinkedHashMap<String, Object>();
        v.put("recordId", rs.getString("record_id"));
        v.put("applicantId", rs.getString("applicant_id"));
        v.put("createdBy", rs.getString("created_by"));
        v.put("departmentId", rs.getString("department_id"));
        return v;
    }
}
