package com.abluepoint.summer.sample.delegation;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:delegation-error-contract;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DelegationErrorContractHttpTest {
    private static final String GRANTS = "/api/v1/enterprises/ent-acme/agents/agent-1/delegations";
    private static final String APPROVALS = "/internal/v1/mcp/approvals";
    private static final String GRANT = "{\"scopeType\":\"MCP_TOOL\",\"mcpServerId\":\"server-1\",\"toolPatterns\":[\"customers.read\"]}";
    private static final String APPROVAL = "{\"enterpriseId\":\"ent-acme\",\"mcpServerId\":\"server-1\",\"toolId\":\"tool-1\",\"requestDigest\":\"digest\"}";
    @Autowired MockMvc mvc;

    @Test void validationAndNativeEnterpriseDenialKeepTheirLiteralMessages() throws Exception {
        error(post(GRANTS).content("{}").header("Idempotency-Key", "invalid-grant"), 400, "SUMMER_DELEGATION_INVALID",
                "scopeType, mcpServerId and toolPatterns are required");
        error(post(APPROVALS).content("{}").header("Idempotency-Key", "invalid-approval"), 400, "SUMMER_DELEGATION_INVALID", "enterpriseId is required");
        error(post(GRANTS.replace("ent-acme", "ent-other")).content(GRANT).header("Idempotency-Key", "foreign-grant"),
                403, "SUMMER_FORBIDDEN", "Access is denied");
        // /internal bypasses the /api membership interceptor and reaches the native service adapter.
        error(post(APPROVALS).content(APPROVAL.replace("ent-acme", "ent-other")).header("Idempotency-Key", "foreign-approval"),
                403, "SUMMER_FORBIDDEN", "Delegation capability denied");
    }

    @Test void absentGrantsAndApprovalsRemain409ThroughEveryReadAndDecisionAdapter() throws Exception {
        error(post("/api/v1/enterprises/ent-acme/delegations/absent:revoke").header("Idempotency-Key", "absent-revoke"),
                409, "SUMMER_DELEGATION_CONFLICT", "Delegation not found");
        for (var request : List.of(post("/api/v1/mcp/approvals/absent:approve").header("Idempotency-Key", "absent-approve"),
                post("/api/v1/mcp/approvals/absent:recheck"), get("/api/v1/mcp/approvals/absent/audit"))) {
            error(request, 409, "SUMMER_DELEGATION_CONFLICT", "Approval not found");
        }
    }

    @Test void changedIdempotencyPayloadKeeps409AndDoesNotReplaceTheAcceptedApproval() throws Exception {
        mvc.perform(post(GRANTS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(GRANT)
                        .header("Idempotency-Key", "grant-receipt")).andExpect(status().isCreated());
        error(post(GRANTS).content(GRANT.replace("customers.read", "customers.write")).header("Idempotency-Key", "grant-receipt"),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused");
        String id = approval("approval-receipt");
        error(post(APPROVALS).content(APPROVAL.replace("digest", "changed")).header("Idempotency-Key", "approval-receipt"),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused");
        mvc.perform(post("/api/v1/mcp/approvals/" + id + ":recheck").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requestDigest").value("digest"));
    }

    @Test void aSecondDecisionCannotChangeTheApprovedStateOrAudit() throws Exception {
        String id = approval("decision-approval");
        mvc.perform(post("/api/v1/mcp/approvals/" + id + ":approve").with(admin()).header("Idempotency-Key", "decision-first"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        error(post("/api/v1/mcp/approvals/" + id + ":reject").header("Idempotency-Key", "decision-second"),
                409, "SUMMER_DELEGATION_CONFLICT", "Approval is not pending");
        mvc.perform(post("/api/v1/mcp/approvals/" + id + ":recheck").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(get("/api/v1/mcp/approvals/" + id + "/audit").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.action == 'APPROVAL_REJECTED')]").isEmpty());
    }

    @Test void unexpectedTechnicalFailureUsesASafe500AndDoesNotConsumeTheReceipt() throws Exception {
        error(post(GRANTS).content(GRANT.replace("[\"customers.read\"]", "[null]")).header("Idempotency-Key", "technical-retry"),
                500, "SUMMER_INTERNAL_ERROR", "An unexpected error occurred");
        mvc.perform(post(GRANTS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(GRANT)
                        .header("Idempotency-Key", "technical-retry")).andExpect(status().isCreated());
    }

    private String approval(String key) throws Exception {
        var result = mvc.perform(post(APPROVALS).with(admin()).contentType(MediaType.APPLICATION_JSON).content(APPROVAL)
                        .header("Idempotency-Key", key)).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.approvalId");
    }
    private ResultActions error(MockHttpServletRequestBuilder request, int expectedStatus, String code, String message) throws Exception {
        return mvc.perform(request.with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "delegation-contract").header("Accept-Language", "zh-CN"))
                .andExpect(status().is(expectedStatus)).andExpect(header().string("X-Request-Id", "delegation-contract"))
                .andExpect(jsonPath("$.error.code").value(code)).andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.violations").isEmpty()).andExpect(jsonPath("$.requestId").value("delegation-contract"));
    }
    private static RequestPostProcessor admin() {
        var capabilities = List.of("enterprise.agents.delegations.read", "enterprise.agents.delegations.write",
                "enterprise.mcp.approvals.write", "enterprise.mcp.approvals.approve");
        return jwt().jwt(token -> token.claim("scope", String.join(" ", capabilities)).subject("admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", capabilities)))
                .authorities(capabilities.stream().map(capability -> new SimpleGrantedAuthority("SCOPE_" + capability)).toArray(SimpleGrantedAuthority[]::new));
    }
}
