package com.abluepoint.summer.sample.release;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.everyItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class ReleaseGateControllerTest {

    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach
    void currentReleaseResources() {
        for (String subject : java.util.List.of("release-operator"))
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType() == ReleaseGateController.class
                            && java.util.Set.of("/internal/v1/release/performance-budgets", "/internal/v1/release/readiness", "/internal/v1/release/performance-benchmarks", "/internal/v1/release/gates:validate", "/internal/v1/release/rollback:dry-run", "/internal/v1/release/failure-drills").contains(path));
    }


    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesDeclaredPerformanceBudgetsAndNonProductionMode() throws Exception {
        mockMvc.perform(get("/internal/v1/release/performance-budgets").with(operator("internal.release.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("NON_PRODUCTION_IN_MEMORY"))
                .andExpect(jsonPath("$.data.budgets.managementListP95Ms").value(500))
                .andExpect(jsonPath("$.data.budgets.cachedAuthorizationP95Ms").value(20))
                .andExpect(jsonPath("$.data.budgets.mcpDecisionP95Ms").value(30))
                .andExpect(jsonPath("$.data.budgets.authorizationDecisionP95Ms").value(50))
                .andExpect(jsonPath("$.data.budgets.permitClaimP95Ms").value(20))
                .andExpect(jsonPath("$.data.budgets.auditWriteP95Ms").value(100))
                .andExpect(jsonPath("$.data.excludesExternalToolExecution").value(true));
    }

    @Test
    void readinessReportsDependencyHealthAndRequiredReleaseArtifacts() throws Exception {
        mockMvc.perform(get("/internal/v1/release/readiness").with(operator("internal.release.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_READY"))
                .andExpect(jsonPath("$.data.checks[*].name", containsInAnyOrder("database", "migrations", "configuration", "keys", "authorization", "tasks", "cache", "events", "realtime", "plugins")))
                .andExpect(jsonPath("$.data.checks[?(@.name == 'configuration')].passed", everyItem(is(false))))
                .andExpect(jsonPath("$.data.rollbackDryRunSupported").value(true));
    }

    @Test
    void validatesSeparateCiGatesAndRequiresDryRunForRelease() throws Exception {
        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"aict-17\",\"dryRun\":true,\"requiredChecks\":[\"format\",\"lint\",\"typecheck\",\"unit\",\"bff-contract\",\"build\",\"e2e\"]}")
                        .header("Idempotency-Key", "release-gates-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("NOT_VERIFIED"))
                .andExpect(jsonPath("$.data.releaseVersion").value("aict-17"))
                .andExpect(jsonPath("$.data.gates[*].name", containsInAnyOrder("format", "lint", "typecheck", "unit", "bff-contract", "build", "e2e")))
                .andExpect(jsonPath("$.data.gates[*].executed").value(everyItem(is(false))))
                .andExpect(jsonPath("$.data.dryRun").value(true));
        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"aict-17\",\"dryRun\":false,\"requiredChecks\":[\"unit\"]}")
                        .header("Idempotency-Key", "release-gates-no-dry-run")
                        .with(operator("internal.release.write")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replaysIdenticalGateValidationAndRejectsAReusedKeyWithDifferentMeaning() throws Exception {
        String body = "{\"releaseVersion\":\"aict-17\",\"dryRun\":true,\"requiredChecks\":[\"unit\"]}";
        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "release-gates-replay")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "release-gates-replay")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/v1/release/gates:validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"aict-18\",\"dryRun\":true,\"requiredChecks\":[\"unit\"]}")
                        .header("Idempotency-Key", "release-gates-replay")
                        .with(operator("internal.release.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"));
    }

    @Test
    void rollbackDryRunIsIdempotentAndDoesNotChangeCurrentRelease() throws Exception {
        String body = "{\"releaseVersion\":\"aict-17\",\"targetVersion\":\"aict-16\",\"reason\":\"failed smoke\"}";
        mockMvc.perform(post("/internal/v1/release/rollback:dry-run")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "rollback-dry-run-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.wouldSwitchFrom").value("aict-17"))
                .andExpect(jsonPath("$.data.wouldSwitchTo").value("aict-16"))
                .andExpect(jsonPath("$.data.currentRelease").value("aict-17"));
        mockMvc.perform(post("/internal/v1/release/rollback:dry-run")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "rollback-dry-run-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentRelease").value("aict-17"));
    }

    @Test
    void rejectsConflictingIdempotencyPayloadsForRollbackAndFailureDrill() throws Exception {
        mockMvc.perform(post("/internal/v1/release/rollback:dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"aict-17\",\"targetVersion\":\"aict-16\"}")
                        .header("Idempotency-Key", "rollback-conflict-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/v1/release/rollback:dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseVersion\":\"aict-17\",\"targetVersion\":\"aict-15\"}")
                        .header("Idempotency-Key", "rollback-conflict-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/internal/v1/release/failure-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"component\":\"IAM\",\"scenario\":\"UNAVAILABLE\"}")
                        .header("Idempotency-Key", "drill-conflict-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/v1/release/failure-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"component\":\"CONNECTION_SERVICE\",\"scenario\":\"UNAVAILABLE\"}")
                        .header("Idempotency-Key", "drill-conflict-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isConflict());
    }

    @Test
    void failureDrillsFailClosedForIamAndConnectionServiceOutages() throws Exception {
        mockMvc.perform(post("/internal/v1/release/failure-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"component\":\"IAM\",\"scenario\":\"UNAVAILABLE\"}")
                        .header("Idempotency-Key", "failure-iam-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationDecision").value("DENY"))
                .andExpect(jsonPath("$.data.auditEvidence").value("REQUIRED"))
                .andExpect(jsonPath("$.data.retry").value(false));
        mockMvc.perform(post("/internal/v1/release/failure-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"component\":\"CONNECTION_SERVICE\",\"scenario\":\"UNAVAILABLE\"}")
                        .header("Idempotency-Key", "failure-connection-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionStatus").value("EXECUTION_UNKNOWN"))
                .andExpect(jsonPath("$.data.automaticRetry").value(false))
                .andExpect(jsonPath("$.data.auditEvidence").value("REQUIRED"));
    }

    @Test
    void rejectsUnknownFailureScenarioAndUnauthorizedReleaseOperator() throws Exception {
        mockMvc.perform(post("/internal/v1/release/failure-drills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"component\":\"IAM\",\"scenario\":\"UNKNOWN\"}")
                        .header("Idempotency-Key", "failure-unknown-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/internal/v1/release/readiness")
                        .with(jwt().jwt(t -> t.subject("viewer"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void executesPerformanceBenchmarkAndComparesP95AndThroughputToBudget() throws Exception {
        mockMvc.perform(post("/internal/v1/release/performance-benchmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"cachedAuthorization\",\"latenciesMs\":[5,10,20,40,50],\"elapsedMs\":1000,\"successful\":5,\"failed\":0}")
                        .header("Idempotency-Key", "benchmark-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation").value("cachedAuthorization"))
                .andExpect(jsonPath("$.data.p95Ms").value(50))
                .andExpect(jsonPath("$.data.throughputPerSecond").value(5.0))
                .andExpect(jsonPath("$.data.budgetMs").value(20))
                .andExpect(jsonPath("$.data.budgetPassed").value(false))
                .andExpect(jsonPath("$.data.failureReason").value("P95_BUDGET_EXCEEDED"));
    }

    @Test
    void benchmarkReportsFailureReasonWhenSamplesContainErrors() throws Exception {
        mockMvc.perform(post("/internal/v1/release/performance-benchmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"mcpDecision\",\"latenciesMs\":[8,9],\"elapsedMs\":1000,\"successful\":1,\"failed\":1}")
                        .header("Idempotency-Key", "benchmark-failure-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.p95Ms").value(9))
                .andExpect(jsonPath("$.data.throughputPerSecond").value(1.0))
                .andExpect(jsonPath("$.data.budgetPassed").value(false))
                .andExpect(jsonPath("$.data.failureReason").value("SAMPLE_FAILURES"));
    }

    @Test
    void benchmarkRequiresIdempotencyKeyAndRejectsUnsupportedOperationWithErrorEnvelope() throws Exception {
        mockMvc.perform(post("/internal/v1/release/performance-benchmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"mcpDecision\",\"latenciesMs\":[1],\"elapsedMs\":1000,\"successful\":1,\"failed\":0}")
                        .with(operator("internal.release.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IAM_RELEASE_INVALID"));
        mockMvc.perform(post("/internal/v1/release/performance-benchmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":\"unknown\",\"latenciesMs\":[1],\"elapsedMs\":1000,\"successful\":1,\"failed\":0}")
                        .header("Idempotency-Key", "benchmark-unsupported-1")
                        .with(operator("internal.release.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IAM_RELEASE_INVALID"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operator(String scope) {
        return jwt().jwt(token -> token.subject("release-operator")
                        .claim("scope", scope).claim("release_operator", true)
                        .claim("service_trust_verified", true))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
}
