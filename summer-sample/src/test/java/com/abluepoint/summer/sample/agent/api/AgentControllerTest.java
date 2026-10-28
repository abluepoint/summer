package com.abluepoint.summer.sample.agent.api;

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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AgentControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void createsAndListsAgentsWithinEnterpriseBoundary() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Support Copilot\",\"code\":\"support-copilot\",\"runtimeType\":\"KUBERNETES\"}")
                        .header("Idempotency-Key", "agent-create-1")
                        .with(admin("enterprise.agents.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.agentId").isNotEmpty())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(header().string("ETag", startsWith("\"")))
                .andReturn();
        String agentId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.agentId");
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents").with(admin("enterprise.agents.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].agentId").value(agentId));
        mockMvc.perform(get("/api/v1/enterprises/ent-other/agents").with(admin("enterprise.agents.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresActiveMembershipCapabilityAndOauthScope() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents")
                        .with(jwt().jwt(token -> token.subject("alice")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.agents.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.agents.other"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents")
                        .with(jwt().jwt(token -> token.subject("disabled")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "DISABLED")))
                                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.agents.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.agents.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void registersInstanceOnlyFromVerifiedWorkloadIdentityAndRejectsSelfReportedAgent() throws Exception {
        String agentId = createAgent("agent-instance-1");
        mockMvc.perform(post("/internal/v1/agent-instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"runtimeId\":\"runtime-1\",\"workloadIdentity\":\"spiffe://acme/agent/1\",\"attestationType\":\"SPIFFE\",\"attestationDigest\":\"sha256:abc\",\"environment\":\"prod\",\"expiresInSeconds\":900}")
                        .with(runtime(agentId, "runtime-1", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.agentId").value(agentId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/internal/v1/agent-instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"agent-forged\",\"runtimeId\":\"runtime-2\",\"workloadIdentity\":\"spiffe://acme/agent/1\",\"attestationType\":\"SPIFFE\",\"attestationDigest\":\"sha256:abc\"}")
                        .with(runtime(agentId, "runtime-2", true)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/v1/agent-instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"runtimeId\":\"runtime-3\",\"workloadIdentity\":\"spiffe://acme/agent/1\",\"attestationType\":\"SPIFFE\",\"attestationDigest\":\"sha256:abc\"}")
                        .with(runtime(agentId, "runtime-3", false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void heartbeatsAndRevokesInstanceWithAuditableIdentity() throws Exception {
        String agentId = createAgent("agent-instance-2");
        MvcResult registered = mockMvc.perform(post("/internal/v1/agent-instances")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"runtimeId\":\"runtime-heartbeat\",\"workloadIdentity\":\"k8s://acme/ns/prod/sa/copilot\",\"attestationType\":\"K8S_SERVICE_ACCOUNT\",\"attestationDigest\":\"sha256:def\",\"environment\":\"prod\",\"expiresInSeconds\":900}")
                        .with(runtime(agentId, "runtime-heartbeat", true)))
                .andExpect(status().isCreated()).andReturn();
        String instanceId = JsonPath.read(registered.getResponse().getContentAsString(), "$.data.instanceId");
        MvcResult heartbeat = mockMvc.perform(post("/internal/v1/agent-instances/" + instanceId + ":heartbeat")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"extendSeconds\":600}")
                        .with(runtimeInstance(instanceId, agentId, "runtime-heartbeat")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastSeenAt").isNotEmpty()).andReturn();
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/agent-instances/" + instanceId + ":revoke")
                        .header("If-Match", heartbeat.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "instance-revoke-1")
                        .with(admin("enterprise.agents.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents/" + agentId + "/audit")
                        .with(admin("enterprise.agents.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.action == 'AGENT_INSTANCE_REGISTERED')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.action == 'AGENT_INSTANCE_REVOKED')]").isNotEmpty());
    }

    @Test
    void rejectsStaleAgentWritesAndListsInstances() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ops Bot\",\"code\":\"ops-bot\"}")
                        .header("Idempotency-Key", "agent-etag-1").with(admin("enterprise.agents.write")))
                .andExpect(status().isCreated()).andReturn();
        String agentId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.agentId");
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/agents/" + agentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}")
                        .header("If-Match", "\"999\"").header("Idempotency-Key", "agent-stale-1")
                        .with(admin("enterprise.agents.write")))
                .andExpect(status().isPreconditionFailed());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents/" + agentId + "/instances")
                        .with(admin("enterprise.agents.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    private String createAgent(String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/enterprises/ent-acme/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Runtime Agent\",\"code\":\"" + key + "\",\"runtimeType\":\"SERVICE\"}")
                        .header("Idempotency-Key", key).with(admin("enterprise.agents.write")))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.agentId");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return jwt().jwt(token -> token.subject("enterprise-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor runtime(String agentId, String runtimeId, boolean verified) {
        return jwt().jwt(token -> token.subject("runtime-" + runtimeId)
                        .claim("agent_id", agentId)
                        .claim("runtime_id", runtimeId)
                        .claim("workload_identity", "verified://" + agentId)
                        .claim("attestation_type", "SPIFFE")
                        .claim("attestation_verified", verified))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.agent_instances.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor runtimeInstance(String instanceId, String agentId, String runtimeId) {
        return jwt().jwt(token -> token.subject("runtime-" + runtimeId)
                        .claim("agent_id", agentId)
                        .claim("agent_instance_id", instanceId)
                        .claim("runtime_id", runtimeId)
                        .claim("workload_identity", "verified://" + agentId)
                        .claim("attestation_verified", true))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.agent_instances.write"));
    }
}
