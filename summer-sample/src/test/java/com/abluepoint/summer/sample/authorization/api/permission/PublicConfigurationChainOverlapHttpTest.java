package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Generic configured public patterns cannot replace the precise public credential chain. */
@SpringBootTest(
    properties = {
      "summer.security.resource-server.protected-paths[0]=/api/**",
      "summer.security.resource-server.protected-paths[1]=/public/**",
      "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme,PLATFORM:platform"
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({RolePermissionHttpTest.Schema.class, DepartmentDataScopeHttpTest.DataSchema.class})
@DirtiesContext
class PublicConfigurationChainOverlapHttpTest {
  @Autowired HistoricalTenantPermissions historical;
  @Autowired com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceBindingService bindings;
  @Autowired MockMvc mvc;
  @Autowired tools.jackson.databind.ObjectMapper json;
  @Autowired org.springframework.jdbc.core.JdbcTemplate db;
  @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources resources;

  @Test
  void configuredOverlapPreservesExplicitManagedPublicReadsAndRejectsInvalidBearer() throws Exception {
    resources.scan();
    for (String resource : List.of("dictionaries", "system-parameters")) {
      boolean dictionary = resource.equals("dictionaries");
      var domain = dictionary ? com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform()
          : com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
      String permission = db.queryForObject("select permission_id from summer_permission where domain_key=? and code='PUBLIC'", String.class, domain.key());
      String group = db.queryForObject("select group_id from summer_permission where domain_key=? and permission_id=?", String.class, domain.key(), permission);
      String template = "/public/enterprises/{enterpriseId}/" + resource + (dictionary ? "/{code}" : "/{key}");
      String installed = db.queryForObject("select resource_id from summer_interface_resource where http_method='GET' and normalized_path=? and active=true", String.class, template);
      var old = bindings.read(domain, permission, "INTERFACE");
      var ids = new java.util.ArrayList<String>();
      json.valueToTree(old.body()).path("resourceIds").forEach(id -> ids.add(id.asText()));
      ids.add(installed);
      bindings.replace(domain, "fixture", permission, "INTERFACE", ids, "\"" + old.version() + "\"",
          java.util.UUID.randomUUID().toString(), "overlap-test");
      historical.seedData(domain, java.util.Map.of(
          "name", "Explicit public read", "code", "overlap-" + resource, "accessMode", "PUBLIC",
          "groupId", group, "dataScope", java.util.Map.of("dataResourceKey", resource, "action", "read", "scopePreset", "TENANT_ALL")));
      org.junit.jupiter.api.Assertions.assertEquals(1, db.queryForObject(
          "select count(*) from summer_permission_resource where domain_key=? and permission_id=? and resource_id=?",
          Integer.class, domain.key(), permission, installed));
    }
    for (String route :
        List.of(
            "/public/enterprises/ent-acme/dictionaries/overlap-final09",
            "/public/enterprises/ent-acme/system-parameters/overlap-final09")) {
      mvc.perform(get(route)).andExpect(status().isNotFound());
      mvc.perform(get(route).header("Authorization", "Bearer invalid-public09"))
          .andExpect(status().isUnauthorized());
    }
  }
}
