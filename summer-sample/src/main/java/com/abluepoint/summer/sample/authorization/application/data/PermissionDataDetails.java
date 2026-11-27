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
    var scope = stored(d, id);
    if (scope != null && !scope.containsKey("interfacePermissionIds")) {
      scope.remove("dataResourceKey");
      scope.remove("action");
      scope.put("interfacePermissionIds", List.of());
    }
    return scope;
  }

  public Map<String, Object> stored(Domain d, String id) {
    var rows =
        db.queryForList(
            "select data_resource_key,action,scope_preset from"
                + " summer_permission_data_scope where domain_key=? and"
                + " permission_id=?",
            d.key(),
            id);
    if (rows.isEmpty()) return null;
    var r = rows.getFirst();
    var result = new LinkedHashMap<String, Object>();
    result.put("scopePreset", r.get("scope_preset"));
    if (r.get("data_resource_key") == null) {
      result.put("interfacePermissionIds", db.queryForList(
          "select interface_permission_id from summer_permission_data_interface"
              + " where domain_key=? and permission_id=? order by interface_permission_id",
          String.class, d.key(), id));
    } else {
      result.put("dataResourceKey", r.get("data_resource_key"));
      result.put("action", r.get("action"));
    }
    if (selectedDepartments(r.get("scope_preset")))
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

  public Map<String, Object> validate(Domain d, Object value) {
    if (!(value instanceof Map<?, ?> r)
        || !Set.of("scopePreset", "departmentIds", "interfacePermissionIds").containsAll(r.keySet())
        || !(r.get("interfacePermissionIds") instanceof List<?> ids) || ids.isEmpty())
      throw invalidInterfaces();
    var selected = new TreeSet<String>();
    for (Object id : ids) {
      if (!(id instanceof String key) || key.isBlank() || key.length() > 96) throw invalidInterfaces();
      selected.add(key);
    }
    var catalog = db.queryForList(
        "select permission_id from summer_permission where domain_key=? and type='INTERFACE'",
        String.class, d.key());
    if (!new HashSet<>(catalog).containsAll(selected)) throw invalidInterfaces();
    var result = preset(r);
    result.put("interfacePermissionIds", List.copyOf(selected));
    return result;
  }

  /** Used only to restore a verified historical grant during migration. */
  public Map<String, Object> historical(Object value) {
    if (!(value instanceof Map<?, ?> r)
        || !Set.of("scopePreset", "departmentIds", "dataResourceKey", "action").containsAll(r.keySet())
        || !(r.get("dataResourceKey") instanceof String key) || !(r.get("action") instanceof String action))
      throw invalid();
    registry.require(key, action);
    var result = preset(r);
    result.put("dataResourceKey", key);
    result.put("action", action);
    return result;
  }

  private Map<String, Object> preset(Map<?, ?> r) {
    if (!(r.get("scopePreset") instanceof String preset)
        || !Set.of("SELF", "TENANT_ALL", "DEPARTMENT", "DEPARTMENT_AND_DESCENDANTS", "SPECIFIED_DEPARTMENTS",
            "PLATFORM_ALL_DEPARTMENTS","PLATFORM_PRIMARY_SUBTREE","PLATFORM_SPECIFIED_SUBTREES").contains(preset))
      throw SummerException.withMessage(PermissionErrorCode.INVALID, "A supported record scope is required");
    var result = new LinkedHashMap<String, Object>();
    result.put("scopePreset", preset);
    if (selectedDepartments(preset)) {
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
    db.update("delete from summer_permission_data_interface where domain_key=? and permission_id=?", d.key(), id);
    String preset = (String) r.get("scopePreset");
    if (preset.equals("SPECIFIED_DEPARTMENTS") && Domain.platform().equals(d)
        || preset.startsWith("PLATFORM_") && !Domain.platform().equals(d)) throw invalid();
    if (selectedDepartments(preset))
      for (Object department : (List<?>) r.get("departmentIds")) {
        int found = Domain.platform().equals(d)
            ? db.queryForObject("select count(*) from summer_platform_department where id=?", Integer.class, department)
            : db.queryForObject("select count(*) from summer_organization_unit where enterprise_id=? and unit_id=? and status='ACTIVE'",
                Integer.class, d.id(), department);
        if (found != 1) throw invalid();
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
    if (r.get("interfacePermissionIds") instanceof List<?> ids)
      db.batchUpdate("insert into summer_permission_data_interface"
              + " (domain_key,permission_id,interface_permission_id) values(?,?,?)",
          ids, 200, (statement, target) -> {
            statement.setString(1, d.key());
            statement.setString(2, id);
            statement.setString(3, (String) target);
          });
    if (selectedDepartments(preset))
      for (Object department : (List<?>) r.get("departmentIds"))
        db.update(
            "insert into"
                + " summer_permission_selected_department(domain_key,permission_id,department_id)"
                + " values(?,?,?)",
            d.key(),
            id,
            department);
  }

  private static boolean selectedDepartments(Object preset) {
    return "SPECIFIED_DEPARTMENTS".equals(preset) || "PLATFORM_SPECIFIED_SUBTREES".equals(preset);
  }

  private static SummerException invalidInterfaces() {
    return SummerException.withMessage(PermissionErrorCode.INVALID,
        "Select one or more interface permissions from this permission catalog");
  }

  private static SummerException invalid() {
    return SummerException.withMessage(
        PermissionErrorCode.INVALID,
        "Department scope requires valid enterprise department IDs; other presets cannot carry a"
            + " department list");
  }
}
