package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

/** A preview resolves a persisted subject and a real row through its registered adapter. */
@Service
public class DataResourcePreviewService {
  private final ApplicationQueryService applications;
  private final DataReadService reads;
  private final DataResourceRegistry registry;
  private final ManagedPermissionAccess access;
  private final NamedParameterJdbcTemplate db;

  public DataResourcePreviewService(ApplicationQueryService applications, DataReadService reads,
      DataResourceRegistry registry, ManagedPermissionAccess access, NamedParameterJdbcTemplate db) {
    this.applications = applications; this.reads = reads; this.registry = registry;
    this.access = access; this.db = db;
  }

  public Object preview(Domain domain, String resource, Map<String, Object> body) {
    return previewCurrent(domain, resource, body, null);
  }

  /** Server-only diagnostic narrowing; callers cannot replace a registered range or SQL adapter. */
  public Object previewPermission(Domain domain, String resource, Map<String, Object> body,
      String permissionId) {
    if (permissionId == null || permissionId.isBlank())
      throw SummerException.withMessage(PermissionErrorCode.INVALID, "A current permission identity is required");
    return previewCurrent(domain, resource, body, permissionId);
  }

  private Object previewCurrent(Domain domain, String resource, Map<String, Object> body,
      String permissionId) {
    if ("applications".equals(resource)) return permissionId == null
        ? applications.preview(domain, resource, body)
        : applications.previewPermission(domain, resource, body, permissionId);
    if (!Set.of("dictionaries", "system-parameters").contains(resource)
        || !"ENTERPRISE".equals(domain.kind())
        || !body.keySet().equals(Set.of("subject", "recordId", "action"))
        || !(body.get("subject") instanceof String subject) || subject.isBlank()
        || !(body.get("recordId") instanceof String recordId)
        || !(body.get("action") instanceof String action))
      throw SummerException.withMessage(PermissionErrorCode.INVALID,
          "Registered resource, domain and persisted subject/record/action required");
    var target = UsernamePasswordAuthenticationToken.authenticated(subject, "", List.of());
    // Table and primary key are trusted application registration, never request-provided SQL.
    String table = "dictionaries".equals(resource) ? "summer_dictionary_type" : "summer_system_parameter";
    java.util.function.Function<DataReadService.Scope, Object> query = scope -> {
      if (!access.active(domain, target))
        throw SummerException.withMessage(PermissionErrorCode.INVALID, "Unknown active subject");
      var range = registry.configurationProjection(scope);
      var bindings = new HashMap<String, Object>(range.bindings());
      bindings.put("recordId", recordId);
      return Map.of("allowed", db.queryForObject("select count(*) from " + table
          + " where id=:recordId and (" + range.predicate() + ")", bindings, Long.class) > 0,
          "revision", scope.revision());
    };
    return permissionId == null ? reads.read(domain, resource, action, target, query)
        : reads.readPermission(domain, resource, action, target, permissionId, query);
  }
}
