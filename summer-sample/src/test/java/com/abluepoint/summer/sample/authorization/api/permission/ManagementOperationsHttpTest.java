package com.abluepoint.summer.sample.authorization.api.permission;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties =
        "summer.sample.authorization.managed-bootstrap-domains=PLATFORM:platform,ENTERPRISE:ent-acme")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(RolePermissionHttpTest.Schema.class)
class ManagementOperationsHttpTest {
  @Autowired HistoricalTenantPermissions historical;
  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.JdbcTemplate db;
  @Autowired tools.jackson.databind.ObjectMapper json;

  private org.springframework.test.web.servlet.request.RequestPostProcessor actor(String scope) {
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(
            token ->
                token
                    .subject("summer-user")
                    .claim("scope", scope)
                    .claim("principal_type", "USER")
                    .claim(
                        "enterprise_capabilities",
                        java.util.Map.of(
                            "ent-acme", java.util.List.of("enterprise.invitation.read"))))
        .authorities(
            java.util.Arrays.stream(scope.split(" "))
                .map(
                    value ->
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "SCOPE_" + value))
                .toArray(org.springframework.security.core.GrantedAuthority[]::new));
  }

  @Test
  void freshAdministratorCanReachTheMenuConfigurationPageWithoutAnExistingPublication() throws Exception {
    mvc.perform(get("/api/v1/enterprises/ent-acme/authorization/navigation").with(user("summer-user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[?(@.menuId == 'menu-manifest')]").isNotEmpty());
  }

  @Test
  void minimalScopeNavigationKeepsIdentityAndInterfaceChecksWithoutManagementDelegation() throws Exception {
    String base = "/api/v1/enterprises/ent-acme";
    for (String path : java.util.List.of("/authorization/navigation", "/menu-manifest")) {
      mvc.perform(get(base + path).with(actor("openid profile customer.read")))
          .andExpect(status().isOk());
    }
    mvc.perform(get(base + "/roles?catalog=permissions").with(actor("openid profile customer.read")))
        .andExpect(status().isForbidden());
    mvc.perform(get(base + "/menu-manifest/management").with(actor("openid profile customer.read")))
        .andExpect(status().isNotFound());
    String permission = db.queryForObject("select permission_id from summer_permission where domain_key='ENTERPRISE:ent-acme' and code='AUTHENTICATED'", String.class);
    String route = base + "/permissions/" + permission + "/interfaces";
    var initial = historical.readInterfaces(permission);
    java.util.List<String> original = new java.util.ArrayList<>();
    initial.body().path("resourceIds").forEach(id -> original.add(id.asText()));
    var targets = db.queryForList("select resource_id from summer_interface_resource where active=true and normalized_path in ('/api/v1/enterprises/{enterpriseId}/authorization/navigation','/api/v1/enterprises/{enterpriseId}/menu-manifest') and http_method='GET'", String.class);
    org.junit.jupiter.api.Assertions.assertEquals(2, targets.size());
    var changed = replaceOperationBindings(route, original.stream().filter(id -> !targets.contains(id)).toList(), initial.etag());
    try {
      for (String path : java.util.List.of("/authorization/navigation", "/menu-manifest")) {
        mvc.perform(get(base + path).with(actor("openid profile customer.read")))
            .andExpect(status().isForbidden());
      }
    } finally {
      replaceOperationBindings(route, original, changed);
    }
  }

  @Test
  void managedMemberReadsUseCurrentMembershipWithoutTokenBusinessClaims() throws Exception {
    mvc.perform(get("/api/v1/enterprises/ent-acme/members").with(actor("enterprise.members.read")))
        .andExpect(status().isOk());
  }

  @Test
  void organizationFullViewRequiresCurrentWriteTargetsAndWriteDelegation() throws Exception {
    String base = "/api/v1/enterprises/ent-acme";
    String scopes = "enterprise.organization.read enterprise.organization.write";
    mvc.perform(get(base + "/org-tree").with(actor(scopes)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items..id").value(org.hamcrest.Matchers.hasItem("org-operations")));
    mvc.perform(get(base + "/org-tree").with(actor("enterprise.organization.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items..id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("org-operations"))));
    String permission = db.queryForObject("select permission_id from summer_permission where domain_key='ENTERPRISE:ent-acme' and code='management-write'", String.class);
    String route = base + "/permissions/" + permission + "/interfaces";
    var initial = historical.readInterfaces(permission);
    java.util.List<String> original = new java.util.ArrayList<>();
    initial.body().path("resourceIds").forEach(id -> original.add(id.asText()));
    var targets = db.queryForList("select resource_id from summer_interface_resource where active=true and ((normalized_path='/api/v1/enterprises/{enterpriseId}/org-units' and http_method='POST') or (normalized_path='/api/v1/enterprises/{enterpriseId}/org-units/{unitId}' and http_method in ('PATCH','DELETE')) or (normalized_path='/api/v1/enterprises/{enterpriseId}/org-assignments:batch' and http_method='PUT'))", String.class);
    org.junit.jupiter.api.Assertions.assertEquals(4, targets.size());
    var changed = replaceOperationBindings(route, original.stream().filter(id -> !targets.contains(id)).toList(), initial.etag());
    try {
      mvc.perform(get(base + "/org-tree").with(actor(scopes)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.items..id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("org-operations"))));
    } finally {
      replaceOperationBindings(route, original, changed);
    }
  }

  private String replaceOperationBindings(String route, java.util.List<String> ids, String etag) throws Exception {
    if (route.startsWith("/api/v1/enterprises/ent-acme/permissions/"))
      return historical.replace(route, java.util.Map.of("resourceIds", ids), etag).etag();
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(route)
        .with(user("summer-user"))
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .header("If-Match", etag).header("Idempotency-Key", java.util.UUID.randomUUID().toString())
        .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("resourceIds", ids))))
        .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
  }

  @Test
  void managedInvitationAndApplicantRequestsRejectLegacyGrantFields() throws Exception {
    for (var body : java.util.List.of(
        java.util.Map.of("inviteeEmail", "summer-user", "capabilityCodes", java.util.List.of("customer.write")),
        java.util.Map.of("inviteeEmail", "summer-user", "roleIds", java.util.List.of("legacy-role")))) {
      mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/enterprises/ent-acme/invitations")
          .with(actor("enterprise.invitation.write"))
          .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
          .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
          .contentType("application/json").content(json.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/enterprises/ent-acme/join-applications")
        .with(actor("enterprise.join-application.submit"))
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
        .contentType("application/json").content("{\"applicantEmail\":\"summer-user\",\"capabilityCodes\":[\"customer.write\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void publicCustomerOperationHintsUseTheInstalledPlatformResourceWithoutManagementRole() throws Exception {
    String base = "/api/v1/platform";
    mvc.perform(get(base + "/authorization/operations").param("actions", "customer.create")
        .with(actor("customer.write"))).andExpect(status().isOk());
    String target = db.queryForObject("select resource_id from summer_interface_resource where normalized_path='/api/customers' and http_method='POST' and active=true", String.class);
    String group = db.queryForObject("select group_id from summer_permission where domain_key='PLATFORM:platform' and code='PUBLIC'", String.class);
    var created = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base + "/permissions")
        .with(user("summer-user"))
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .header("Idempotency-Key", java.util.UUID.randomUUID().toString()).contentType("application/json")
        .content(json.writeValueAsString(java.util.Map.of("code", "public-customer-hint", "name", "Public customer hint", "type", "INTERFACE", "accessMode", "PUBLIC", "groupId", group))))
        .andExpect(status().isCreated()).andReturn().getResponse();
    String permission = json.readTree(created.getContentAsString()).path("data").path("permissionId").asText();
    replaceOperationBindings(base + "/permissions/" + permission + "/interfaces", java.util.List.of(target), "\"0\"");
    String username = "hint-user-" + java.util.UUID.randomUUID();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users")
        .with(actor("enterprise.members.write"))
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .contentType("application/json")
        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", "test-only-hint-fixture-123", "role", "USER"))))
        .andExpect(status().isOk());
    try {
      mvc.perform(get(base + "/authorization/operations").param("actions", "customer.create")
          .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
              .jwt(t -> t.subject(username).claim("scope", "customer.write"))
              .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.write"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.actions['customer.create']").value(true));
    } finally {
      replaceOperationBindings(base + "/permissions/" + permission + "/interfaces", java.util.List.of(), "\"1\"");
    }
  }

  @Test
  void platformAuditWithEnterpriseVariableRemainsInPlatformDomain() throws Exception {
    mvc.perform(
            get("/api/v1/platform/enterprises/ent-acme/audit")
                .with(actor("platform.enterprise.read")))
        .andExpect(status().isOk());
  }

  @Test
  void globalIdentityManagementUsesPlatformInterfaceGrantAndOriginalDelegation() throws Exception {
    mvc.perform(get("/api/users").with(actor("enterprise.members.read")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/users").with(actor("customer.read"))).andExpect(status().isForbidden());
  }

  @Test
  void invitationReadStillRequiresItsOwnDelegatedScope() throws Exception {
    mvc.perform(
            get("/api/v1/enterprises/ent-acme/invitations").with(actor("enterprise.roles.read")))
        .andExpect(status().isForbidden());
  }

  @Test
  void writeOnlyDelegateCanReadItsOperationHintsWithoutManagementReadScope() throws Exception {
    mvc.perform(
            get("/api/v1/platform/authorization/operations")
                .param("actions", "menu.layout.write")
                .with(actor("platform.capabilities.write")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['menu.layout.write']").value(true));
    mvc.perform(
            get("/api/v1/platform/authorization/operations")
                .param("actions", "menu.layout.write")
                .with(actor("platform.capabilities.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['menu.layout.write']").value(false));
  }

  @Test
  void legacyGrantWritesAreRejectedWithoutCreatingASecondAuthorizationFact() throws Exception {
    int before =
        db.queryForObject("select count(*) from summer_authorization_capability", Integer.class);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/enterprises/ent-acme/capabilities")
                .with(actor("enterprise.capabilities.write"))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType("application/json")
                .content("{\"code\":\"obsolete\",\"resource\":\"x\",\"action\":\"read\"}"))
        .andExpect(status().isBadRequest());
    org.junit.jupiter.api.Assertions.assertEquals(
        before,
        db.queryForObject("select count(*) from summer_authorization_capability", Integer.class));
  }

  @Test
  void invitationManagementUsesCurrentInterfaceGrantWithoutTokenBusinessCodes() throws Exception {
    mvc.perform(
            get("/api/v1/enterprises/ent-acme/invitations")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(
                            jwt ->
                                jwt.subject("summer-user")
                                    .claim("scope", "enterprise.invitation.read"))
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "SCOPE_enterprise.invitation.read"))))
        .andExpect(status().isOk());
  }

  @Test
  void qualificationRejectsUnknownActions() throws Exception {
    mvc.perform(
            get("/api/v1/enterprises/ent-acme/authorization/operations")
                .param("actions", "not.installed")
                .with(user("summer-user")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void managementReadDoesNotImplyAnUnassociatedMenuWriteOperation() throws Exception {
    String base = "/api/v1/platform";
    String permission =
        db.queryForObject(
            "select permission_id from summer_permission where domain_key='PLATFORM:platform' and"
                + " code='management-write'",
            String.class);
    String route = base + "/permissions/" + permission + "/interfaces";
    var initial =
        mvc.perform(get(route).with(user("summer-user")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var ids = json.readTree(initial.getContentAsString()).path("data").path("resourceIds");
    String target =
        db.queryForObject(
            "select resource_id from summer_interface_resource where"
                + " normalized_path='/api/v1/platform/authorization/menu-layout'"
                + " and http_method='PATCH' and active=true",
            String.class);
    java.util.List<String> narrowed = new java.util.ArrayList<>();
    ids.forEach(
        id -> {
          if (!target.equals(id.asText())) narrowed.add(id.asText());
        });
    var changed = replaceOperationBindings(route, narrowed, initial.getHeader("ETag"));
    try {
      mvc.perform(get(base + "/authorization/menu-layout").with(user("summer-user")))
          .andExpect(status().isOk());
      mvc.perform(
              get(base + "/authorization/operations")
                  .param("actions", "menu.layout.write")
                  .with(user("summer-user")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.actions['menu.layout.write']").value(false));
    } finally {
      java.util.List<String> original = new java.util.ArrayList<>();
      ids.forEach(id -> original.add(id.asText()));
      replaceOperationBindings(route, original, changed);
    }
    mvc.perform(
            get(base + "/authorization/operations")
                .param("actions", "menu.layout.write")
                .with(user("summer-user")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actions['menu.layout.write']").value(true));
  }
}
