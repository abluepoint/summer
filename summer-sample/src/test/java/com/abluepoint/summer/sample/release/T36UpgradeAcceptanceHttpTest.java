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
class T36UpgradeAcceptanceHttpTest {

    @Autowired
    MockMvc mockMvc;

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
                .andExpect(jsonPath("$.data.decision").value("READY"))
                .andExpect(jsonPath("$.data.gates[*].name", containsInAnyOrder(
                        "migration", "configuration", "keys", "authorization", "tasks",
                        "cache", "events", "realtime", "plugins", "ui")))
                .andExpect(jsonPath("$.data.gates[*].executed").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))))
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
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.checks[*].name", containsInAnyOrder(
                        "iam", "connectionService", "database", "auditLedger", "migrations", "runbook")))
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operator(String scope) {
        return jwt().jwt(token -> token.subject("t36-release-operator")
                        .claim("release_operator", true)
                        .claim("service_trust_verified", true))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
