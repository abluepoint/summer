package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(DirectoryFailureOverrideHttpTest.Policy.class)
class DirectoryFailureOverrideHttpTest {
    @Autowired MockMvc mvc;

    @Test void conditionalEnterpriseFailureUsesTheApplicationsTypedStatusPolicy() throws Exception {
        mvc.perform(post("/api/v1/platform/enterprises/ent-acme:freeze")
                        .header("If-Match", "\"stale\"").header("Idempotency-Key", "directory-policy")
                        .header("X-Request-Id", "directory-policy-request")
                        .with(jwt().jwt(token -> token.subject("directory-operator")
                                        .claim("platform_capabilities", List.of("platform.enterprise.write")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.enterprise.write"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("The enterprise has changed"))
                .andExpect(jsonPath("$.requestId").value("directory-policy-request"));
    }

    @Test void conditionalReviewFailureUsesTheApplicationsTypedStatusPolicy() throws Exception {
        mvc.perform(post("/api/v1/platform/enterprise-reviews/application-acme:approve")
                        .header("If-Match", "\"stale\"").header("Idempotency-Key", "review-policy")
                        .with(jwt().jwt(token -> token.subject("directory-operator")
                                        .claim("platform_capabilities", List.of("platform.enterprise-review.write")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.enterprise-review.write"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("The resource has changed"));
    }

    @Test void conditionalMemberFailureUsesTheApplicationsTypedStatusPolicy() throws Exception {
        mvc.perform(post("/api/v1/enterprises/ent-acme/members/member-alice:disable")
                        .header("If-Match", "\"stale\"").header("Idempotency-Key", "member-policy")
                        .with(tenant("enterprise.members.write")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("The resource has changed"));
    }

    @Test void conditionalInvitationFailureUsesTheApplicationsTypedStatusPolicy() throws Exception {
        var created = mvc.perform(post("/api/v1/enterprises/ent-acme/invitations")
                        .contentType("application/json").content("{\"inviteeEmail\":\"policy@example.test\"}")
                        .header("Idempotency-Key", "invitation-policy-create").with(tenant("enterprise.invitation.write")))
                .andExpect(status().isCreated()).andReturn();
        String id = new tools.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString())
                .path("data").path("invitationId").asString();
        mvc.perform(post("/api/v1/enterprises/ent-acme/invitations/" + id + ":resend")
                        .header("If-Match", "\"stale\"").header("Idempotency-Key", "invitation-policy")
                        .with(tenant("enterprise.invitation.write")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("The resource has changed"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor tenant(String capability) {
        return jwt().jwt(token -> token.subject("directory-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }

    @TestConfiguration
    static class Policy {
        @Bean @Primary ErrorStatusResolver directoryPolicy() {
            return new DefaultErrorStatusResolver(Map.of(SampleTransportErrorCode.PRECONDITION_FAILED, HttpStatus.valueOf(422)));
        }
    }
}
