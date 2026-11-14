package com.abluepoint.summer.sample.agent;

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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class AgentControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture managed;
    @org.junit.jupiter.api.BeforeEach
    void currentActorFacts() {
        managed.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "enterprise-admin",
                (handler, path, method) -> path.startsWith("/api/v1/enterprises/{enterpriseId}/agents") || path.startsWith("/api/v1/enterprises/{enterpriseId}/agent-instances"));

    }


    @Test void nativeAccessDenialsKeepTheirOriginalMessageAndCause() throws Exception {
        managed.reachServiceWithoutDelegation(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "agent-native-no-cap", (handler,path,method) -> handler.getBeanType().getName().endsWith(".AgentController") && method.equals("GET") && path.startsWith("/api/v1/enterprises/{enterpriseId}/"));
        String path = "/api/v1/enterprises/ent-acme/agents";
        MvcResult result = mockMvc.perform(get(path).with(jwt().jwt(t -> t.subject("agent-native-no-cap").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))).header("X-Request-Id", "native-denied"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Agent capability denied"))
                .andExpect(jsonPath("$.requestId").value("native-denied"))
                .andExpect(header().string("X-Request-Id", "native-denied"))
                .andExpect(jsonPath("$.error.cause").doesNotExist()).andReturn();
        var error = org.junit.jupiter.api.Assertions.assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class,
                result.getResolvedException());
        var cause = org.junit.jupiter.api.Assertions.assertInstanceOf(org.springframework.security.access.AccessDeniedException.class,
                error.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(cause.getMessage(), error.explicitMessage());
    }

    @Test void rejectedAgentMutationsKeepTheirMessagesAndDoNotChangeInstanceState() throws Exception {
        String base = "/api/v1/enterprises/ent-acme/agents";
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{}"), 400,
                "SUMMER_AGENT_INVALID", "Idempotency-Key is required");
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{}").header("Idempotency-Key", "empty"), 400,
                "SUMMER_AGENT_INVALID", "name and code are required");
        String id = createAgent("agent-stable");
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Duplicate\",\"code\":\"agent-stable\"}")
                .header("Idempotency-Key", "duplicate"), 409, "SUMMER_AGENT_CONFLICT", "Agent code already exists");
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\",\"code\":\"other\"}")
                .header("Idempotency-Key", "agent-stable"), 409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused");
        failure(get(base + "/missing"), 409, "SUMMER_AGENT_CONFLICT", "Agent not found");
        failure(patch(base + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}")
                .header("If-Match", "\"999\"").header("Idempotency-Key", "stale"), 412,
                "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        failure(patch(base + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("If-Match", "\"1\"").header("Idempotency-Key", "empty-update"), 400,
                "SUMMER_AGENT_INVALID", "name is required");
        mockMvc.perform(post("/internal/v1/agent-instances").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(runtime(id, "runtime-stable", true)).header("X-Request-Id", "agent-runtime-invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_AGENT_INVALID"))
                .andExpect(jsonPath("$.error.message").value("runtimeId is required"))
                .andExpect(jsonPath("$.requestId").value("agent-runtime-invalid"));
        String registration = "{\"runtimeId\":\"runtime-stable\"}";
        mockMvc.perform(post("/internal/v1/agent-instances").contentType(MediaType.APPLICATION_JSON).content(registration)
                        .with(runtime(id, "runtime-stable", true))).andExpect(status().isCreated());
        mockMvc.perform(post("/internal/v1/agent-instances").contentType(MediaType.APPLICATION_JSON).content(registration)
                        .with(runtime(id, "runtime-stable", true)).header("X-Request-Id", "agent-duplicate-runtime"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SUMMER_AGENT_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Runtime instance already registered"))
                .andExpect(jsonPath("$.requestId").value("agent-duplicate-runtime"));
        failure(post("/api/v1/enterprises/ent-acme/agent-instances/missing:revoke")
                .header("If-Match", "\"1\"").header("Idempotency-Key", "missing-instance"), 409,
                "SUMMER_AGENT_CONFLICT", "Agent instance not found");
        mockMvc.perform(get(base + "/" + id).with(admin("enterprise.agents.read")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.name").value("Runtime Agent"));
        mockMvc.perform(get(base + "/" + id + "/instances").with(admin("enterprise.agents.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
        mockMvc.perform(get(base + "/" + id + "/audit").with(admin("enterprise.agents.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    private void failure(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                         int status, String code, String message) throws Exception {
        mockMvc.perform(request.with(admin("enterprise.agents.write")).header("X-Request-Id", "agent-rejected"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.requestId").value("agent-rejected"))
                .andExpect(header().string("X-Request-Id", "agent-rejected"))
                .andExpect(jsonPath("$.error.cause").doesNotExist());
    }

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
        var domain=com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
        for (String subject : List.of("alice", "disabled")) managed.grantHandlers(domain,subject,
                (handler,path,method)->path.equals("/api/v1/enterprises/{enterpriseId}/agents") && method.equals("GET"));
        jdbc.update("update summer_enterprise_member set status='DISABLED' where enterprise_id=? and subject=?", "ent-acme", "disabled");
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents")
                        .with(jwt().jwt(token -> token.claim("scope", "enterprise.agents.other").subject("alice")
                                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.agents.read"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.agents.other"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/agents")
                        .with(jwt().jwt(token -> token.claim("scope", "enterprise.agents.read").subject("disabled")
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
        return jwt().jwt(token -> token.claim("scope", capability).subject("enterprise-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor runtime(String agentId, String runtimeId, boolean verified) {
        return jwt().jwt(token -> token.claim("scope", "internal.agent_instances.write").subject("runtime-" + runtimeId)
                        .claim("agent_id", agentId)
                        .claim("runtime_id", runtimeId)
                        .claim("workload_identity", "verified://" + agentId)
                        .claim("attestation_type", "SPIFFE")
                        .claim("attestation_verified", verified))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.agent_instances.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor runtimeInstance(String instanceId, String agentId, String runtimeId) {
        return jwt().jwt(token -> token.claim("scope", "internal.agent_instances.write").subject("runtime-" + runtimeId)
                        .claim("agent_id", agentId)
                        .claim("agent_instance_id", instanceId)
                        .claim("runtime_id", runtimeId)
                        .claim("workload_identity", "verified://" + agentId)
                        .claim("attestation_verified", true))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.agent_instances.write"));
    }
}
