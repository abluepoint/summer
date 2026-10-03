package com.abluepoint.summer.sample.mcpauthorization;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class McpAuthorizationControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void evaluatesIdentityAndIssuesAudienceBoundSingleUsePermit() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/mcp/execution-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"ent-acme\",\"connectionServiceId\":\"cs-1\",\"connectionBindingId\":\"bind-1\",\"externalConnectionId\":\"ext-1\",\"connectionRevision\":3,\"executionContextId\":\"ctx-1\",\"actorPrincipalId\":\"agent-principal\",\"actorInstanceId\":\"ainst-1\",\"effectiveSubjectPrincipalId\":\"user-1\",\"toolId\":\"tool-1\",\"toolName\":\"search\",\"schemaDigest\":\"sha256:schema\",\"argumentsDigest\":\"sha256:args\",\"audience\":\"urn:aict:cs-1\",\"risk\":\"LOW\",\"allow\":true}")
                        .with(service("cs-1", "ctx-1", "agent-principal", "ainst-1", "user-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("ALLOW"))
                .andExpect(jsonPath("$.data.allow").value(true)).andExpect(jsonPath("$.data.permit.permitId").isNotEmpty())
                .andExpect(jsonPath("$.data.permit.audience").value("urn:aict:cs-1"))
                .andExpect(jsonPath("$.data.permit.argumentsDigest").value("sha256:args")).andReturn();
        String permitId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.permit.permitId");
        String invocationId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.invocationId");
        mockMvc.perform(post("/internal/v1/mcp/execution-permits/" + permitId + ":claim")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"executionAttemptId\":\"attempt-1\"}")
                        .with(service("cs-1", "ctx-1", "agent-principal", "ainst-1", "user-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLAIMED"))
                .andExpect(jsonPath("$.data.invocationId").value(invocationId));
        mockMvc.perform(post("/internal/v1/mcp/execution-permits/" + permitId + ":claim")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"executionAttemptId\":\"attempt-1\"}")
                        .with(service("cs-1", "ctx-1", "agent-principal", "ainst-1", "user-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLAIMED"));
    }

    @Test
    void deniesApprovalRequiredAndIgnoresClientAllowConclusion() throws Exception {
        mockMvc.perform(post("/internal/v1/mcp/execution-decisions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"ent-acme\",\"connectionServiceId\":\"cs-1\",\"executionContextId\":\"ctx-2\",\"actorPrincipalId\":\"agent\",\"effectiveSubjectPrincipalId\":\"user\",\"toolId\":\"tool\",\"toolName\":\"delete\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"audience\":\"urn:aict:cs-1\",\"risk\":\"HIGH\",\"allow\":true}")
                        .with(service("cs-1", "ctx-2", "agent", null, "user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.data.allow").value(false)).andExpect(jsonPath("$.data.permit").doesNotExist());
    }

    @Test
    void approvedHighRiskDecisionIssuesPermitOnlyForTheExactBoundInvocation() throws Exception {
        String request = "{\"enterpriseId\":\"ent-acme\",\"connectionServiceId\":\"cs-1\",\"connectionBindingId\":\"bind-1\",\"externalConnectionId\":\"ext-1\",\"executionContextId\":\"ctx-approval\",\"actorPrincipalId\":\"agent\",\"actorInstanceId\":\"ainst-1\",\"effectiveSubjectPrincipalId\":\"user\",\"toolId\":\"tool-delete\",\"toolName\":\"delete\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args-original\",\"audience\":\"urn:aict:cs-1\",\"risk\":\"HIGH\"}";
        MvcResult pending = mockMvc.perform(post("/internal/v1/mcp/execution-decisions")
                        .contentType(MediaType.APPLICATION_JSON).content(request)
                        .with(service("cs-1", "ctx-approval", "agent", "ainst-1", "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.data.approvalId").isNotEmpty())
                .andReturn();
        String approvalId = JsonPath.read(pending.getResponse().getContentAsString(), "$.data.approvalId");

        mockMvc.perform(post("/api/v1/mcp/approvals/{id}:approve", approvalId)
                        .header("Idempotency-Key", "approve-bound-high-risk")
                        .with(approver()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(post("/internal/v1/mcp/execution-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withApproval(request, approvalId))
                        .with(service("cs-1", "ctx-approval", "agent", "ainst-1", "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("ALLOW"))
                .andExpect(jsonPath("$.data.permit.permitId").isNotEmpty());

        mockMvc.perform(post("/internal/v1/mcp/execution-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withApproval(request.replace("args-original", "args-changed"), approvalId))
                        .with(service("cs-1", "ctx-approval", "agent", "ainst-1", "user")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsCrossEnterpriseIdentityAudienceAndConflictingClaimAttempt() throws Exception {
        String body = "{\"enterpriseId\":\"ent-other\",\"connectionServiceId\":\"cs-1\",\"executionContextId\":\"ctx-3\",\"actorPrincipalId\":\"agent\",\"effectiveSubjectPrincipalId\":\"user\",\"toolId\":\"tool\",\"toolName\":\"read\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"audience\":\"urn:aict:cs-1\",\"risk\":\"LOW\"}";
        mockMvc.perform(post("/internal/v1/mcp/execution-decisions").contentType(MediaType.APPLICATION_JSON).content(body).with(service("cs-1", "ctx-3", "agent", null, "user"))).andExpect(status().isForbidden());
        MvcResult result = mockMvc.perform(post("/internal/v1/mcp/execution-decisions").contentType(MediaType.APPLICATION_JSON).content(body.replace("ent-other", "ent-acme")).with(service("cs-1", "ctx-3", "agent", null, "user"))).andExpect(status().isOk()).andReturn();
        String permitId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.permit.permitId");
        mockMvc.perform(post("/internal/v1/mcp/execution-permits/" + permitId + ":claim").contentType(MediaType.APPLICATION_JSON).content("{\"executionAttemptId\":\"a-1\"}").with(service("cs-2", "ctx-3", "agent", null, "user"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/v1/mcp/execution-permits/" + permitId + ":claim").contentType(MediaType.APPLICATION_JSON).content("{\"executionAttemptId\":\"a-1\"}").with(service("cs-1", "ctx-3", "agent", null, "user"))).andExpect(status().isOk());
        mockMvc.perform(post("/internal/v1/mcp/execution-permits/" + permitId + ":claim").contentType(MediaType.APPLICATION_JSON).content("{\"executionAttemptId\":\"a-2\"}").with(service("cs-1", "ctx-3", "agent", null, "user"))).andExpect(status().isConflict());
    }

    @Test
    void revokesPermitAndPreventsClaimAfterRevoke() throws Exception {
        MvcResult result = mockMvc.perform(post("/internal/v1/mcp/execution-decisions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"ent-acme\",\"connectionServiceId\":\"cs-1\",\"executionContextId\":\"ctx-4\",\"actorPrincipalId\":\"agent\",\"effectiveSubjectPrincipalId\":\"user\",\"toolId\":\"tool\",\"toolName\":\"read\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"audience\":\"urn:aict:cs-1\",\"risk\":\"LOW\"}")
                        .with(service("cs-1", "ctx-4", "agent", null, "user"))).andExpect(status().isOk()).andReturn();
        String permitId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.permit.permitId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/mcp-execution-permits/" + permitId + ":revoke").header("Idempotency-Key", "permit-revoke-1").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(post("/internal/v1/mcp/execution-permits/" + permitId + ":claim").contentType(MediaType.APPLICATION_JSON).content("{\"executionAttemptId\":\"attempt\"}").with(service("cs-1", "ctx-4", "agent", null, "user"))).andExpect(status().isConflict());
    }

    @Test
    void rejectsCallerControlledIdentityMismatch() throws Exception {
        mockMvc.perform(post("/internal/v1/mcp/execution-decisions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseId\":\"ent-acme\",\"connectionServiceId\":\"cs-1\",\"executionContextId\":\"ctx-5\",\"actorPrincipalId\":\"forged\",\"effectiveSubjectPrincipalId\":\"user\",\"toolId\":\"tool\",\"toolName\":\"read\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"audience\":\"urn:aict:cs-1\",\"risk\":\"LOW\"}")
                        .with(service("cs-1", "ctx-5", "agent", null, "user"))).andExpect(status().isForbidden());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor service(String serviceId,String context,String actor,String instance,String effective){return jwt().jwt(t->t.subject("connection-service").claim("connection_service_id",serviceId).claim("service_trust_verified",true).claim("execution_context_id",context).claim("actor_principal_id",actor).claim("actor_instance_id",instance).claim("effective_subject_principal_id",effective)).authorities(new SimpleGrantedAuthority("SCOPE_internal.mcp.execution.decide"));}
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(){return jwt().jwt(t->t.subject("admin").claim("enterprise_memberships",List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))).claim("enterprise_capabilities",java.util.Map.of("ent-acme",List.of("enterprise.mcp.execution.write")))).authorities(new SimpleGrantedAuthority("SCOPE_enterprise.mcp.execution.write"));}
    private static org.springframework.test.web.servlet.request.RequestPostProcessor approver(){return jwt().jwt(t->t.subject("approver").claim("enterprise_memberships",List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))).claim("enterprise_capabilities",java.util.Map.of("ent-acme",List.of("enterprise.mcp.approvals.approve")))).authorities(new SimpleGrantedAuthority("SCOPE_enterprise.mcp.approvals.approve"));}
    private static String withApproval(String request, String approvalId) { return request.substring(0, request.length() - 1) + ",\"approvalId\":\"" + approvalId + "\"}"; }
}
