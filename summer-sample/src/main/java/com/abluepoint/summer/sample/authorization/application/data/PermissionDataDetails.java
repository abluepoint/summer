package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** Called inside the permission aggregate's domain-locked mutation transaction. */
@Service
public class PermissionDataDetails {
    private final JdbcTemplate db;
    private final DataResourceRegistry registry;

    public PermissionDataDetails(JdbcTemplate db, DataResourceRegistry registry) {
        this.db = db;
        this.registry = registry;
    }

    public Map<String, Object> get(Domain d, String id) {
        var rows =
                db.queryForList(
                        "select data_resource_key,action,scope_preset from"
                            + " summer_permission_data_scope where domain_key=? and"
                            + " permission_id=?",
                        d.key(),
                        id);
        if (rows.isEmpty()) return null;
        var r = rows.getFirst();
        return Map.of(
                "dataResourceKey",
                r.get("data_resource_key"),
                "action",
                r.get("action"),
                "scopePreset",
                r.get("scope_preset"));
    }

    public Map<String, Object> validate(Object value) {
        if (!(value instanceof Map<?, ?> r)
                || !r.keySet().equals(Set.of("dataResourceKey", "action", "scopePreset"))
                || !(r.get("dataResourceKey") instanceof String key)
                || !(r.get("action") instanceof String action)
                || !(r.get("scopePreset") instanceof String preset)
                || !Set.of("SELF", "TENANT_ALL").contains(preset))
            throw SummerException.withMessage(
                    PermissionErrorCode.INVALID,
                    "One registered resource/action and one supported scope preset are required");
        registry.require(key, action);
        return Map.of("dataResourceKey", key, "action", action, "scopePreset", preset);
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void replace(Domain d, String id, Map<String, Object> r) {
        db.update(
                "delete from summer_permission_data_scope where domain_key=? and permission_id=?",
                d.key(),
                id);
        db.update(
                "insert into"
                    + " summer_permission_data_scope(domain_key,permission_id,data_resource_key,action,scope_preset)"
                    + " values(?,?,?,?,?)",
                d.key(),
                id,
                r.get("dataResourceKey"),
                r.get("action"),
                r.get("scopePreset"));
    }
}
