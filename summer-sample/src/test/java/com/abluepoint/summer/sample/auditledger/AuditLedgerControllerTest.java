package com.abluepoint.summer.sample.auditledger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuditLedgerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test void auditAdminDenialsKeepTheirOriginalMessageCauseAndEnvelope() throws Exception {
        for (String suffix : List.of("audit-events", "audit-events:query", "audit-events:export", "audit-events/missing",
                "mcp-invocations", "mcp-invocations/missing", "audit-anomalies", "mcp-invocations/missing:mark-unknown")) {
            String path = "/api/v1/enterprises/ent-acme/" + suffix;
            var request = suffix.endsWith(":mark-unknown") ? post(path) : get(path);
            mockMvc.perform(request.with(jwt().jwt(t -> t.subject("audit-native-no-cap").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))).header("X-Request-Id", "audit-admin-denied"))
                    .andExpect(result -> deniedOrigin(result, "Audit capability denied"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                    .andExpect(jsonPath("$.error.message").value("Audit capability denied"))
                    .andExpect(jsonPath("$.requestId").value("audit-admin-denied"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Request-Id", "audit-admin-denied"))
                    .andExpect(jsonPath("$.error.cause").doesNotExist()).andExpect(AuditLedgerControllerTest::nativeDeniedCause);
        }
    }

    @Test void auditServiceDenialsKeepTheirOriginalMessageCauseAndEnvelope() throws Exception {
        for (String path : List.of("/internal/v1/audit-events", "/internal/v1/mcp/invocations/missing:complete",
                "/internal/v1/mcp/invocations/missing:reconcile")) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")
                            .header("Idempotency-Key", "audit-denied").header("X-Request-Id", "audit-service-denied").with(jwt()))
                    .andExpect(result -> deniedOrigin(result, "Audit service trust denied"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                    .andExpect(jsonPath("$.error.message").value("Audit service trust denied"))
                    .andExpect(jsonPath("$.requestId").value("audit-service-denied"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Request-Id", "audit-service-denied"))
                    .andExpect(jsonPath("$.error.cause").doesNotExist()).andExpect(AuditLedgerControllerTest::nativeDeniedCause);
        }
    }

    private static void deniedOrigin(MvcResult result, String expected) {
        Throwable failure = result.getResolvedException();
        if (failure instanceof com.abluepoint.summer.common.error.SummerException summer) failure = summer.getCause();
        var nativeFailure = org.junit.jupiter.api.Assertions.assertInstanceOf(
                org.springframework.security.access.AccessDeniedException.class, failure);
        org.junit.jupiter.api.Assertions.assertEquals(expected, nativeFailure.getMessage());
    }

    private static void nativeDeniedCause(MvcResult result) {
        var error = org.junit.jupiter.api.Assertions.assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class,
                result.getResolvedException());
        org.junit.jupiter.api.Assertions.assertSame(com.abluepoint.summer.common.error.CommonErrorCode.FORBIDDEN, error.errorCode());
        var cause = org.junit.jupiter.api.Assertions.assertInstanceOf(org.springframework.security.access.AccessDeniedException.class,
                error.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(cause.getMessage(), error.explicitMessage());
    }

    @Test
    void invalidAuditRequestsKeepTheirOriginalMessagesAndCorrelation() throws Exception {
        for (String[] example : new String[][]{
                {"/internal/v1/audit-events", "missing", "Idempotency-Key is required"},
                {"/internal/v1/audit-events", "present", "eventId, eventType, enterpriseId and invocationId are required"},
                {"/internal/v1/mcp/invocations/absent:complete", "present", "resultStatus and resultDigest are required"}}) {
            var request = post(example[0]).contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header("X-Request-Id", "audit-validation").with(connectionService());
            if ("present".equals(example[1])) request.header("Idempotency-Key", "invalid-audit");
            mockMvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("IAM_AUDIT_INVALID"))
                    .andExpect(jsonPath("$.error.message").value(example[2]))
                    .andExpect(jsonPath("$.requestId").value("audit-validation"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("X-Request-Id", "audit-validation"))
                    .andExpect(jsonPath("$.error.cause").doesNotExist());
        }
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events:query")
                        .queryParam("from", "invalid-time").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("IAM_AUDIT_INVALID"))
                .andExpect(jsonPath("$.error.message").value("Invalid audit time range"));
    }

    @Test
    void missingAuditResourcesKeepConflictStatusAndTheirOriginalMessages() throws Exception {
        for (String[] example : new String[][]{
                {"audit-events/absent", "Event not found"},
                {"mcp-invocations/absent", "Invocation not found"}}) {
            mockMvc.perform(get("/api/v1/enterprises/ent-acme/" + example[0]).with(admin()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("IAM_AUDIT_CONFLICT"))
                    .andExpect(jsonPath("$.error.message").value(example[1]));
        }
        mockMvc.perform(post("/internal/v1/mcp/invocations/absent:reconcile")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Idempotency-Key", "missing-reconcile").with(connectionService()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Invocation not found"));
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/mcp-invocations/absent:mark-unknown")
                        .header("Idempotency-Key", "missing-unknown").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Invocation not found"));
    }

    @Test
    void conflictingIdempotencyKeyPreservesTheOriginalAuditEvidence() throws Exception {
        append("event-original", "MCP_AUTHORIZATION_DECISION", "inv-idempotent", "attempt", "{\"decision\":\"ALLOW\"}");
        mockMvc.perform(post("/internal/v1/audit-events").with(connectionService())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "event-original")
                        .content("{\"eventId\":\"event-second\",\"eventType\":\"MCP_PERMIT_CLAIMED\",\"enterpriseId\":\"ent-acme\",\"invocationId\":\"inv-idempotent\",\"detail\":{}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Idempotency key reused"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events").queryParam("invocationId", "inv-idempotent").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].eventId").value("event-original"));
    }

    @Test
    void appendsAnIdentityDecisionPermitAndResultChainAsAppendOnlyEvents() throws Exception {
        append("evt-decision", "MCP_AUTHORIZATION_DECISION", "inv-1", "attempt-1", "{\"decision\":\"ALLOW\",\"reasonCode\":\"AUTHORIZED\",\"policyVersion\":3,\"initiator\":\"alice\",\"actor\":\"agent-1\",\"effectiveSubject\":\"alice\",\"agentInstanceId\":\"instance-1\",\"toolId\":\"tool-search\",\"resource\":\"customers/42\",\"action\":\"read\"}");
        append("evt-claim", "MCP_PERMIT_CLAIMED", "inv-1", "attempt-1", "{\"permitId\":\"permit-1\",\"permitJti\":\"jti-1\",\"connectionRevision\":12}");
        append("evt-result", "MCP_EXECUTION_COMPLETED", "inv-1", "attempt-1", "{\"resultStatus\":\"SUCCEEDED\",\"resultDigest\":\"sha256:result\",\"remoteRequestId\":\"remote-1\",\"latencyMs\":231}");

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-invocations/inv-1")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invocationId").value("inv-1"))
                .andExpect(jsonPath("$.data.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.initiator").value("alice"))
                .andExpect(jsonPath("$.data.actor").value("agent-1"))
                .andExpect(jsonPath("$.data.effectiveSubject").value("alice"))
                .andExpect(jsonPath("$.data.decision").value("ALLOW"))
                .andExpect(jsonPath("$.data.permitId").value("permit-1"))
                .andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.resultDigest").value("sha256:result"))
                .andExpect(jsonPath("$.data.events.length()", org.hamcrest.Matchers.is(3)));
    }

    @Test
    void queriesByEnterpriseIdentityToolStatusAndInvocationWithoutCrossTenantLeakage() throws Exception {
        append("evt-query-1", "MCP_AUTHORIZATION_DECISION", "inv-query-1", "attempt-q", "{\"decision\":\"DENY\",\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"agentInstanceId\":null,\"toolId\":\"tool-read\",\"resource\":\"customers/1\",\"action\":\"read\"}");
        append("evt-query-2", "MCP_AUTHORIZATION_DECISION", "inv-query-2", "attempt-q", "{\"decision\":\"ALLOW\",\"initiator\":\"bob\",\"actor\":\"agent-2\",\"effectiveSubject\":\"bob\",\"agentInstanceId\":\"instance-2\",\"toolId\":\"tool-write\",\"resource\":\"orders/1\",\"action\":\"write\"}", "ent-other");

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-invocations")
                        .queryParam("actor", "alice")
                        .queryParam("toolId", "tool-read")
                        .queryParam("decision", "DENY")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].invocationId").value(containsInAnyOrder("inv-query-1")));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events")
                        .queryParam("invocationId", "inv-query-2")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", org.hamcrest.Matchers.is(0)));

        mockMvc.perform(get("/api/v1/enterprises/ent-other/mcp-invocations")
                        .with(admin()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events:query")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enterprises/ent-other/audit-events:query")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .with(adminForOther()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void completionReceiptIsIdempotentByDigestAndContradictionsEnterAnomalyQueue() throws Exception {
        append("evt-start", "MCP_AUTHORIZATION_DECISION", "inv-receipt", "attempt-1", "{\"decision\":\"ALLOW\",\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"toolId\":\"tool-read\"}");
        MvcResult first = complete("inv-receipt", "permit-1", "jti-1", "attempt-1", "SUCCEEDED", "sha256:ok");
        mockMvc.perform(post("/internal/v1/mcp/invocations/inv-receipt:complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody("permit-1", "jti-1", "attempt-1", "SUCCEEDED", "sha256:ok"))
                        .header("Idempotency-Key", "complete-inv-receipt-attempt-1")
                        .with(connectionService()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultDigest").value("sha256:ok"));
        mockMvc.perform(post("/internal/v1/mcp/invocations/inv-receipt:complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody("permit-1", "jti-1", "attempt-1", "SUCCEEDED", "sha256:other"))
                        .header("Idempotency-Key", "complete-inv-receipt-conflict")
                        .with(connectionService()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IAM_AUDIT_CONTRADICTORY_RECEIPT"))
                .andExpect(jsonPath("$.error.message").value("Conflicting result digest"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events")
                        .queryParam("invocationId", "inv-receipt")
                        .queryParam("eventType", "AUDIT_ANOMALY")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].detail.reason").value(containsString("digest")));
    }

    @Test
    void unknownExecutionCanBeReconciledAndLateReceiptIsVisible() throws Exception {
        append("evt-unknown-start", "MCP_AUTHORIZATION_DECISION", "inv-unknown", "attempt-u", "{\"decision\":\"ALLOW\",\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"toolId\":\"tool-write\"}");
        complete("inv-unknown", "permit-u", "jti-u", "attempt-u", "EXECUTION_UNKNOWN", "sha256:unknown");
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-invocations/inv-unknown")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultStatus").value("EXECUTION_UNKNOWN"));

        mockMvc.perform(post("/internal/v1/mcp/invocations/inv-unknown:reconcile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"executionAttemptId\":\"attempt-u\",\"resultStatus\":\"SUCCEEDED\",\"resultDigest\":\"sha256:confirmed\",\"remoteRequestId\":\"remote-confirmed\"}")
                        .header("Idempotency-Key", "reconcile-inv-unknown-attempt-u")
                        .with(connectionService()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-invocations/inv-unknown")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.events[*].eventType", hasItem("MCP_EXECUTION_RECONCILED")));
    }

    @Test
    void recordsRevocationAndRejectsSensitiveCredentialFieldsFromLedger() throws Exception {
        append("evt-revoke", "AUTHORIZATION_REVOKED", "inv-revoke", "attempt-r", "{\"permitId\":\"permit-r\",\"revocationVersion\":4,\"reason\":\"delegation revoked\",\"mcpAccessToken\":\"secret-token\",\"clientSecret\":\"client-secret\",\"rawResult\":\"private body\"}");
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events")
                        .queryParam("invocationId", "inv-revoke")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].eventType").value("AUTHORIZATION_REVOKED"))
                .andExpect(jsonPath("$.data[0].detail.mcpAccessToken").doesNotExist())
                .andExpect(jsonPath("$.data[0].detail.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.data[0].detail.rawResult").doesNotExist());
    }

    @Test
    void appendOnlyRejectsConflictingEventReplayAndPreservesOriginalEvidence() throws Exception {
        append("evt-immutable", "MCP_PERMIT_CLAIMED", "inv-immutable", "attempt-i", "{\"permitId\":\"permit-i\",\"permitJti\":\"jti-i\"}");
        mockMvc.perform(post("/internal/v1/audit-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt-immutable\",\"eventType\":\"MCP_PERMIT_CLAIMED\",\"enterpriseId\":\"ent-acme\",\"invocationId\":\"inv-immutable\",\"attemptId\":\"attempt-i\",\"detail\":{\"permitId\":\"permit-other\"}}")
                        .header("Idempotency-Key", "event-immutable-conflict")
                        .with(connectionService()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IAM_AUDIT_EVENT_IMMUTABLE"))
                .andExpect(jsonPath("$.error.message").value("Event is immutable"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events")
                        .queryParam("invocationId", "inv-immutable")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].detail.permitId").value("permit-i"));
    }

    @Test
    void pagedComplianceQueryKeepsCorrelationFiltersTenantBoundaryAndRedactsNestedSecrets() throws Exception {
        append("evt-page-1", "MCP_AUTHORIZATION_DECISION", "inv-page-1", "attempt-page", "{\"decision\":\"ALLOW\",\"agentInstanceId\":\"instance-page\",\"requestId\":\"req-page\",\"safe\":\"kept\",\"nested\":{\"accessToken\":\"must-hide\"}}");
        append("evt-page-2", "MCP_EXECUTION_COMPLETED", "inv-page-2", "attempt-page", "{\"resultStatus\":\"SUCCEEDED\",\"requestId\":\"other\"}");

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events:query")
                        .queryParam("requestId", "req-evt-page-1")
                        .queryParam("agentInstanceId", "instance-page")
                        .queryParam("page", "0")
                        .queryParam("size", "1")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].requestId").value("req-evt-page-1"))
                .andExpect(jsonPath("$.data.items[0].detail.safe").value("kept"))
                .andExpect(jsonPath("$.data.items[0].detail.nested.accessToken").doesNotExist());
    }

    @Test
    void complianceExportIsTenantBoundCsvAndNeverIncludesCredentialFields() throws Exception {
        append("evt-export", "AUTHORIZATION_REVOKED", "inv-export", "attempt-export", "{\"clientSecret\":\"secret-value\",\"reason\":\"revoked\"}");

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events:export")
                        .queryParam("invocationId", "inv-export")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("eventId,eventType,enterpriseId")))
                .andExpect(content().string(containsString("evt-export")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-value"))));
    }

    private void append(String eventId, String eventType, String invocationId, String attemptId, String detail) throws Exception {
        append(eventId, eventType, invocationId, attemptId, detail, "ent-acme");
    }

    private void append(String eventId, String eventType, String invocationId, String attemptId, String detail, String enterpriseId) throws Exception {
        mockMvc.perform(post("/internal/v1/audit-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"enterpriseId\":\"" + enterpriseId + "\",\"requestId\":\"req-" + eventId + "\",\"traceId\":\"trace-" + eventId + "\",\"invocationId\":\"" + invocationId + "\",\"attemptId\":\"" + attemptId + "\",\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"detail\":" + detail + "}")
                .header("Idempotency-Key", eventId)
                .with(connectionService()))
                .andExpect(status().isCreated());
    }

    private MvcResult complete(String invocationId, String permitId, String jti, String attemptId, String resultStatus, String resultDigest) throws Exception {
        return mockMvc.perform(post("/internal/v1/mcp/invocations/" + invocationId + ":complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completionBody(permitId, jti, attemptId, resultStatus, resultDigest))
                        .header("Idempotency-Key", "complete-" + invocationId + "-" + attemptId)
                        .with(connectionService()))
                .andExpect(status().isOk()).andReturn();
    }

    private static String completionBody(String permitId, String jti, String attemptId, String resultStatus, String resultDigest) {
        return "{\"permitId\":\"" + permitId + "\",\"permitJti\":\"" + jti + "\",\"executionAttemptId\":\"" + attemptId + "\",\"resultStatus\":\"" + resultStatus + "\",\"resultDigest\":\"" + resultDigest + "\",\"remoteRequestId\":\"remote-" + attemptId + "\",\"latencyMs\":30}";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor connectionService() {
        return jwt().jwt(token -> token.subject("connection-service")
                        .claim("service_trust_verified", true)
                        .claim("connection_service_id", "cs-1"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.audit.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("audit-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.audit.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminForOther() {
        return jwt().jwt(token -> token.subject("audit-admin-other")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-other", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-other", List.of("enterprise.audit.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));
    }

}
