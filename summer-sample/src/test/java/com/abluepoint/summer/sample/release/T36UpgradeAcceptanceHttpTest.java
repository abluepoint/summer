package com.abluepoint.summer.sample.release;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T36 acceptance seam: exercise the real release-control HTTP contract used
 * by the operational gate script. Module-specific integration tests remain
 * the evidence for the underlying migration, cache, event and realtime work.
 */
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class T36UpgradeAcceptanceHttpTest {

    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach
    void currentReleaseResources() {
        for (String subject : java.util.List.of("t36-release-operator", "t36-invalid-claim"))
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType() == ReleaseGateController.class
                            && java.util.Set.of("/internal/v1/release/readiness", "/internal/v1/release/gates:validate", "/internal/v1/release/rollback:dry-run").contains(path));
    }


    @Autowired
    MockMvc mockMvc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    RuntimeReleaseChecks runtimeChecks;

    @Test
    void validatesTheCrossModuleReleaseMatrixWithCorrelationAndIdempotency() throws Exception {
        String body = "{\"releaseVersion\":\"summer4-t36\",\"dryRun\":true,"
                + "\"requiredChecks\":[\"migration\",\"configuration\",\"keys\","
                + "\"authorization\",\"tasks\",\"cache\",\"events\",\"realtime\","
                + "\"plugins\",\"ui\"]}";
        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "t36-release-matrix")
                        .header("X-Request-Id", "t36-release-request")
                        .content(body)
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "t36-release-request"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.data.gates[*].name", containsInAnyOrder(
                        "migration", "configuration", "keys", "authorization", "tasks",
                        "cache", "events", "realtime", "plugins", "ui")))
                .andExpect(jsonPath("$.data.gates[*].executed").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))))
                .andExpect(jsonPath("$.error").doesNotExist());

        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "t36-release-matrix")
                        .content(body)
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseVersion").value("summer4-t36"));
    }

    @Test
    void keepsReadinessAndRollbackProtectedAndUsesFailureEnvelopeForInvalidInput() throws Exception {
        mockMvc.perform(get("/internal/v1/release/readiness")
                        .header("X-Request-Id", "t36-readiness-request")
                        .with(operator("internal.release.read")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "t36-readiness-request"))
                .andExpect(jsonPath("$.data.status").value("NOT_READY"))
                .andExpect(jsonPath("$.data.checks[*].name", containsInAnyOrder(
                        "database", "migrations", "configuration", "keys", "authorization", "tasks", "cache", "events", "realtime", "plugins")))
                .andExpect(jsonPath("$.data.rollbackDryRunSupported").value(true));

        mockMvc.perform(post("/internal/v1/release/rollback:dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "t36-rollback-plan")
                        .content("{\"releaseVersion\":\"summer4-t36\",\"targetVersion\":\"summer4-t35\",\"reason\":\"gate failure\"}")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.currentRelease").value("aict-17"));

        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "t36-invalid-gate")
                        .content("{\"releaseVersion\":\"summer4-t36\",\"dryRun\":false}")
                        .with(operator("internal.release.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IAM_RELEASE_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(get("/internal/v1/release/readiness")
                        .with(jwt().jwt(token -> token.subject("t36-viewer"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void unexpectedReadinessFailureDoesNotExposeInternalDetails() throws Exception {
        var oldClaim = jwt().jwt(token -> token.subject("t36-invalid-claim")
                        .claim("scope", "internal.release.read")
                        .claim("release_operator", java.util.Map.of("value", "t36-private-canary")))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"));
        mockMvc.perform(get("/internal/v1/release/readiness").with(oldClaim))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        org.mockito.Mockito.clearInvocations(runtimeChecks);
        org.mockito.Mockito.doThrow(new IllegalStateException("t36-private-canary"))
                .when(runtimeChecks).snapshot();
        try {
        mockMvc.perform(get("/internal/v1/release/readiness")
                        .header("X-Request-Id", "t36-safe-error")
                        .with(oldClaim))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "t36-safe-error"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.requestId").value("t36-safe-error"));
        org.mockito.Mockito.verify(runtimeChecks).snapshot();
        } finally {
            org.mockito.Mockito.reset(runtimeChecks);
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operator(String scope) {
        return jwt().jwt(token -> token.subject("t36-release-operator")
                        .claim("scope", scope).claim("release_operator", true)
                        .claim("service_trust_verified", true))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
