package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.*;

/** The registered application write adapter; client fields never set tenant or safety bounds. */
@Service
public class ApplicationOperationService {
    private final DataOperationExecutor operations;
    private final NamedParameterJdbcTemplate db;
    private final ManagedPermissionAccess access;

    public ApplicationOperationService(
            DataOperationExecutor operations,
            NamedParameterJdbcTemplate db,
            ManagedPermissionAccess access) {
        this.operations = operations;
        this.db = db;
        this.access = access;
    }

    public Object create(
            Domain domain, String resource, Authentication actor, Map<String, Object> body) {
        requireAdapter(resource);
        if (!Set.of("recordId", "note", "applicantId", "departmentId").containsAll(body.keySet()))
            throw invalid("Unknown creation field");
        String id = text(body, "recordId", 96);
        String note = text(body, "note", 200);
        return operations.write(
                domain,
                resource,
                "create",
                actor,
                scope -> {
                    String applicant =
                            body.containsKey("applicantId")
                                    ? text(body, "applicantId", 128)
                                    : actor.getName();
                    var subject =
                            UsernamePasswordAuthenticationToken.authenticated(
                                    applicant, "", List.of());
                    if (!access.active(domain, subject)) throw invalid("Active applicant required");
                    String department =
                            body.containsKey("departmentId")
                                    ? text(body, "departmentId", 96)
                                    : null;
                    if (department != null
                            && (!domain.kind().equals("ENTERPRISE")
                                    || db.getJdbcTemplate()
                                                    .queryForObject(
                                                            "select count(*) from"
                                                                + " summer_organization_membership"
                                                                + " m join summer_enterprise_member"
                                                                + " e on"
                                                                + " e.enterprise_id=m.enterprise_id"
                                                                + " and e.member_id=m.member_id"
                                                                + " join summer_organization_unit u"
                                                                + " on u.enterprise_id=m.enterprise_id"
                                                                + " and u.unit_id=m.organization_id"
                                                                + " where m.enterprise_id=? and"
                                                                + " e.subject=? and"
                                                                + " e.status='ACTIVE' and"
                                                                + " u.unit_id=? and"
                                                                + " u.status='ACTIVE'",
                                                            Integer.class,
                                                            domain.id(),
                                                            applicant,
                                                            department)
                                            == 0))
                        throw invalid("Applicant department not active in this tenant");
                    var b = scope.bindings();
                    b.put("id", id);
                    b.put("tenant", domain.id());
                    b.put("domain", domain.key());
                    b.put("applicant", applicant);
                    b.put("creator", actor.getName());
                    b.put("department", department);
                    b.put("note", note);
                    try {
                        int changed =
                                db.update(
                                        "insert into"
                                            + " summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,department_id,business_visible,note,record_version)"
                                            + " select"
                                            + " :id,:domain,:tenant,:applicant,:creator,:department,true,:note,0"
                                            + " where exists (select 1 from (select cast(:domain as"
                                            + " char(96)) as domain_key,cast(:tenant as char(80))"
                                            + " as tenant_id,cast(:applicant as char(128)) as"
                                            + " applicant_id,cast(:department as char(96)) as"
                                            + " department_id,true as business_visible) candidate"
                                            + " where "
                                                + scope.predicate()
                                                + ")",
                                        b);
                        if (changed != 1)
                            throw new AccessDeniedException("Creation outside current range");
                    } catch (DuplicateKeyException duplicate) {
                        throw SummerException.withMessage(
                                PermissionErrorCode.CONFLICT, "Record already exists");
                    }
                    return Map.of("recordId", id, "version", 0);
                });
    }

    public Object update(
            Domain domain,
            String resource,
            Authentication actor,
            String id,
            Map<String, Object> body) {
        if (!body.keySet().equals(Set.of("version", "note")))
            throw invalid("Update requires version and note");
        var item = new HashMap<>(body);
        item.put("recordId", id);
        return mutate(domain, resource, actor, List.of(item), false).getFirst();
    }

    public Object delete(
            Domain domain, String resource, Authentication actor, String id, long version) {
        return mutate(
                        domain,
                        resource,
                        actor,
                        List.of(Map.of("recordId", id, "version", version)),
                        true)
                .getFirst();
    }

    public Object batch(
            Domain domain,
            String resource,
            Authentication actor,
            Map<String, Object> body,
            boolean delete) {
        if (!body.keySet().equals(Set.of("items")) || !(body.get("items") instanceof List<?> items))
            throw invalid("Batch requires items");
        var targets = new ArrayList<Map<String, Object>>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> fields)
                    || fields.keySet().stream().anyMatch(k -> !(k instanceof String)))
                throw invalid("Invalid batch item");
            var target = new HashMap<String, Object>();
            fields.forEach((k, v) -> target.put((String) k, v));
            targets.add(target);
        }
        return Map.of("items", mutate(domain, resource, actor, targets, delete));
    }

    private List<Map<String, Object>> mutate(
            Domain domain,
            String resource,
            Authentication actor,
            List<Map<String, Object>> items,
            boolean delete) {
        requireAdapter(resource);
        if (items.isEmpty() || items.size() > 100)
            throw invalid("Batch size must be between 1 and 100");
        Set<String> ids = new HashSet<>();
        for (var item : items) {
            if (!item.keySet()
                    .equals(
                            delete
                                    ? Set.of("recordId", "version")
                                    : Set.of("recordId", "version", "note")))
                throw invalid("Invalid mutation fields");
            if (!ids.add(text(item, "recordId", 96))) throw invalid("Duplicate batch record");
            version(item.get("version"));
            if (!delete) text(item, "note", 200);
        }
        return operations.write(
                domain,
                resource,
                delete ? "delete" : "update",
                actor,
                scope -> {
                    var result = new ArrayList<Map<String, Object>>();
                    for (var item : items) {
                        var b = scope.bindings();
                        b.putAll(item);
                        String condition =
                                "record_id=:recordId and record_version=:version and ("
                                        + scope.predicate()
                                        + ")";
                        int affected =
                                db.update(
                                        delete
                                                ? "delete from summer_application_record where "
                                                        + condition
                                                : "update summer_application_record set"
                                                      + " note=:note,record_version=record_version+1"
                                                      + " where "
                                                        + condition,
                                        b);
                        if (affected != 1)
                            throw SummerException.withMessage(
                                    PermissionErrorCode.NOT_FOUND,
                                    "Record or expected version not available in current range");
                        result.add(
                                Map.of(
                                        "recordId",
                                        item.get("recordId"),
                                        "version",
                                        version(item.get("version")) + 1));
                    }
                    return result;
                });
    }

    private static long version(Object value) {
        if (!(value instanceof Number number))
            throw invalid("Nonnegative integer version required");
        try {
            long version = new java.math.BigDecimal(number.toString()).longValueExact();
            if (version < 0 || version == Long.MAX_VALUE) throw invalid("Invalid version");
            return version;
        } catch (ArithmeticException failure) {
            throw invalid("Invalid version");
        }
    }

    static void requireAdapter(String resource) {
        if (!"applications".equals(resource))
            throw invalid("Application operation adapter not registered");
    }

    static String text(Map<String, Object> body, String key, int max) {
        if (!(body.get(key) instanceof String value) || value.isBlank() || value.length() > max)
            throw invalid("Invalid " + key);
        return value;
    }

    static SummerException invalid(String message) {
        return SummerException.withMessage(PermissionErrorCode.INVALID, message);
    }
}
