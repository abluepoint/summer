package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class PlatformEnterpriseDirectoryControllerTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.platform(), "platform-operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.enterprise.PlatformEnterpriseDirectoryController")
                && java.util.Set.of("list GET /api/v1/platform/enterprises",
                        "transition POST /api/v1/platform/enterprises/{id}:freeze")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.platform(), "platform-operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.enterprise.EnterpriseDirectoryController")
                && java.util.Set.of("create POST /api/v1/platform/enterprises")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.platform(), "enterprise-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.enterprise.PlatformEnterpriseDirectoryController")
                && java.util.Set.of("list GET /api/v1/platform/enterprises")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mockMvc;

    @Test
    void platformOperatorReadsEnterpriseDirectoryAndSummaryWithFilters() throws Exception {
        mockMvc.perform(get("/api/v1/platform/enterprises")
                        .param("status", "待审核")
                        .with(platformAuth("platform.enterprise.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.summary.total").value(1))
                .andExpect(jsonPath("$.data.summary.pending").value(1))
                .andExpect(jsonPath("$.data.items[0].enterpriseId").value("ent-acme"));
    }

    @Test
    void platformOperatorCanFreezeWithIfMatchAndIdempotency() throws Exception {
        String revision = mockMvc.perform(get("/api/v1/platform/enterprises")
                        .with(platformAuth("platform.enterprise.read")))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"enterpriseId\\\":\\\"ent-acme\\\".*?\\\"revision\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/v1/platform/enterprises/{id}:freeze", "ent-acme")
                        .header("If-Match", "\"" + revision + "\"")
                        .header("Idempotency-Key", "freeze-ent-acme-1")
                        .with(platformAuth("platform.enterprise.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已冻结"));

        mockMvc.perform(post("/api/v1/platform/enterprises/{id}:freeze", "ent-acme")
                        .header("If-Match", "\"" + revision + "\"")
                        .header("Idempotency-Key", "freeze-ent-acme-1")
                        .with(platformAuth("platform.enterprise.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("已冻结"));
    }

    @Test
    void enterpriseScopedPrincipalCannotReadPlatformDirectory() throws Exception {
        mockMvc.perform(get("/api/v1/platform/enterprises")
                        .with(jwt().jwt(token -> token.subject("enterprise-admin").claim("scope", "platform.enterprise.read")
                                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme")))
                                )
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.enterprise.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCreatedEnterpriseIsVisibleInThePlatformDirectory() throws Exception {
        var created = mockMvc.perform(post("/api/v1/platform/enterprises").with(platformAuth("platform.enterprise.write"))
                        .contentType("application/json").content("{\"name\":\"Created directory enterprise\",\"code\":\"created-directory-enterprise\"}"))
                .andExpect(result -> {
                    if (result.getResolvedException() != null)
                        throw new AssertionError("Formal enterprise creation must complete its creator bootstrap", result.getResolvedException());
                })
                .andExpect(status().isCreated()).andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.enterpriseId");
        mockMvc.perform(get("/api/v1/platform/enterprises").param("search", "Created directory enterprise")
                        .with(platformAuth("platform.enterprise.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].enterpriseId").value(id));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAuth(String capability) {
        return jwt().jwt(token -> token.subject("platform-operator").claim("scope", capability))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
