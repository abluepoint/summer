package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Servlet feedback for the public creation contract; real TCP/MySQL acceptance is separate. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:managed-onboarding14;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class ManagedDomainOnboardingHttpTest {
  @Autowired MockMvc mvc;

  @org.springframework.boot.test.context.TestConfiguration
  static class InstallationSchema {
    @org.springframework.context.annotation.Bean
    org.springframework.boot.ApplicationRunner installationSchema(org.springframework.jdbc.core.JdbcTemplate db) {
      return args -> {
        for (String name : java.util.List.of("V160__authorization_migration_ledger.sql",
            "V170__authorization_data_migration_proof.sql", "V180__authorization_controlled_installation.sql")) {
          String sql = new String(new org.springframework.core.io.ClassPathResource(
              "db/migration/mysql/" + name).getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
          for (String statement : sql.replaceAll("(?m)--[^\\n]*", "").split(";"))
            if (!statement.isBlank()) db.execute(statement);
        }
      };
    }
  }

  @Test
  void newlyCreatedEnterpriseImmediatelyHasItsCreatorAndManagedAdministration() throws Exception {
    String code = "managed-" + UUID.randomUUID();
    var created = mvc.perform(post("/api/v1/platform/enterprises")
            .with(user("summer-user").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.enterprise.write")))
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Managed new enterprise\",\"code\":\"" + code + "\"}"))
        .andExpect(status().isCreated()).andReturn();
    String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.enterpriseId");
    mvc.perform(get("/api/v1/enterprises/{id}/members", id).with(user("summer-user").authorities(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_enterprise.members.read"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
    mvc.perform(get("/api/v1/enterprises/{id}/roles?catalog=permissions", id).with(user("summer-user")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/enterprises/{id}/roles?catalog=permissions", id).with(user("unassigned")))
        .andExpect(status().isForbidden());
  }
}
