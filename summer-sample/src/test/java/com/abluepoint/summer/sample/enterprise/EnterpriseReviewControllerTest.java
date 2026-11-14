package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class EnterpriseReviewControllerTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.platform(), "platform-operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.enterprise.EnterpriseReviewController")
                && java.util.Set.of("list GET /api/v1/platform/enterprise-reviews",
                        "detail GET /api/v1/platform/enterprise-reviews/{id}",
                        "audit GET /api/v1/platform/enterprise-reviews/{id}/audit",
                        "approve POST /api/v1/platform/enterprise-reviews/{id}:approve",
                        "reject POST /api/v1/platform/enterprise-reviews/{id}:reject")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.platform(), "enterprise-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.enterprise.EnterpriseReviewController")
                && java.util.Set.of("list GET /api/v1/platform/enterprise-reviews")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mockMvc;

    @Test
    void platformOperatorListsPendingApplicationsWithoutEnterpriseContext() throws Exception {
        mockMvc.perform(get("/api/v1/platform/enterprise-reviews")
                        .with(platformAuth("platform.enterprise-review.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].applicationId").value("application-acme"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")));
    }

    @Test
    void approvesAnApplicationWithIdempotencyAndIfMatchAndWritesAudit() throws Exception {
        var auth = platformAuth("platform.enterprise-review.write");
        String etag = mockMvc.perform(get("/api/v1/platform/enterprise-reviews/{id}", "application-acme")
                        .with(platformAuth("platform.enterprise-review.read")))
                .andReturn().getResponse().getHeader("ETag");
        mockMvc.perform(post("/api/v1/platform/enterprise-reviews/{id}:approve", "application-acme")
                        .header("If-Match", etag).header("Idempotency-Key", "approve-acme-1").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(get("/api/v1/platform/enterprise-reviews/{id}/audit", "application-acme")
                        .with(platformAuth("platform.enterprise-review.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].decision").value("APPROVE"))
                .andExpect(jsonPath("$.data[0].actorSubject").value("platform-operator"));
    }

    @Test
    void rejectsWithoutReasonAndRefusesStaleConditionalWrite() throws Exception {
        mockMvc.perform(post("/api/v1/platform/enterprise-reviews/{id}:reject", "application-acme")
                        .header("If-Match", "\"stale\"").header("Idempotency-Key", "reject-acme-1")
                        .contentType("application/json").content("{\"reason\":\"\"}")
                        .with(platformAuth("platform.enterprise-review.write")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"));
    }

    @Test
    void deniesEnterpriseScopedPrincipalEvenWithEnterpriseMembership() throws Exception {
        mockMvc.perform(get("/api/v1/platform/enterprise-reviews")
                        .with(jwt().jwt(token -> token.subject("enterprise-admin").claim("scope", "platform.enterprise-review.read").claim("enterprise_memberships", java.util.List.of(
                                java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.enterprise-review.read"))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAuth(String capability) {
        return jwt().jwt(token -> token.subject("platform-operator").claim("scope", capability))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
