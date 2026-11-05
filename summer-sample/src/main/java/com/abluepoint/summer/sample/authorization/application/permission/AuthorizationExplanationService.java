package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.AuthorizationDomainVersions;
import com.abluepoint.summer.sample.authorization.UnifiedAuthorizationService;
import com.abluepoint.summer.sample.authorization.application.data.DataResourcePreviewService;
import com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Current diagnostic facts only; historical decisions never acquire today's role provenance. */
@Service
public class AuthorizationExplanationService {
  private static final int MAX_DATA_CANDIDATES = 50;
  private final AuthorizationDomainVersions versions;
  private final PermissionManagementGuard guard;
  private final ManagedPermissionAccess access;
  private final UnifiedAuthorizationService authorization;
  private final List<TrustedPermissionResourceResolver> resolvers;
  private final DataResourceRegistry data;
  private final DataResourcePreviewService previews;
  private final JdbcTemplate db;

  public AuthorizationExplanationService(AuthorizationDomainVersions versions,
      PermissionManagementGuard guard, ManagedPermissionAccess access,
      UnifiedAuthorizationService authorization, List<TrustedPermissionResourceResolver> resolvers,
      DataResourceRegistry data, DataResourcePreviewService previews, JdbcTemplate db) {
    this.versions = versions; this.guard = guard; this.access = access;
    this.authorization = authorization; this.resolvers = List.copyOf(resolvers);
    this.data = data; this.previews = previews; this.db = db;
  }

  public Map<String, Object> explain(Domain domain, Authentication manager,
      Map<String, String> query, String requestId) {
    guard.require(domain, manager, false);
    String type = query.get("resourceType");
    boolean isData = "DATA".equals(type);
    if (!query.keySet().equals(isData
        ? Set.of("subject", "resourceType", "resourceId", "action", "recordId")
        : Set.of("subject", "resourceType", "resourceId"))
        || !Set.of("INTERFACE", "MENU", "DATA").contains(type == null ? "" : type)
        || !valid(query.get("subject"), 128) || !valid(query.get("resourceId"), 160)
        || isData && (!valid(query.get("action"), 32) || !valid(query.get("recordId"), 256)))
      throw invalid();
    String id = query.get("resourceId");
    var resolver = isData ? null : resolvers.stream().filter(r -> r.type().equals(type))
        .findFirst().orElseThrow(AuthorizationExplanationService::invalid);
    if (isData) data.require(domain, id, query.get("action"));
    else resolver.prepare(domain);
    var target = UsernamePasswordAuthenticationToken.authenticated(query.get("subject"), "", List.of());
    return versions.readManaged(domain, revision -> {
      // The manager can have been revoked after the HTTP gate or the catalog preparation.
      guard.require(domain, manager, false);
      if (!access.active(domain, target)) throw new AccessDeniedException("Active domain subject required");
      ResourceAuthorization.Resource resource = isData
          ? new ResourceAuthorization.Resource(id, "DATA", "APPLICATION") : resolver.resolve(domain, id);
      boolean allowed;
      String reason;
      if (isData) {
        Object value = previews.preview(domain, id, Map.of("subject", target.getName(),
            "recordId", query.get("recordId"), "action", query.get("action")));
        var preview = sameRevisionPreview(value, revision);
        allowed = Boolean.TRUE.equals(preview.get("allowed"));
        reason = allowed ? "CURRENT_DATA_RANGE_ALLOW" : "OUTSIDE_CURRENT_DATA_RANGE_OR_BUSINESS_BOUND";
      } else {
        var decision = authorization.decide(
            new ResourceAuthorization.Request(domain, target, resource, requestId));
        if (decision.revision() != revision)
          throw new AccessDeniedException("Authorization changed during explanation");
        allowed = decision.allowed(); reason = decision.reason();
      }
      var management = management(domain, target, resource);
      if (Boolean.TRUE.equals(management.get("required"))
          && !Boolean.TRUE.equals(management.get("allowed"))) {
        allowed = false;
        reason = "CURRENT_MANAGEMENT_ROLE_REQUIRED";
      }
      var result = new LinkedHashMap<String, Object>();
      result.put("subject", target.getName());
      result.put("domain", Map.of("kind", domain.kind(), "id", domain.id(), "key", domain.key()));
      result.put("resource", Map.of("type", resource.type(), "id", resource.id(), "source", resource.source()));
      if (isData) result.put("action", query.get("action"));
      result.put("allowed", allowed); result.put("reason", reason); result.put("revision", revision);
      result.put("requestId", requestId); result.put("provenance", "CURRENT_PERSISTED_FACTS");
      result.put("evaluationBoundary", "PERSISTED_RESOURCE_AND_MANAGEMENT_GRANTS");
      result.put("credentialDelegation", "NOT_EVALUATED");
      result.put("managementQualification", management);
      result.put("matchedPermissions", matches(domain, target, type, id, query.get("action"),
          query.get("recordId"), revision));
      return Collections.unmodifiableMap(result);
    });
  }

