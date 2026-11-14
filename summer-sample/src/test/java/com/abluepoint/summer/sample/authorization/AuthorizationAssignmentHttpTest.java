package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.jayway.jsonpath.JsonPath;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Import(CurrentManagedAuthorizationTestFixture.class)
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationAssignmentHttpTest {
  static final Domain DOMAIN=Domain.platform();
  static final String BASE="/api/v1/platform";
  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CurrentManagedAuthorizationTestFixture fixture;
  @Autowired AuthorizationAuditStore audit;
  String permission;
  @BeforeEach void currentFacts() {
    fixture.grantHandlers(DOMAIN,"security-admin",(h,p,m)->p.startsWith(BASE+"/roles") || p.startsWith(BASE+"/users/"));
    permission=fixture.grantHandlers(DOMAIN,"fixture-holder",(h,p,m)->m.equals("GET") && p.equals("/api/customers"));
    fixture.activeUser(DOMAIN,"alice"); fixture.activeUser(DOMAIN,"bob");
  }
  private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
    return jwt().jwt(t->t.subject("security-admin").claim("scope","platform.capabilities.read platform.capabilities.write"));
  }
  private org.springframework.test.web.servlet.request.RequestPostProcessor user(String name,String scope) {
    return jwt().jwt(t->t.subject(name).claim("scope",scope)).authorities(new SimpleGrantedAuthority("SCOPE_"+scope));
  }
  private String createRole() throws Exception {
    var result=mockMvc.perform(post(BASE+"/roles").with(admin()).contentType("application/json")
        .content("{\"name\":\"Customer reader\",\"code\":\"customer-reader\",\"permissionIds\":[\""+permission+"\"]}").header("Idempotency-Key","role"))
        .andExpect(status().isCreated()).andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(),"$.data.roleId");
  }
  private void assign(String user,String role) throws Exception {
    mockMvc.perform(put(BASE+"/users/"+user+"/roles").with(admin()).contentType("application/json")
        .content("{\"roleIds\":[\""+role+"\"]}").header("If-Match","\"0\"").header("Idempotency-Key","assign-"+user))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.roleIds[0]").value(role));
  }
  @Test void roleBindingToScannedResourceAllowsUserAndAuditExplainsDecision() throws Exception {
    String role=createRole(); assign("alice",role);
    assertThat(jdbc.queryForList("select role_id from summer_permission_user_role where domain_key=? and username=?",String.class,DOMAIN.key(),"alice")).containsExactly(role);
    mockMvc.perform(get("/api/customers").with(user("alice","customer.read")).header("X-Request-Id","assignment-allow")).andExpect(status().isOk());
    assertThat(audit.read(DOMAIN,"alice")).anySatisfy(row->{
      assertThat(row).containsEntry("decision","ALLOW").containsEntry("requestId","assignment-allow").containsEntry("reasonCode","CURRENT_PERMISSION_ALLOW");
      assertThat(row.get("resourceId")).isNotNull(); assertThat(row.get("authorizationRevision")).isInstanceOf(Number.class);
    });
  }
  @Test void retiredExplicitDenyCannotMutateRolesAndCurrentRevocationFailsClosed() throws Exception {
    String role=createRole(); assign("bob",role);
    long legacyBindings=jdbc.queryForObject("select count(*) from summer_authorization_user_binding",Long.class);
    mockMvc.perform(put(BASE+"/users/bob/roles").with(admin()).contentType("application/json")
        .content("{\"roleIds\":[],\"deniedCapabilityCodes\":[\"GET:/api/customers\"]}").header("If-Match","\"1\"").header("Idempotency-Key","old-deny"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SAMPLE_PERMISSION_INVALID"));
    assertThat(jdbc.queryForList("select role_id from summer_permission_user_role where domain_key=? and username=?",String.class,DOMAIN.key(),"bob")).containsExactly(role);
    assertThat(jdbc.queryForObject("select count(*) from summer_authorization_user_binding",Long.class)).isEqualTo(legacyBindings);
    var sameJwt=user("bob","customer.read");
    mockMvc.perform(get("/api/customers").with(sameJwt)).andExpect(status().isOk());
    mockMvc.perform(put(BASE+"/users/bob/roles").with(admin()).contentType("application/json").content("{\"roleIds\":[]}")
        .header("If-Match","\"1\"").header("Idempotency-Key","revoke")).andExpect(status().isOk());
    mockMvc.perform(get("/api/customers").with(sameJwt).header("X-Request-Id","assignment-revoked")).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/customers").with(user("bob","customer.write")).contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    assertThat(audit.read(DOMAIN,"bob")).anySatisfy(row->assertThat(row).containsEntry("decision","DENY").containsEntry("requestId","assignment-revoked"));
  }
  @Test void assignmentBoundaryRejectsUnauthenticatedAndStaleWrites() throws Exception {
    mockMvc.perform(get("/api/customers")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));
    mockMvc.perform(put(BASE+"/users/alice/roles").with(admin()).contentType("application/json").content("{\"roleIds\":[]}")
        .header("If-Match","\"99\"").header("Idempotency-Key","stale"))
        .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.error.code").value("SAMPLE_PERMISSION_STALE"));
    assertThat(jdbc.queryForList("select role_id from summer_permission_user_role where domain_key=? and username=?",String.class,DOMAIN.key(),"alice")).isEmpty();
  }
}
