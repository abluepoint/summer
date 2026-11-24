package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.sample.authorization.application.permission.PermissionService;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceBindingService;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Pre-existing tenant data for runtime authorization tests, never a substitute HTTP writer. */
final class HistoricalTenantPermissions {
  private static final String BASE = "/api/v1/enterprises/ent-acme";
  private final PermissionService permissions;
  private final com.abluepoint.summer.sample.authorization.application.permission.ManagedRoleService roles;
  private final org.springframework.jdbc.core.JdbcTemplate db;
  private final PermissionResourceBindingService bindings;
  private final ObjectMapper json;
  private final com.abluepoint.summer.sample.authorization.application.data.PermissionDataDetails details;
  private final org.springframework.transaction.support.TransactionTemplate tx;

  HistoricalTenantPermissions(DataSource source, PermissionService permissions,
      PermissionResourceBindingService bindings, ObjectMapper json,
      com.abluepoint.summer.sample.authorization.application.permission.ManagedRoleService roles,
      com.abluepoint.summer.sample.authorization.application.data.PermissionDataDetails details,
      org.springframework.transaction.PlatformTransactionManager transactions) throws Exception {
    try (var connection = source.getConnection()) {
      if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"))
        throw new IllegalStateException("Historical permission fixtures require an in-memory database");
    }
    this.details = details;
    this.tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
    this.permissions = permissions;
    this.roles = roles;
    this.db = new org.springframework.jdbc.core.JdbcTemplate(source);
    this.bindings = bindings;
    this.json = json;
  }

  JsonNode create(String path, Map<String, Object> body) {
    if (!List.of(BASE + "/permission-groups", BASE + "/permissions").contains(path))
      throw new IllegalArgumentException("Only historical tenant permission definitions may be seeded");
    if (path.endsWith("/permissions") && "DATA".equals(body.get("type")))
      return json.valueToTree(Map.of("permissionId", seedData(Domain.enterprise("ent-acme"), body)));
    var result = permissions.mutate(Domain.enterprise("ent-acme"), "historical-fixture",
        path.endsWith("/permission-groups"), null, "POST", body, null,
        UUID.randomUUID().toString(), "historical-fixture");
    return json.valueToTree(result.body());
  }

  // Seed a pre-upgrade row for compatibility tests. The current HTTP contract accepts interface IDs.
  String platformData(String group, String action, String preset, String mode) {
    var id = UUID.randomUUID().toString();
    return seedData(Domain.platform(), Map.of("code", id, "name", "Historical scope", "groupId", group,
        "accessMode", mode, "dataScope", Map.of("dataResourceKey", "applications", "action", action, "scopePreset", preset)));
  }

  String seedData(Domain domain, Map<String, Object> body) {
    return tx.execute(status -> {
      String id = UUID.randomUUID().toString();
      db.update("insert into summer_permission_identity(domain_key,permission_id,code,type) values(?,?,?,'DATA')",
          domain.key(), id, body.get("code"));
      db.update("insert into summer_permission(domain_key,permission_id,code,name,type,access_mode,group_id,status,version)"
          + " values(?,?,?,?,'DATA',?,?,'ACTIVE',1)", domain.key(), id, body.get("code"), body.get("name"),
          body.get("accessMode"), body.get("groupId"));
      details.replace(domain, id, details.historical(body.get("dataScope")));
      db.update("update summer_authorization_domain set revision=revision+1 where domain_key=?", domain.key());
      return id;
    });
  }

  JsonNode seedRole(Map<String, Object> body) {
    var fields = new java.util.HashMap<>(body);
    fields.put("permissionIds", List.of());
    var created = roles.mutate(Domain.enterprise("ent-acme"), "historical-fixture", "role", null,
        "POST", fields, null, UUID.randomUUID().toString(), "historical-fixture");
    String id = json.valueToTree(created.body()).path("roleId").asText();
    for (Object permission : (List<?>) body.get("permissionIds"))
      db.update("insert into summer_role_permission values ('ENTERPRISE:ent-acme',?,?)", id, permission);
    return json.valueToTree(roles.role(Domain.enterprise("ent-acme"), id));
  }

  void deletePermission(String id, String etag) {
    permissions.mutate(Domain.enterprise("ent-acme"), "historical-fixture", false, id,
        "DELETE", Map.of(), etag, UUID.randomUUID().toString(), "historical-fixture");
  }

  record Update(JsonNode body, String etag) {}

  String permissionEtag(String id) {
    return "\"" + permissions.get(Domain.enterprise("ent-acme"), false, id).version() + "\"";
  }

  Update readInterfaces(String permissionId) {
    return readBindings(permissionId, "INTERFACE");
  }

  Update readBindings(String permissionId, String type) {
    var result = bindings.read(Domain.enterprise("ent-acme"), permissionId, type);
    return new Update(json.valueToTree(result.body()), "\"" + result.version() + "\"");
  }

  Update replace(String path, Map<String, Object> body, String etag) {
    String prefix = BASE + "/permissions/";
    String suffix = path.endsWith("/interfaces") ? "/interfaces" : "/menus";
    if (!path.startsWith(prefix) || !path.endsWith(suffix))
      throw new IllegalArgumentException("Only historical tenant permission bindings may be seeded");
    String field = suffix.equals("/interfaces") ? "resourceIds" : "menuIds";
    if (!body.keySet().equals(java.util.Set.of(field)) || !(body.get(field) instanceof List<?> ids))
      throw new IllegalArgumentException("Explicit historical binding list required");
    var result = bindings.replace(Domain.enterprise("ent-acme"), "historical-fixture",
        path.substring(prefix.length(), path.length() - suffix.length()),
        suffix.equals("/interfaces") ? "INTERFACE" : "MENU",
        ids.stream().map(String.class::cast).toList(), etag,
        UUID.randomUUID().toString(), "historical-fixture");
    return new Update(json.valueToTree(result.body()), "\"" + result.version() + "\"");
  }
}