  private List<Map<String, Object>> matches(Domain domain, Authentication target,
      String type, String id, String action, String recordId, long revision) {
    var rows = "DATA".equals(type)
        ? db.queryForList("select p.*,s.scope_preset from summer_permission p join"
            + " summer_permission_data_scope s on s.domain_key=p.domain_key and s.permission_id=p.permission_id"
            + " where p.domain_key=? and p.type='DATA' and s.data_resource_key=? and s.action=?"
            + " order by p.permission_id limit " + (MAX_DATA_CANDIDATES + 1), domain.key(), id, action)
        : db.queryForList("select p.* from summer_permission p join summer_permission_resource x"
            + " on x.domain_key=p.domain_key and x.permission_id=p.permission_id where p.domain_key=?"
            + " and p.type=? and x.resource_type=? and x.resource_id=? order by p.permission_id",
            domain.key(), type, type, id);
    if ("DATA".equals(type) && rows.size() > MAX_DATA_CANDIDATES)
      throw SummerException.withMessage(PermissionErrorCode.INVALID,
          "Data explanation supports at most 50 candidate permissions");
    var roleFacts = access.permissions(domain, target);
    return rows.stream().filter(p -> access.applies(domain, target, p))
        .filter(p -> !"DATA".equals(type) || Boolean.TRUE.equals(sameRevisionPreview(
            previews.previewPermission(domain, id, Map.of("subject", target.getName(),
                "recordId", recordId, "action", action), p.get("permission_id").toString()), revision)
            .get("allowed"))).map(p -> {
      var match = new LinkedHashMap<String, Object>();
      match.put("permissionId", p.get("permission_id")); match.put("name", p.get("name"));
      match.put("accessMode", p.get("access_mode"));
      match.put("roles", "AUTHORIZED".equals(p.get("access_mode"))
          ? roleFacts.stream().filter(r -> r.get("permission_id").equals(p.get("permission_id")))
              .map(r -> Map.of("roleId", r.get("role_id"), "name", r.get("role_name"))).toList()
          : List.of());
      if (p.get("scope_preset") != null) match.put("scopePreset", p.get("scope_preset"));
      return Collections.unmodifiableMap(match);
    }).toList();
  }

  private static Map<?, ?> sameRevisionPreview(Object value, long revision) {
    if (!(value instanceof Map<?, ?> preview)
        || !(preview.get("revision") instanceof Number nested)
        || nested.longValue() != revision)
      throw new AccessDeniedException("Authorization changed during preview");
    return preview;
  }

  private Map<String, Object> management(Domain domain, Authentication target,
      ResourceAuthorization.Resource resource) {
    if (!"INTERFACE".equals(resource.type()) || !"MVC".equals(resource.source()))
      return Map.of("required", false, "allowed", true, "grants", List.of());
    var rows = db.queryForList("select handler_provenance,http_method,normalized_path from"
        + " summer_interface_resource where resource_id=? and active=true and domain_kind=?",
        resource.id(), domain.kind());
    if (rows.size() != 1 || !(rows.getFirst().get("handler_provenance") instanceof String handler))
      throw new AccessDeniedException("Current trusted handler provenance required");
    int bean = handler.indexOf('@'), signature = handler.indexOf('#');
    if (bean < 1 || signature <= bean + 1 || signature == handler.length() - 1
        || handler.indexOf('@', bean + 1) >= 0 || handler.indexOf('#', signature + 1) >= 0)
      throw new AccessDeniedException("Invalid trusted handler provenance");
    String owner = handler.substring(0, bean);
    java.lang.reflect.Method method;
    try {
      Class<?> type = Class.forName(owner, false, getClass().getClassLoader());
      method = java.util.stream.Stream.concat(Arrays.stream(type.getDeclaredMethods()),
          Arrays.stream(type.getMethods()))
          .filter(candidate -> candidate.toGenericString().equals(handler.substring(signature + 1)))
          .findFirst().orElseThrow(() -> new AccessDeniedException("Current handler method required"));
    } catch (ClassNotFoundException | LinkageError invalid) {
      throw new AccessDeniedException("Current handler owner required");
    }
    boolean identity = owner.equals("com.abluepoint.summer.sample.authorization.api.permission.PermissionAccessController")
        || owner.equals("com.abluepoint.summer.sample.menu.MenuManifestController")
            && method.getName().equals("visible")
            && "GET".equals(rows.getFirst().get("http_method"))
            && "/api/v1/enterprises/{enterpriseId}/menu-manifest".equals(rows.getFirst().get("normalized_path"));
    boolean required = !identity
        && com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerRoutes.management(owner);
    if (!required) return Map.of("required", false, "allowed", true, "grants", List.of());
    var permissionIds = new HashSet<>(db.queryForList("select permission_id from summer_permission_resource"
        + " where domain_key=? and resource_type='INTERFACE' and resource_id=?", String.class,
        domain.key(), resource.id()));
    var grants = access.permissions(domain, target).stream()
        .filter(permission -> permissionIds.contains(permission.get("permission_id")))
        .map(permission -> Map.of("permissionId", permission.get("permission_id"),
            "permissionName", permission.get("name"), "roleId", permission.get("role_id"),
            "roleName", permission.get("role_name"))).toList();
    return Map.of("required", true, "allowed", access.roleAllows(domain, target, resource.id())
        && !grants.isEmpty(), "grants", grants);
  }

  private static boolean valid(String value, int max) {
    return value != null && !value.isBlank() && value.length() <= max
        && value.chars().noneMatch(Character::isISOControl);
  }

  private static SummerException invalid() {
    return SummerException.withMessage(PermissionErrorCode.INVALID,
        "Only a persisted subject and a registered resource (plus DATA action/recordId) are accepted");
  }
}
