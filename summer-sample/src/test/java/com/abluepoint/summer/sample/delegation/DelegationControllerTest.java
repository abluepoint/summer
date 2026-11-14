package com.abluepoint.summer.sample.delegation;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class DelegationControllerTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "enterprise-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.delegation.DelegationController")
                && java.util.Set.of("create POST /api/v1/enterprises/{e}/agents/{a}/delegations",
                        "list GET /api/v1/enterprises/{e}/agents/{a}/delegations",
                        "approval POST /internal/v1/mcp/approvals",
                        "withdraw POST /api/v1/mcp/approvals/{id}:withdraw")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "approver", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.delegation.DelegationController")
                && java.util.Set.of("approve POST /api/v1/mcp/approvals/{id}:approve",
                        "reject POST /api/v1/mcp/approvals/{id}:reject",
                        "recheck POST /api/v1/mcp/approvals/{id}:recheck",
                        "audit GET /api/v1/mcp/approvals/{id}/audit")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }

    @Autowired MockMvc mockMvc;

    @Test
    void createsDelegationAndEnforcesEnterpriseBoundary() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/enterprises/ent-acme/agents/agent-1/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"MCP_TOOL\",\"mcpServerId\":\"server-1\",\"toolPatterns\":[\"customers.read\"],\"resourcePatterns\":[\"tenant/*\"],\"capabilityCodes\":[\"mcp.customer.read\"],\"maxRiskLevel\":\"MEDIUM\",\"allowRedelegation\":true,\"maxDelegationDepth\":2,\"expiresInSeconds\":3600}")
                        .header("Idempotency-Key", "delegation-create-1").with(admin("enterprise.agents.delegations.write")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.delegationId").isNotEmpty())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE")).andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.delegationId");
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents/agent-1/delegations").with(admin("enterprise.agents.delegations.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].delegationId").value(id));
        mockMvc.perform(get("/api/v1/enterprises/ent-other/agents/agent-1/delegations").with(admin("enterprise.agents.delegations.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void childDelegationCannotExpandParentScopeRiskOrLifetime() throws Exception {
        MvcResult parent = createDelegation("parent-1", "HIGH", 7200, true, 2, List.of("customers.read", "customers.write"));
        String parentId = JsonPath.read(parent.getResponse().getContentAsString(), "$.data.delegationId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/agents/agent-1/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentGrantId\":\"" + parentId + "\",\"scopeType\":\"MCP_TOOL\",\"mcpServerId\":\"server-1\",\"toolPatterns\":[\"customers.delete\"],\"capabilityCodes\":[\"mcp.customer.delete\"],\"maxRiskLevel\":\"CRITICAL\",\"allowRedelegation\":true,\"maxDelegationDepth\":2,\"expiresInSeconds\":7200}")
                        .header("Idempotency-Key", "delegation-child-invalid").with(admin("enterprise.agents.delegations.write")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/agents/agent-1/delegations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentGrantId\":\"" + parentId + "\",\"scopeType\":\"MCP_TOOL\",\"mcpServerId\":\"server-1\",\"toolPatterns\":[\"customers.read\"],\"capabilityCodes\":[\"mcp.customer.read\"],\"maxRiskLevel\":\"MEDIUM\",\"allowRedelegation\":false,\"maxDelegationDepth\":2,\"expiresInSeconds\":3600}")
                        .header("Idempotency-Key", "delegation-child-valid").with(admin("enterprise.agents.delegations.write")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.depth").value(1));
    }

    @Test
    void approvalLifecycleUsesApproverIdentityAndSupportsReevaluationAndAudit() throws Exception {
        MvcResult approval = mockMvc.perform(post("/internal/v1/mcp/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"ent-acme\",\"agentId\":\"agent-1\",\"mcpServerId\":\"server-1\",\"toolId\":\"tool-1\",\"requestDigest\":\"sha256:req\",\"approvalScope\":\"ONE_CALL\",\"expiresInSeconds\":900,\"approverPrincipalId\":\"forged\"}")
                        .header("Idempotency-Key", "approval-create-1").with(admin("enterprise.mcp.approvals.write")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.approverPrincipalId").value("forged")).andReturn();
        String id = JsonPath.read(approval.getResponse().getContentAsString(), "$.data.approvalId");
        mockMvc.perform(post("/api/v1/mcp/approvals/" + id + ":approve")
                        .header("Idempotency-Key", "approval-approve-1").with(approver("enterprise.mcp.approvals.approve")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value("approver"));
        mockMvc.perform(post("/api/v1/mcp/approvals/" + id + ":recheck").with(approver("enterprise.mcp.approvals.approve")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        mockMvc.perform(get("/api/v1/mcp/approvals/" + id + "/audit").with(approver("enterprise.mcp.approvals.approve")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.action == 'APPROVAL_APPROVED')]").isNotEmpty());
    }

    @Test
    void approvalCanBeRejectedOrWithdrawnAndCannotBeReused() throws Exception {
        MvcResult rejected = createApproval("approval-reject-1");
        String rejectedId = JsonPath.read(rejected.getResponse().getContentAsString(), "$.data.approvalId");
        mockMvc.perform(post("/api/v1/mcp/approvals/" + rejectedId + ":reject").header("Idempotency-Key", "approval-reject-action").with(approver("enterprise.mcp.approvals.approve")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        mockMvc.perform(post("/api/v1/mcp/approvals/" + rejectedId + ":approve").header("Idempotency-Key", "approval-reapprove").with(approver("enterprise.mcp.approvals.approve")))
                .andExpect(status().isConflict());
        MvcResult withdrawn = createApproval("approval-withdraw-1");
        String withdrawnId = JsonPath.read(withdrawn.getResponse().getContentAsString(), "$.data.approvalId");
        mockMvc.perform(post("/api/v1/mcp/approvals/" + withdrawnId + ":withdraw").header("Idempotency-Key", "approval-withdraw-action").with(admin("enterprise.mcp.approvals.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
    }

    @Test
    void rejectsCrossEnterpriseDelegationAndInactiveApprovalRequester() throws Exception {
        mockMvc.perform(post("/api/v1/enterprises/ent-other/agents/agent-1/delegations").contentType(MediaType.APPLICATION_JSON).content("{\"scopeType\":\"MCP_TOOL\",\"toolPatterns\":[\"*\"]}").header("Idempotency-Key", "cross-1").with(admin("enterprise.agents.delegations.write"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/v1/mcp/approvals").contentType(MediaType.APPLICATION_JSON).content("{\"enterpriseId\":\"ent-acme\",\"agentId\":\"agent-1\",\"mcpServerId\":\"server\",\"toolId\":\"tool\",\"requestDigest\":\"d\"}").header("Idempotency-Key", "inactive-1").with(inactive("enterprise.mcp.approvals.write"))).andExpect(status().isForbidden());
    }

    private MvcResult createDelegation(String key, String risk, long expires, boolean redelegation, int depth, List<String> tools) throws Exception {
        return mockMvc.perform(post("/api/v1/enterprises/ent-acme/agents/agent-1/delegations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeType\":\"MCP_TOOL\",\"mcpServerId\":\"server-1\",\"toolPatterns\":[\"" + String.join("\",\"", tools) + "\"],\"capabilityCodes\":[\"mcp.customer.read\"],\"maxRiskLevel\":\"" + risk + "\",\"allowRedelegation\":" + redelegation + ",\"maxDelegationDepth\":" + depth + ",\"expiresInSeconds\":" + expires + "}")
                        .header("Idempotency-Key", key).with(admin("enterprise.agents.delegations.write"))).andExpect(status().isCreated()).andReturn();
    }
    private MvcResult createApproval(String key) throws Exception { return mockMvc.perform(post("/internal/v1/mcp/approvals").contentType(MediaType.APPLICATION_JSON).content("{\"enterpriseId\":\"ent-acme\",\"agentId\":\"agent-1\",\"mcpServerId\":\"server-1\",\"toolId\":\"tool-1\",\"requestDigest\":\"sha256:req\",\"expiresInSeconds\":900}").header("Idempotency-Key", key).with(admin("enterprise.mcp.approvals.write"))).andExpect(status().isCreated()).andReturn(); }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) { return jwt().jwt(t -> t.claim("scope", capability).subject("enterprise-admin").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))).authorities(new SimpleGrantedAuthority("SCOPE_" + capability)); }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor approver(String capability) { return jwt().jwt(t -> t.claim("scope", capability).subject("approver").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))).authorities(new SimpleGrantedAuthority("SCOPE_" + capability)); }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor inactive(String capability) { return jwt().jwt(t -> t.claim("scope", capability).subject("inactive").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "DISABLED")))).authorities(new SimpleGrantedAuthority("SCOPE_" + capability)); }
}
