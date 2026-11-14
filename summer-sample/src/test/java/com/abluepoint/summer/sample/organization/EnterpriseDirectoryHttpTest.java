package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class EnterpriseDirectoryHttpTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.platform(), "directory-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.enterprise.EnterpriseDirectoryController")
                && java.util.Set.of("create POST /api/v1/platform/enterprises")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "alice", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("context PUT /api/v1/enterprises/{enterpriseId}/context")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mvc;

    @Test
    void administratorCreatesEnterpriseAndReadsItsEmptyOrganizationTree() throws Exception {
        var created = mvc.perform(post("/api/v1/platform/enterprises")
                        .with(jwt().jwt(j -> j.subject("directory-admin").claim("scope", "platform.enterprise.write"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.enterprise.write")))
                        .header("X-Request-Id", "create-tenant-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Directory tenant\",\"code\":\"directory-tenant\"}"))
                .andExpect(result -> {
                    if (result.getResolvedException() != null)
                        throw new AssertionError("Formal enterprise creation must complete its creator bootstrap", result.getResolvedException());
                })
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Directory tenant"))
                .andExpect(jsonPath("$.requestId").value("create-tenant-test"))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.enterpriseId");
        mvc.perform(get("/api/v1/enterprises/{id}/org-tree", id)
                        .with(jwt().jwt(j -> j.subject("directory-admin").claim("scope", "enterprise.organization.read")
                                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", id, "status", "ACTIVE")))
                                )
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseId").value(id))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void memberCanSelectAnOrganizationContextAndCannotSelectAnotherTenant() throws Exception {
        mvc.perform(put("/api/v1/enterprises/{id}/context", "ent-acme")
                        .with(jwt().jwt(j -> j.subject("alice").claim("scope", "enterprise.organization.read")
                                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                )
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"org-engineering\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.organizationId").value("org-engineering"));

        mvc.perform(get("/api/v1/enterprises/{id}/org-tree", "ent-other")
                        .with(jwt().jwt(j -> j.subject("alice").claim("scope", "enterprise.organization.read")
                                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                )
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"))))
                .andExpect(status().isForbidden());
    }
}
