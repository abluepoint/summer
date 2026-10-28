package com.abluepoint.summer.sample.organization.api;

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
class EnterpriseDirectoryHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void administratorCreatesEnterpriseAndReadsItsEmptyOrganizationTree() throws Exception {
        var created = mvc.perform(post("/api/v1/platform/enterprises")
                        .with(jwt().jwt(j -> j.subject("directory-admin").claim("platform_capabilities", java.util.List.of("platform.enterprise.write")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.enterprise.write")))
                        .header("X-Request-Id", "create-tenant-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Directory tenant\",\"code\":\"directory-tenant\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Directory tenant"))
                .andExpect(jsonPath("$.requestId").value("create-tenant-test"))
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.enterpriseId");
        mvc.perform(get("/api/v1/enterprises/{id}/org-tree", id)
                        .with(jwt().jwt(j -> j.subject("directory-admin")
                                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", id, "status", "ACTIVE")))
                                .claim("enterprise_capabilities", java.util.Map.of(id, java.util.List.of("enterprise.organization.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseId").value(id))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void memberCanSelectAnOrganizationContextAndCannotSelectAnotherTenant() throws Exception {
        mvc.perform(put("/api/v1/enterprises/{id}/context", "ent-acme")
                        .with(jwt().jwt(j -> j.subject("alice")
                                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                .claim("enterprise_capabilities", java.util.Map.of("ent-acme", java.util.List.of("enterprise.organization.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"org-engineering\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.organizationId").value("org-engineering"));

        mvc.perform(get("/api/v1/enterprises/{id}/org-tree", "ent-other")
                        .with(jwt().jwt(j -> j.subject("alice")
                                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                .claim("enterprise_capabilities", java.util.Map.of("ent-acme", java.util.List.of("enterprise.organization.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"))))
                .andExpect(status().isForbidden());
    }
}
