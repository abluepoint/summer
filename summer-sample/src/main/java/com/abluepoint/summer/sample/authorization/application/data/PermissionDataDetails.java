package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
    var result =
        new LinkedHashMap<String, Object>(
            Map.of(
                "dataResourceKey",
                r.get("data_resource_key"),
                "action",
                r.get("action"),
                "scopePreset",
                r.get("scope_preset")));
    if ("SPECIFIED_DEPARTMENTS".equals(r.get("scope_preset")))
      result.put(
          "departmentIds",
          db.queryForList(
              "select department_id from summer_permission_selected_department where domain_key=?"
                  + " and permission_id=? order by department_id",
              String.class,
              d.key(),
              id));
    return result;
  }

  public Map<String, Object> validate(Object value) {
    if (!(value instanceof Map<?, ?> r)
        || !Set.of("dataResourceKey", "action", "scopePreset", "departmentIds")
            .containsAll(r.keySet())
        || !(r.get("dataResourceKey") instanceof String key)
        || !(r.get("action") instanceof String action)
        || !(r.get("scopePreset") instanceof String preset)
        || !Set.of(
                "SELF",
                "TENANT_ALL",
                "DEPARTMENT",
                "DEPARTMENT_AND_DESCENDANTS",
                "SPECIFIED_DEPARTMENTS")
            .contains(preset))
      throw SummerException.withMessage(
          PermissionErrorCode.INVALID,
          "One registered resource/action and one supported scope preset are required");
    registry.require(key, action);
    var result =
        new LinkedHashMap<String, Object>(
            Map.of("dataResourceKey", key, "action", action, "scopePreset", preset));
    if (preset.equals("SPECIFIED_DEPARTMENTS")) {
      if (!(r.get("departmentIds") instanceof List<?> ids)
          || ids.isEmpty()
          || ids.stream()
              .anyMatch(id -> !(id instanceof String t) || t.isBlank() || t.length() > 64))
        throw invalid();
      result.put(
          "departmentIds",
          ids.stream().distinct().sorted((a, b) -> a.toString().compareTo(b.toString())).toList());
    } else if (r.containsKey("departmentIds")) throw invalid();
    return result;
  }

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void replace(Domain d, String id, Map<String, Object> r) {
    registry.require(d, (String) r.get("dataResourceKey"), (String) r.get("action"));
    String preset = (String) r.get("scopePreset");
    if (!Set.of("SELF", "TENANT_ALL").contains(preset) && !d.kind().equals("ENTERPRISE"))
      throw invalid();
    if (preset.equals("SPECIFIED_DEPARTMENTS"))
      for (Object department : (List<?>) r.get("departmentIds")) {
        if (db.queryForObject(
                "select count(*) from summer_organization_unit where enterprise_id=? and unit_id=?"
                    + " and status='ACTIVE'",
                Integer.class,
                d.id(),
                department)
            != 1) throw invalid();
      }
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
    if (preset.equals("SPECIFIED_DEPARTMENTS"))
      for (Object department : (List<?>) r.get("departmentIds"))
        db.update(
            "insert into"
                + " summer_permission_selected_department(domain_key,permission_id,department_id)"
                + " values(?,?,?)",
            d.key(),
            id,
            department);
  }

  private static SummerException invalid() {
    return SummerException.withMessage(
        PermissionErrorCode.INVALID,
        "Department scope requires valid enterprise department IDs; other presets cannot carry a"
            + " department list");
  }
}
