package com.abluepoint.summer.sample.release;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class ReleaseFailureHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach void currentReleaseResources() {
        for (String subject : java.util.List.of("release-contract-operator", "user"))
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler,path,method) -> handler.getBeanType() == ReleaseGateController.class
                            && java.util.Set.of("/internal/v1/release/gates:validate", "/internal/v1/release/readiness").contains(path));
    }

    @Autowired MockMvc mvc;

    @Test
    void validationAndIdempotencyKeepDefaultMessagesAndTheOriginalReceipt() throws Exception {
        String path = "/internal/v1/release/gates:validate";
        mvc.perform(post(path).with(operator()).header("Idempotency-Key", "release-invalid")
                        .header("X-Request-Id", "release-invalid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"candidate\",\"dryRun\":false}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("X-Request-Id", "release-invalid"))
                .andExpect(jsonPath("$.error.code").value("IAM_RELEASE_INVALID"))
                .andExpect(jsonPath("$.error.message").value("dryRun is required"))
                .andExpect(jsonPath("$.requestId").value("release-invalid"));
        String original = "{\"releaseVersion\":\"candidate\",\"dryRun\":true,\"requiredChecks\":[\"unit\"]}";
        mvc.perform(post(path).with(operator()).header("Idempotency-Key", "release-contract-replay")
                        .contentType(MediaType.APPLICATION_JSON).content(original)).andExpect(status().isOk());
        mvc.perform(post(path).with(operator()).header("Idempotency-Key", "release-contract-replay")
                        .header("X-Request-Id", "release-conflict").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"other\",\"dryRun\":true}"))
                .andExpect(status().isConflict()).andExpect(header().string("X-Request-Id", "release-conflict"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Idempotency key reused"))
                .andExpect(jsonPath("$.requestId").value("release-conflict"));
        mvc.perform(post(path).with(operator()).header("Idempotency-Key", "release-contract-replay")
                        .contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.releaseVersion").value("candidate"));
    }

    @Test
    void nativeOperatorRejectionKeepsItsPublicMessage() throws Exception {
        mvc.perform(get("/internal/v1/release/readiness").header("X-Request-Id", "release-denied")
                        .with(jwt().jwt(claims -> claims.claim("scope", "openid")).authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"))))
                .andExpect(status().isForbidden()).andExpect(header().string("X-Request-Id", "release-denied"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Release operator denied"))
                .andExpect(jsonPath("$.requestId").value("release-denied"));
    }

    private RequestPostProcessor operator() {
        return jwt().jwt(token -> token.subject("release-contract-operator").claim("release_operator", true).claim("scope", "internal.release.write"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.write"));
    }
}
