package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.security.storage.keys=memory")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataScopeHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void policyIsConfiguredAndInjectedIntoParameterListDetailAndWrites() throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operator\":\"AND\",\"rules\":[{\"type\":\"SELF\"}]}")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource").value("system-parameters"));

        create("owned.by.alice", "alice", "alice");
        create("owned.by.bob", "bob", "bob");

        mvc.perform(get("/api/system-parameters").with(reader("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].key").value("owned.by.alice"));
        mvc.perform(get("/api/system-parameters/owned.by.bob").with(reader("alice")))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/system-parameters/owned.by.bob")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"blocked\",\"version\":0}")
                        .with(writer("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingPolicyDeniesSensitiveScopeEndpointAndCrossTenantIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/enterprises/ent-other/data-scopes")
                        .with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/data-scopes/system-parameters")
                        .with(member("reader", "enterprise.data-scope.read")))
                .andExpect(status().isOk());
    }

    private void create(String key, String creator, String owner) throws Exception {
        mvc.perform(post("/api/system-parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + key + "\",\"type\":\"STRING\",\"value\":\"v\",\"createdBy\":\"" + creator + "\",\"ownerId\":\"" + owner + "\",\"organizationId\":\"org-engineering\"}")
                        .with(writer(creator)))
                .andExpect(status().isCreated());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return member("admin", "enterprise.data-scope.read", "enterprise.data-scope.write", "system.parameter.read", "system.parameter.write");
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor reader(String subject) {
        return member(subject, "system.parameter.read");
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer(String subject) {
        return member(subject, "system.parameter.write");
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor member(String subject, String... capabilities) {
        return jwt().jwt(j -> j.subject(subject)
                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", java.util.Map.of("ent-acme", java.util.List.of("enterprise.data-scope.read", "enterprise.data-scope.write"))))
                .authorities(java.util.Arrays.stream(capabilities).map(value -> new SimpleGrantedAuthority("SCOPE_" + value)).toArray(SimpleGrantedAuthority[]::new));
    }
}
