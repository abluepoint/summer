package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.abluepoint.summer.sample.authorization.application.data.DataReadService;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class SharedPlatformPermissionsHttpTest {
  static final String P = "/api/v1/platform", E = "/api/v1/enterprises/ent-acme";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired ManagedPermissionAccess access;
  @Autowired DataReadService reads;

  @Test void tenantChoicesComeOnlyFromThePlatformCatalog() throws Exception {
    String id = permission("MENU", null);
    var tenant = read(E + "/roles?catalog=permissions&size=200");
    assertTrue(tenant.toString().contains(id));
    assertEquals(read(P + "/permissions?size=200"), tenant);
    assertEquals(read(P + "/permission-groups?size=200"), read(E + "/roles?catalog=permission-groups&size=200"));
    String local = db.queryForObject("select permission_id from summer_permission where domain_key='ENTERPRISE:ent-acme' and code='management-read'", String.class);
    assertFalse(tenant.toString().contains(local));
    mvc.perform(post(E + "/roles").with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("name", "Invalid local grant", "code", UUID.randomUUID().toString(), "permissionIds", List.of(local)))))
        .andExpect(status().isBadRequest());
  }

  @Test void sharedInterfaceMenuAndDataGrantsStayInsideTheAssignedTenantAndRevokeImmediately() throws Exception {
    String subject = "catalog-reader";
    db.update("insert into summer_auth_user(username,password_hash,role,active,created_at,updated_at) select ?,password_hash,role,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP from summer_auth_user where username='summer-user'", subject);
    db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values ('ent-acme','catalog-member',?,'Catalog','catalog@example.test','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", subject);
    String api = permission("INTERFACE", null), menu = permission("MENU", null);
    String group = create(P + "/permission-groups", Map.of("name", "Historical catalog")).path("groupId").asText();
    String data = historical.platformData(group, "read", "TENANT_ALL", "AUTHORIZED");
    String resource = db.queryForObject("select resource_id from summer_interface_resource where normalized_path='/api/v1/enterprises/{enterpriseId}/roles' and http_method='GET' and active=true", String.class);
    replace(P + "/permissions/" + api + "/interfaces", Map.of("resourceIds", List.of(resource)), "\"0\"");
    replace(P + "/permissions/" + menu + "/menus", Map.of("menuIds", List.of("roles")), "\"0\"");
    String role = create(E + "/roles", Map.of("name", "Catalog role", "code", UUID.randomUUID().toString(), "permissionIds", List.of(api, menu, data))).path("roleId").asText();
    replace(E + "/users/" + subject + "/roles", Map.of("roleIds", List.of(role)), "\"0\"");
    mvc.perform(get(E + "/roles").with(user(subject))).andExpect(status().isOk());
    mvc.perform(get(P + "/roles").with(user(subject))).andExpect(status().isForbidden());
    assertTrue(read(E + "/users/" + subject + "/roles").path("sources").toString().contains(data));
    var actor = new UsernamePasswordAuthenticationToken(subject, "unused", List.of());
    assertTrue(access.allows(Domain.enterprise("ent-acme"), actor, "MENU", "roles"));
    var scope = reads.read(Domain.enterprise("ent-acme"), "applications", "read", actor, x -> x);
    assertTrue(scope.all());
    assertEquals("ent-acme", scope.bindings().get("scopeTenant"));
    db.update("insert into summer_enterprise(enterprise_id,name,code,status,version,created_at,updated_at) values ('catalog-other','Other','catalog-other','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values ('catalog-other','catalog-other-member',?,'Catalog','catalog@example.test','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", subject);
    db.update("insert into summer_authorization_domain(domain_key,engine_mode,revision) values ('ENTERPRISE:catalog-other','MANAGED',0)");
    assertTrue(access.active(Domain.enterprise("catalog-other"), actor));
    assertFalse(access.allows(Domain.enterprise("catalog-other"), actor, "MENU", "roles"));
    assertFalse(reads.read(Domain.enterprise("catalog-other"), "applications", "read", actor, DataReadService.Scope::all));
    assertFalse(access.allows(Domain.platform(), actor, "MENU", "roles"));
    mvc.perform(patch(P + "/permissions/" + api).with(user("summer-user")).with(csrf())
        .header("If-Match", "\"1\"").header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType("application/json").content("{\"status\":\"DISABLED\"}"))
        .andExpect(status().isOk());
    mvc.perform(get(E + "/roles").with(user(subject))).andExpect(status().isForbidden());
    replace(E + "/roles/" + role + "/permissions", Map.of("permissionIds", List.of()), "\"1\"");
    assertFalse(access.allows(Domain.enterprise("ent-acme"), actor, "MENU", "roles"));
    assertFalse(reads.read(Domain.enterprise("ent-acme"), "applications", "read", actor, DataReadService.Scope::all));
  }

  @Test void existingLocalGrantsCanBeRetainedOrRemovedButCannotBeCopiedToAnotherRole() throws Exception {
    String local = db.queryForObject("select permission_id from summer_permission where domain_key='ENTERPRISE:ent-acme' and code='management-read'", String.class);
    String role = create(E + "/roles", Map.of("name", "Historical role", "code", UUID.randomUUID().toString(), "permissionIds", List.of())).path("roleId").asText();
    // A pre-upgrade persisted relationship, not a tenant HTTP definition writer.
    db.update("insert into summer_role_permission values ('ENTERPRISE:ent-acme',?,?)", role, local);
    assertEquals(local, read(E + "/roles/" + role).path("legacyPermissions").get(0).path("permissionId").asText());
    replace(E + "/roles/" + role + "/permissions", Map.of("permissionIds", List.of(local)), "\"1\"");
    replace(E + "/roles/" + role + "/permissions", Map.of("permissionIds", List.of()), "\"2\"");
    assertEquals(0, read(E + "/roles/" + role).path("legacyPermissions").size());
  }

  @Test void platformCatalogCanDefineTenantDataPresetsWithoutEmbeddingTenantDepartmentIds() throws Exception {
    var targets = List.of(permission("INTERFACE", null));
    String id = permission("DATA", Map.of("interfacePermissionIds", targets, "scopePreset", "DEPARTMENT_AND_DESCENDANTS"));
    assertEquals("DEPARTMENT_AND_DESCENDANTS", read(P + "/permissions/" + id).path("dataScope").path("scopePreset").asText());
    mvc.perform(patch(P + "/permissions/" + id).with(user("summer-user")).with(csrf())
        .header("If-Match", "\"1\"").header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType("application/json").content(json.writeValueAsString(Map.of("dataScope", Map.of("interfacePermissionIds", targets, "scopePreset", "SPECIFIED_DEPARTMENTS", "departmentIds", List.of("foreign-department"))))))
        .andExpect(status().isBadRequest());
  }

  @Autowired HistoricalTenantPermissions historical;

  String permission(String type, Map<String, Object> scope) throws Exception {
    String group = create(P + "/permission-groups", Map.of("name", "Shared catalog")).path("groupId").asText();
    var body = new HashMap<String, Object>(Map.of("name", "Shared " + type, "code", UUID.randomUUID().toString(), "type", type, "accessMode", "AUTHORIZED", "groupId", group));
    if (scope != null) body.put("dataScope", scope);
    return create(P + "/permissions", body).path("permissionId").asText();
  }
  JsonNode create(String path, Map<String, Object> body) throws Exception {
    return json.readTree(mvc.perform(post(path).with(user("summer-user")).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("data");
  }
  JsonNode read(String path) throws Exception {
    return json.readTree(mvc.perform(get(path).with(user("summer-user"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
  }
  void replace(String path, Map<String, Object> body, String etag) throws Exception {
    mvc.perform(put(path).with(user("summer-user")).with(csrf())
        .header("If-Match", etag).header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
  }
}
