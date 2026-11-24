package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties =
    "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class PlatformPermissionManagementHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  static final String TENANT = "/api/v1/enterprises/ent-acme";

  @ParameterizedTest
  @CsvSource({
      "POST,/permission-groups", "PATCH,/permission-groups/existing", "DELETE,/permission-groups/existing",
      "POST,/permissions", "PATCH,/permissions/existing", "DELETE,/permissions/existing",
      "PUT,/permissions/existing/interfaces", "PUT,/permissions/existing/menus", "PUT,/permissions/existing/data-scope"
  })
  void evenAnAuthorizedTenantAdministratorCannotCustomizePermissionDefinitions(String method, String path)
      throws Exception {
    // This actor is a persisted managed administrator; rejection must not depend on missing grants.
    mvc.perform(get(TENANT + "/roles").with(user("summer-user"))).andExpect(status().isOk());
    var before = db.queryForList("select * from summer_permission where domain_key='ENTERPRISE:ent-acme' order by permission_id");
    var groups = db.queryForList("select * from summer_permission_group where domain_key='ENTERPRISE:ent-acme' order by group_id");
    var domainVersion = db.queryForMap("select * from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'");
    mvc.perform(request(HttpMethod.valueOf(method), TENANT + path)
            .with(user("summer-user")).with(csrf()).contentType("application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString()).header("If-Match", "\"0\"")
            .content("{}"))
        .andExpect(status().isNotFound());
    assertEquals(before, db.queryForList("select * from summer_permission where domain_key='ENTERPRISE:ent-acme' order by permission_id"));
    assertEquals(groups, db.queryForList("select * from summer_permission_group where domain_key='ENTERPRISE:ent-acme' order by group_id"));
    assertEquals(domainVersion, db.queryForMap("select * from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'"));
  }

  @Test
  void tenantOperationHintsExcludeDefinitionWritesButRetainRoleManagement() throws Exception {
    mvc.perform(get(TENANT + "/authorization/operations")
            .param("actions", "groups.create,permissions.create,permissions.update,permissions.delete,permissions.interfaces.write,permissions.menus.write,roles.create")
            .with(user("summer-user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['groups.create']").value(false))
        .andExpect(jsonPath("$.data.actions['permissions.create']").value(false))
        .andExpect(jsonPath("$.data.actions['permissions.update']").value(false))
        .andExpect(jsonPath("$.data.actions['permissions.delete']").value(false))
        .andExpect(jsonPath("$.data.actions['permissions.interfaces.write']").value(false))
        .andExpect(jsonPath("$.data.actions['permissions.menus.write']").value(false))
        .andExpect(jsonPath("$.data.actions['roles.create']").value(true));
  }

  @Test
  void platformDefinitionsAndTenantRoleAssignmentRemainAvailable() throws Exception {
    mvc.perform(post("/api/v1/platform/permission-groups").with(user("summer-user")).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
            .content("{\"name\":\"Platform definitions\"}"))
        .andExpect(status().isCreated());
    String permission = db.queryForObject("select permission_id from summer_permission where domain_key='PLATFORM:platform' and code='management-read'", String.class);
    mvc.perform(get(TENANT + "/roles?catalog=permissions").with(user("summer-user"))).andExpect(status().isOk());
    mvc.perform(post(TENANT + "/roles").with(user("summer-user")).with(csrf())
            .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("name", "Tenant reader", "code", "reader-" + UUID.randomUUID(), "permissionIds", java.util.List.of(permission)))))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.data.permissionIds[0]").value(permission));
  }
}
