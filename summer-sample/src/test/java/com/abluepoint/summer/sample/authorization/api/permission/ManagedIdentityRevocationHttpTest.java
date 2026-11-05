package com.abluepoint.summer.sample.authorization.api.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Current local identity is required even when role and membership facts survive deletion. */
@SpringBootTest(
    properties =
        "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
@Transactional
class ManagedIdentityRevocationHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/platform", "/api/v1/enterprises/ent-acme"})
  void oldAuthenticatedPrincipalLosesAccessWhenCurrentIdentityIsMissing(String base)
      throws Exception {
    var session = user("summer-user");
    var bearer =
        jwt()
            .jwt(
                j ->
                    j.subject("summer-user")
                        .claim("scope", "enterprise.roles.read platform.capabilities.read"));
    mvc.perform(get(base + "/authorization/access").with(session)).andExpect(status().isOk());
    mvc.perform(get(base + "/authorization/access").with(bearer)).andExpect(status().isOk());
    assertEquals(1, db.update("delete from summer_auth_user where username=?", "summer-user"));
    mvc.perform(get(base + "/authorization/access").with(session))
        .andExpect(status().isForbidden());
    mvc.perform(get(base + "/authorization/access").with(bearer)).andExpect(status().isForbidden());
    mvc.perform(get(base + "/authorization/profile").with(session))
        .andExpect(status().isForbidden());
    mvc.perform(get(base + "/roles").with(bearer)).andExpect(status().isForbidden());
    mvc.perform(get(base + "/authorization/status")).andExpect(status().isOk());
  }
}
