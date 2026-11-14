package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Current persisted management grants and raw delegation through real MVC dispatch. */
@SpringBootTest(properties = {
    "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme",
    "summer.sample.authorization.legacy-scope-compatibility=false",
    "spring.datasource.url=jdbc:h2:mem:perm09_legacy_operations;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class LegacyManagementOperationsHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService events;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  private static final String TENANT = "/api/v1/enterprises/ent-acme";
  private static final List<String> SCOPES = List.of("enterprise.menu.write", "platform.menu.write", "enterprise.roles.write", "platform.capabilities.write");

  private RequestPostProcessor actor(List<String> scopes) {
    return jwt().jwt(j -> j.subject("summer-user").claim("scope", scopes)
        .claim("summer_identity_source", "local-user")
        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
        .claim("enterprise_capabilities", Map.of("ent-acme", SCOPES))
        .claim("platform_capabilities", List.of("platform.menu.write")))
        .authorities(scopes.stream().map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
            .toArray(org.springframework.security.core.GrantedAuthority[]::new));
  }

  @Test
  void managedMenuAndEventHintsKeepCurrentGrantsAndOriginalDelegation() throws Exception {
    hints(actor(SCOPES), true, true);
    mvc.perform(get("/api/v1/platform/authorization/operations").param("actions", "menu.manifest.write").with(actor(SCOPES)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.actions['menu.manifest.write']").value(true));
    mvc.perform(put("/api/v1/platform/menu-manifest").with(actor(SCOPES)).with(csrf()).header("If-Match", "\"0\"")
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content("{\"manifestVersion\":1,\"items\":[{\"menuId\":\"menu-manifest\",\"path\":\"/menu-manifest\",\"titleKey\":\"menu.menu-manifest\",\"iconKey\":\"menu\",\"sortOrder\":0}]}"))
        .andExpect(status().isOk());
    mvc.perform(get(TENANT + "/menu-manifest/management").with(actor(SCOPES))).andExpect(status().isOk());
    mvc.perform(patch(TENANT + "/menu-manifest").param("menuId", "menu-manifest").with(actor(SCOPES)).with(csrf())
        .header("If-Match", "\"1\"").header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType("application/json").content("{\"sortOrder\":17}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.sortOrder").value(17));
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(status -> events.enqueueEnterprise("ent-acme", "ROLE", "legacy09", "ROLE_UPDATED", 1, "legacy09"));
    String event = db.queryForObject("select event_id from summer_authorization_change_outbox where resource_id='legacy09'", String.class);
    db.update("update summer_authorization_change_outbox set status='DEAD_LETTER' where event_id=?", event);
    mvc.perform(post("/api/authorization-events/" + event + "/retry").param("enterpriseId", "ent-acme").with(actor(SCOPES)).with(csrf()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));

    // Old claims and explicitly supplied authorities must never widen the raw OAuth scope.
    var claimAuthoritiesOnly = jwt().jwt(j -> j.subject("summer-user").claim("scope", "openid")
        .claim("summer_identity_source", "local-user")
        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
        .claim("enterprise_capabilities", Map.of("ent-acme", SCOPES))
        .claim("platform_capabilities", List.of("platform.menu.write")))
        .authorities(SCOPES.stream().map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
            .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    hints(claimAuthoritiesOnly, false, false);
    mvc.perform(put("/api/v1/platform/menu-manifest").with(claimAuthoritiesOnly).with(csrf())
        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
        .content("{\"manifestVersion\":2,\"items\":[]}"))
        .andExpect(status().isForbidden());
    mvc.perform(patch(TENANT + "/menu-manifest").param("menuId", "menu-manifest").with(claimAuthoritiesOnly).with(csrf())
        .header("If-Match", "\"2\"").header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType("application/json").content("{\"sortOrder\":23}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/authorization-events/" + event + "/retry").param("enterpriseId", "ent-acme")
        .with(claimAuthoritiesOnly).with(csrf())).andExpect(status().isForbidden());

    hints(actor(List.of("openid")), false, false);
    db.update("update summer_authorization_role set status='DISABLED' where role_id in (select role_id from summer_permission_role where domain_key='ENTERPRISE:ent-acme')");
    hints(actor(SCOPES), false, false);
    mvc.perform(patch(TENANT + "/menu-manifest").param("menuId", "menu-manifest").with(actor(SCOPES)).with(csrf())
        .header("If-Match", "\"2\"").header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType("application/json").content("{\"sortOrder\":23}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/authorization-events/" + event + "/retry").param("enterpriseId", "ent-acme").with(actor(SCOPES)).with(csrf()))
        .andExpect(status().isForbidden());
  }

  private void hints(RequestPostProcessor who, boolean menu, boolean retry) throws Exception {
    mvc.perform(get(TENANT + "/authorization/operations").param("actions", "menu.layout.write,authorization.events.retry,roles.create").with(who))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.actions['menu.layout.write']").value(menu))
        .andExpect(jsonPath("$.data.actions['authorization.events.retry']").value(retry))
        .andExpect(jsonPath("$.data.actions['roles.create']").value(retry));
  }
}
