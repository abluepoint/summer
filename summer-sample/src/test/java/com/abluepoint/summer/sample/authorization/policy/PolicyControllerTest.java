package com.abluepoint.summer.sample.authorization.policy;

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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class PolicyControllerTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "policy-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.authorization.policy.PolicyController")
                && java.util.Set.of("list GET /api/v1/enterprises/{enterpriseId}/policies",
                        "create POST /api/v1/enterprises/{enterpriseId}/policies",
                        "detail GET /api/v1/enterprises/{enterpriseId}/policies/{policyId}",
                        "versions GET /api/v1/enterprises/{enterpriseId}/policies/{policyId}/versions",
                        "updateDraft PATCH /api/v1/enterprises/{enterpriseId}/policies/{policyId}/draft",
                        "validate POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:validate",
                        "publish POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:publish",
                        "pause POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:pause",
                        "revoke POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:revoke",
                        "rollback POST /api/v1/enterprises/{enterpriseId}/policies/{policyId}:rollback")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
    }


    @Autowired
    MockMvc mockMvc;

    @Test
    void unpublishedPolicyAndReusedKeyUseUnifiedFailures() throws Exception {
        String body = policyBody("Draft", "unified-draft");
        MvcResult created = create("unified-policy", body);
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:pause", id)
                        .header("If-Match", "\"1\"").header("Idempotency-Key", "pause-draft")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isConflict())
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified(
                        "SUMMER_POLICY_CONFLICT", "Policy is not published"));
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies").contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Draft", "Changed")).header("Idempotency-Key", "unified-policy")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isConflict())
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified(
                        "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused"));
    }

    @Test
    void createsAndListsAnEnterprisePolicyDraft() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody("Customer read policy", "allow-customer-read"))
                        .header("Idempotency-Key", "policy-create-1")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.policyId").isNotEmpty())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.lifecycleStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.draftVersion").value(1))
                .andReturn();
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/policies")
                        .with(admin("enterprise.policies.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].policyId").value(policyId))
                .andExpect(jsonPath("$.data[0].lifecycleStatus").value("DRAFT"));
    }

    @Test
    void validatesConditionWhitelistBeforePublication() throws Exception {
        MvcResult created = create("policy-validate-1", policyBody("Condition policy", "condition-policy"));
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        String etag = created.getResponse().getHeader("ETag");

        MvcResult patched = mockMvc.perform(patch("/api/v1/enterprises/ent-acme/policies/{id}/draft", policyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definition\":{\"defaultDecision\":\"DENY\",\"denyOverrides\":true,\"rules\":[{\"id\":\"bad\",\"effect\":\"ALLOW\",\"subjects\":[\"alice\"],\"resources\":[\"customers/*\"],\"actions\":[\"read\"],\"conditions\":{\"devicePosture\":\"managed\"}}]}}")
                        .header("If-Match", etag)
                        .header("Idempotency-Key", "policy-draft-bad-condition")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:validate", policyId)
                        .header("Idempotency-Key", "policy-validate-bad")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_POLICY_INVALID"))
                .andExpect(jsonPath("$.error.message").value(containsString("Unsupported condition")))
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_POLICY_INVALID", "Unsupported condition: devicePosture"));
    }

    @Test
    void publishesImmutableVersionsAndRollsBackCurrentVersion() throws Exception {
        MvcResult created = create("policy-publish-1", policyBody("Versioned policy", "versioned-policy"));
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        String etag = created.getResponse().getHeader("ETag");

        MvcResult published = mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:publish", policyId)
                        .header("If-Match", etag).header("Idempotency-Key", "policy-publish-v1")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lifecycleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(1))
                .andExpect(jsonPath("$.data.currentVersion.immutable").value(true))
                .andReturn();

        String publishedEtag = published.getResponse().getHeader("ETag");
        MvcResult draft = mockMvc.perform(patch("/api/v1/enterprises/ent-acme/policies/{id}/draft", policyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"second draft\"}")
                        .header("If-Match", publishedEtag).header("Idempotency-Key", "policy-draft-v2")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk()).andReturn();
        MvcResult publishedV2 = mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:publish", policyId)
                        .header("If-Match", draft.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-publish-v2")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(2))
                .andReturn();

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:rollback", policyId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":1}")
                        .header("If-Match", publishedV2.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-rollback-v1")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion.versionNo").value(1))
                .andExpect(jsonPath("$.data.versions", hasItem(org.hamcrest.Matchers.hasEntry("immutable", true))));
    }

    @Test
    void evaluatesAllowAndReturnsIdentityChainAndPolicyVersion() throws Exception {
        MvcResult created = create("policy-evaluate-1", policyBody("Customer policy", "customer-policy"));
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        MvcResult published = mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:publish", policyId)
                        .header("If-Match", created.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-evaluate-publish")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk()).andReturn();
        int policyVersion = JsonPath.read(published.getResponse().getContentAsString(), "$.data.currentVersion.versionNo");

        mockMvc.perform(post("/internal/v1/authorization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"enterpriseId\":\"ent-acme\",\"resource\":\"customers/42\",\"action\":\"read\",\"risk\":\"LOW\",\"organizationId\":\"org-engineering\",\"requestId\":\"req-policy-allow\",\"allow\":false}")
                        .with(internalCaller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("ALLOW"))
                .andExpect(jsonPath("$.data.allow").value(true))
                .andExpect(jsonPath("$.data.approvalRequired").value(false))
                .andExpect(jsonPath("$.data.policyVersion").value(policyVersion))
                .andExpect(jsonPath("$.data.matchedRules", hasItem("allow-customer-read")))
                .andExpect(jsonPath("$.data.identity.initiator").value("alice"))
                .andExpect(jsonPath("$.data.identity.effectiveSubject").value("alice"));
    }

    @Test
    void returnsStructuredPolicyDetailAndRejectsSpoofedEvaluationIdentity() throws Exception {
        MvcResult created = create("policy-detail-contract", policyBody("Detail policy", "detail-policy"));
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/policies/{id}", policyId)
                        .with(admin("enterprise.policies.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policy.id").value(policyId))
                .andExpect(jsonPath("$.data.versions").isArray())
                .andExpect(jsonPath("$.data.bindings").isArray())
                .andExpect(jsonPath("$.data.testCases").isArray());

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/policies/{id}/versions", policyId)
                        .with(admin("enterprise.policies.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(post("/internal/v1/authorization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiator\":\"mallory\",\"actor\":\"mallory\",\"effectiveSubject\":\"mallory\",\"enterpriseId\":\"ent-acme\",\"resource\":\"customers/42\",\"action\":\"read\",\"risk\":\"LOW\",\"requestId\":\"req-spoof\"}")
                        .with(internalCaller()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void denyOverridesAllowAndClientCannotSupplyFinalAllow() throws Exception {
        MvcResult created = create("policy-deny-override-1", denyOverrideBody());
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:publish", policyId)
                        .header("If-Match", created.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-deny-override-publish")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/internal/v1/authorization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"enterpriseId\":\"ent-acme\",\"resource\":\"customers/secret\",\"action\":\"read\",\"risk\":\"HIGH\",\"requestId\":\"req-policy-deny\",\"allow\":true}")
                        .with(internalCaller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("DENY"))
                .andExpect(jsonPath("$.data.allow").value(false))
                .andExpect(jsonPath("$.data.reasonCode").value("DENY_RULE_MATCH"))
                .andExpect(jsonPath("$.data.matchedRules", containsInAnyOrder("allow-customer-read", "deny-sensitive")));
    }

    @Test
    void defaultsToDenyAndRequiresApprovalForHighRiskRule() throws Exception {
        MvcResult created = create("policy-approval-1", approvalBody());
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:publish", policyId)
                        .header("If-Match", created.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-approval-publish")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/v1/authorization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"enterpriseId\":\"ent-acme\",\"resource\":\"payments/42\",\"action\":\"approve\",\"risk\":\"HIGH\",\"requestId\":\"req-policy-approval\"}")
                        .with(internalCaller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("APPROVAL_REQUIRED"))
                .andExpect(jsonPath("$.data.allow").value(false))
                .andExpect(jsonPath("$.data.approvalRequired").value(true));
        mockMvc.perform(post("/internal/v1/authorization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"enterpriseId\":\"ent-acme\",\"resource\":\"payments/42\",\"action\":\"refund\",\"risk\":\"LOW\",\"requestId\":\"req-policy-default-deny\"}")
                        .with(internalCaller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("DENY"))
                .andExpect(jsonPath("$.data.reasonCode").value("DEFAULT_DENY"));
    }

    @Test
    void rejectsCrossEnterpriseEvaluationAndStaleWrites() throws Exception {
        MvcResult created = create("policy-boundary-1", policyBody("Boundary policy", "boundary-policy"));
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/policies/{id}/draft", policyId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"stale\"}")
                        .header("If-Match", "\"999\"").header("Idempotency-Key", "policy-stale")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"))
                .andExpect(com.abluepoint.summer.sample.authorization.UnifiedBusinessFailure.unified("SUMMER_PRECONDITION_FAILED", "The resource has changed"));
        mockMvc.perform(post("/internal/v1/authorization/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"enterpriseId\":\"ent-other\",\"resource\":\"customers/42\",\"action\":\"read\",\"risk\":\"LOW\",\"requestId\":\"req-policy-cross-tenant\",\"allow\":true}")
                        .with(internalCaller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("DENY"))
                .andExpect(jsonPath("$.data.reasonCode").value("ENTERPRISE_CONTEXT_MISMATCH"));
    }

    @Test
    void pauseAndRevokeInvalidateRuntimePolicyCache() throws Exception {
        MvcResult created = create("policy-cache-1", policyBody("Cache policy", "cache-policy"));
        String policyId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.policyId");
        MvcResult published = mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:publish", policyId)
                        .header("If-Match", created.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-cache-publish")
                        .with(admin("enterprise.policies.write")))
                .andExpect(status().isOk()).andReturn();
        String body = "{\"initiator\":\"alice\",\"actor\":\"alice\",\"effectiveSubject\":\"alice\",\"enterpriseId\":\"ent-acme\",\"resource\":\"customers/42\",\"action\":\"read\",\"risk\":\"LOW\",\"organizationId\":\"org-engineering\",\"requestId\":\"req-policy-cache\"}";
        mockMvc.perform(post("/internal/v1/authorization/evaluate").contentType(MediaType.APPLICATION_JSON).content(body).with(internalCaller()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("ALLOW"));
        MvcResult paused = mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:pause", policyId)
                        .header("If-Match", published.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-cache-pause").with(admin("enterprise.policies.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lifecycleStatus").value("PAUSED")).andReturn();
        mockMvc.perform(post("/internal/v1/authorization/evaluate").contentType(MediaType.APPLICATION_JSON).content(body).with(internalCaller()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("DENY"))
                .andExpect(jsonPath("$.data.reasonCode").value("POLICY_NOT_ACTIVE"));
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies/{id}:revoke", policyId)
                        .header("If-Match", paused.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "policy-cache-revoke").with(admin("enterprise.policies.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.lifecycleStatus").value("REVOKED"));
    }

    private MvcResult create(String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/enterprises/ent-acme/policies")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", key).with(admin("enterprise.policies.write")))
                .andExpect(status().isCreated()).andReturn();
    }

    private static String policyBody(String name, String code) {
        return "{\"name\":\"" + name + "\",\"code\":\"" + code + "\",\"description\":\"demo\",\"policyType\":\"RESOURCE\",\"definition\":{\"defaultDecision\":\"DENY\",\"denyOverrides\":true,\"rules\":[{\"id\":\"allow-customer-read\",\"effect\":\"ALLOW\",\"subjects\":[\"alice\"],\"resources\":[\"customers/*\"],\"actions\":[\"read\"],\"conditions\":{\"organizationId\":\"org-engineering\"},\"riskLevels\":[\"LOW\"]}]}}";
    }

    private static String denyOverrideBody() {
        return "{\"name\":\"Deny override\",\"code\":\"deny-override\",\"definition\":{\"defaultDecision\":\"DENY\",\"denyOverrides\":true,\"rules\":[{\"id\":\"allow-customer-read\",\"effect\":\"ALLOW\",\"subjects\":[\"alice\"],\"resources\":[\"customers/*\"],\"actions\":[\"read\"]},{\"id\":\"deny-sensitive\",\"effect\":\"DENY\",\"subjects\":[\"*\"],\"resources\":[\"customers/secret\"],\"actions\":[\"read\"],\"riskLevels\":[\"HIGH\"]}]}}";
    }

    private static String approvalBody() {
        return "{\"name\":\"Approval policy\",\"code\":\"approval-policy\",\"definition\":{\"defaultDecision\":\"DENY\",\"denyOverrides\":true,\"rules\":[{\"id\":\"approve-payment\",\"effect\":\"ALLOW\",\"subjects\":[\"alice\"],\"resources\":[\"payments/*\"],\"actions\":[\"approve\"],\"riskLevels\":[\"HIGH\"],\"approvalRequired\":true}]}}";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return jwt().jwt(token -> token.subject("policy-admin").claim("scope", capability)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        )
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor internalCaller() {
        return jwt().jwt(token -> token.subject("connection-service").claim("scope", "internal.authorization.evaluate")
                        .claim("initiator", "alice")
                        .claim("actor", "alice")
                        .claim("effectiveSubject", "alice")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.authorization.evaluate"));
    }
}
