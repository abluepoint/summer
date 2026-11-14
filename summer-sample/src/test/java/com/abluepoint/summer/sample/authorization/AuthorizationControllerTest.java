package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.jayway.jsonpath.JsonPath;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(CurrentManagedAuthorizationTestFixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationControllerTest {
  static final Domain DOMAIN=Domain.enterprise("ent-acme");
  static final String BASE="/api/v1/enterprises/ent-acme/roles";
  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate db;
  @Autowired CurrentManagedAuthorizationTestFixture fixture;
  @Autowired com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess access;
  @BeforeEach void currentAdministrator() {
    fixture.grantHandlers(DOMAIN,"security-admin",(h,p,m)->p.startsWith("/api/v1/enterprises/{enterpriseId}/") && (h.getBeanType().getName().endsWith(".RolePermissionController") || h.getBeanType().getName().endsWith(".AuthorizationController")));
    fixture.grantHandlers(Domain.platform(),"platform-operator",(h,p,m)->p.startsWith("/api/v1/platform/") && h.getBeanType().getName().endsWith(".AuthorizationController"));
  }
  private org.springframework.test.web.servlet.request.RequestPostProcessor admin(String scope) {
    return jwt().jwt(t->t.subject("security-admin").claim("scope",scope)).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_"+scope));
  }
  private MvcResult create(String body,String key) throws Exception {
    return mockMvc.perform(post(BASE).with(admin("enterprise.roles.write")).contentType("application/json").content(body).header("Idempotency-Key",key)).andExpect(status().isCreated()).andReturn();
  }
  private void retired(String path) throws Exception {
    long roles=db.queryForObject("select count(*) from summer_authorization_role",Long.class);
    mockMvc.perform(get(path).with(path.startsWith("/api/v1/platform/") ? jwt().jwt(t->t.subject("platform-operator").claim("scope","platform.capabilities.read")) : admin("enterprise.roles.read")))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_AUTHORIZATION_INVALID"))
        .andExpect(jsonPath("$.error.message").value("Legacy code, deny and bundle grant APIs are retired; use permission and role management"));
    assertThat(db.queryForObject("select count(*) from summer_authorization_role",Long.class)).isEqualTo(roles);
    assertThat(db.queryForObject("select engine_mode from summer_authorization_domain where domain_key=?",String.class,DOMAIN.key())).isEqualTo("MANAGED");
  }

  @Test void invalidRoleDuplicateCodeAndReusedIdempotencyKeyUseUnifiedFailures() throws Exception {
    mockMvc.perform(post(BASE).with(admin("enterprise.roles.write")).contentType("application/json").content("{}").header("Idempotency-Key","invalid"))
        .andExpect(status().isBadRequest()).andExpect(UnifiedBusinessFailure.unified("SAMPLE_PERMISSION_INVALID","Invalid role text"));
    String body="{\"name\":\"Reader\",\"code\":\"reader\",\"permissionIds\":[]}";
    create(body,"create");
    mockMvc.perform(post(BASE).with(admin("enterprise.roles.write")).contentType("application/json").content(body.replace("Reader","Changed")).header("Idempotency-Key","create"))
        .andExpect(status().isConflict()).andExpect(UnifiedBusinessFailure.unified("SAMPLE_PERMISSION_CONFLICT","Idempotency key reused"));
    mockMvc.perform(post(BASE).with(admin("enterprise.roles.write")).contentType("application/json").content(body).header("Idempotency-Key","duplicate"))
        .andExpect(status().isConflict()).andExpect(UnifiedBusinessFailure.unified("SAMPLE_PERMISSION_CONFLICT","Persistent role conflict"));
  }
  @Test void retiredEnterpriseCatalogCannotTreatOAuthScopesAsCapabilities() throws Exception {
    retired("/api/v1/enterprises/ent-acme/capabilities/catalog");
    mockMvc.perform(get(BASE).with(jwt().jwt(t->t.subject("alice").claim("scope","enterprise.roles.read"))))
        .andExpect(status().isForbidden());
  }
  @Test void retiredPlatformCatalogCannotBeSelectedThroughEnterpriseWorkspace() throws Exception {
    retired("/api/v1/platform/capabilities/catalog"); retired("/api/v1/enterprises/ent-acme/capabilities/catalog");
    mockMvc.perform(get("/api/v1/platform/roles").with(admin("enterprise.roles.read"))).andExpect(status().isForbidden());
  }
  @Test void disabledPermissionRetainsIdentityAndRolePermissionHasNoEffectField() throws Exception {
    String permission=fixture.grantHandlers(DOMAIN,"alice",(h,p,m)->p.equals("/api/v1/enterprises/{enterpriseId}/agents") && m.equals("GET"));
    var result=create("{\"name\":\"Auditor\",\"code\":\"auditor\",\"permissionIds\":[\""+permission+"\"]}","auditor");
    String id=JsonPath.read(result.getResponse().getContentAsString(),"$.data.roleId");
    mockMvc.perform(get(BASE+"/"+id).with(admin("enterprise.roles.read"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.permissionIds[0]").value(permission)).andExpect(jsonPath("$.data.effect").doesNotExist());
    db.update("update summer_permission set status='DISABLED' where domain_key=? and permission_id=?",DOMAIN.key(),permission);
    String resource=db.queryForObject("select resource_id from summer_permission_resource where domain_key=? and permission_id=?",String.class,DOMAIN.key(),permission);
    var actor=new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg","none").subject("alice").build());
    assertThat(access.allows(DOMAIN,actor,"INTERFACE",resource)).isFalse();
    // Current roles retain explicit IDs even while a permission is disabled; lifecycle affects access.
    assertThat(db.queryForObject("select permission_id from summer_role_permission where domain_key=? and role_id=?",String.class,DOMAIN.key(),id)).isEqualTo(permission);
  }
  @Test void idempotentRoleWriteReplaysAndStaleEtagIsRejected() throws Exception {
    String body="{\"name\":\"Operator\",\"code\":\"operator\",\"permissionIds\":[]}";
    var first=create(body,"replay"); var replay=create(body,"replay");
    assertThat(JsonPath.<Map<String,Object>>read(replay.getResponse().getContentAsString(),"$.data")).isEqualTo(JsonPath.<Map<String,Object>>read(first.getResponse().getContentAsString(),"$.data"));
    assertThat(replay.getResponse().getHeader("ETag")).isEqualTo(first.getResponse().getHeader("ETag"));
    String id=JsonPath.read(first.getResponse().getContentAsString(),"$.data.roleId");
    mockMvc.perform(patch(BASE+"/"+id).with(admin("enterprise.roles.write")).contentType("application/json").content("{\"name\":\"Changed\"}").header("If-Match","\"999\"").header("Idempotency-Key","stale"))
        .andExpect(status().isPreconditionFailed()).andExpect(UnifiedBusinessFailure.unified("SAMPLE_PERMISSION_STALE","The resource has changed"));
    mockMvc.perform(get(BASE+"/"+id).with(admin("enterprise.roles.read"))).andExpect(jsonPath("$.data.name").value("Operator")).andExpect(jsonPath("$.data.version").value(1));
  }
  @Test void retiredBundlesCannotExpandImplicitlyIntoCurrentRoles() throws Exception {
    retired("/api/v1/enterprises/ent-acme/capability-bundles");
    long count=db.queryForObject("select count(*) from summer_permission_role where domain_key=?",Long.class,DOMAIN.key());
    mockMvc.perform(post(BASE).with(admin("enterprise.roles.write")).contentType("application/json").content("{\"name\":\"Old bundle\",\"code\":\"old-bundle\",\"bundleId\":\"missing\"}").header("Idempotency-Key","bundle"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SAMPLE_PERMISSION_INVALID"));
    assertThat(db.queryForObject("select count(*) from summer_permission_role where domain_key=?",Long.class,DOMAIN.key())).isEqualTo(count);
  }
  @Test void rejectsCrossEnterpriseAndInactiveMembership() throws Exception {
    mockMvc.perform(get("/api/v1/enterprises/ent-other/roles").with(admin("enterprise.roles.read"))).andExpect(status().isForbidden());
    db.update("update summer_enterprise_member set status='DISABLED' where enterprise_id=? and subject=?",DOMAIN.id(),"security-admin");
    mockMvc.perform(get(BASE).with(admin("enterprise.roles.read"))).andExpect(status().isForbidden());
  }
}
