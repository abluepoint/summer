package com.abluepoint.summer.sample.physicalconnection;
import com.jayway.jsonpath.JsonPath;import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.http.MediaType;import org.springframework.security.core.authority.SimpleGrantedAuthority;import org.springframework.test.annotation.DirtiesContext;import org.springframework.test.web.servlet.MockMvc;import org.springframework.test.web.servlet.MvcResult;import java.util.List;import java.util.Map;import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class PhysicalConnectionControllerTest{@Autowired MockMvc mockMvc;
    @org.springframework.beans.factory.annotation.Autowired
    com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void grantCurrentInterfaces() throws Exception {
        var enterprise = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
        current.grantHandlers(enterprise, "admin", (handler, path, method) -> handler.getBeanType().getName().equals(
                "com.abluepoint.summer.sample.mcpauthorization.McpAuthorizationController") && method.equals("POST")
                && path.contains("/mcp-execution-permits/") && path.endsWith(":revoke"));
        current.grantHandlers(enterprise, "auditor", (handler, path, method) -> handler.getBeanType().getName().equals(
                "com.abluepoint.summer.sample.auditledger.AuditLedgerController") && method.equals("GET")
                && java.util.Set.of("/api/v1/enterprises/{enterpriseId}/audit-events/{eventId}",
                        "/api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}").contains(path));
    }

    @Test void nativeAccessDenialsKeepTheirOriginalMessageAndCause() throws Exception {
        String path = "/internal/v1/connections/ext-1/tools/search:invoke";
        MvcResult result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(invokeBody("missing", "attempt-denied", "session-denied")).with(service("cs-1", "ctx-denied", false)).header("X-Request-Id", "native-denied"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Connection service trust denied"))
                .andExpect(jsonPath("$.requestId").value("native-denied"))
                .andExpect(header().string("X-Request-Id", "native-denied"))
                .andExpect(jsonPath("$.error.cause").doesNotExist()).andReturn();
        var error = org.junit.jupiter.api.Assertions.assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class,
                result.getResolvedException());
        var cause = org.junit.jupiter.api.Assertions.assertInstanceOf(org.springframework.security.access.AccessDeniedException.class,
                error.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(cause.getMessage(), error.explicitMessage());
    }

 @Test void physicalValidationAndBindingConflictsKeepTheirOriginalMessages() throws Exception {
  physicalFailure("{}", "ctx-own", 400, "SUMMER_EXECUTION_INVALID", "permit, attempt, digests and session are required");
  String p = permit("ctx-own");
  String body = invokeBody(p, "attempt-own", "session-own");
  physicalFailure(body.replace("\"schema\"", "\"wrong\""), "ctx-own", 409,
    "SUMMER_EXECUTION_CONFLICT", "Permit binding mismatch");
  mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON)
    .content(body).with(service("cs-1", "ctx-own"))).andExpect(status().isOk());
  physicalFailure(body.replace("session-own", "session-changed"), "ctx-own", 409,
    "SUMMER_EXECUTION_CONFLICT", "Execution session mismatch");
 }

 @Test void revokedAndPreviouslyClaimedPermitsKeepPhysicalConflictsAndCauses() throws Exception {
  String revoked = permit("ctx-revoked");
  mockMvc.perform(post("/api/v1/enterprises/ent-acme/mcp-execution-permits/{id}:revoke", revoked)
    .with(jwt().jwt(t -> t.claim("scope", "enterprise.mcp.execution.write").subject("admin")
      .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
      .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.mcp.execution.write"))))
      .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.mcp.execution.write"))))
    .andExpect(status().isOk());
  MvcResult rejected = physicalFailure(invokeBody(revoked, "attempt-revoked", "session-revoked"), "ctx-revoked", 409,
    "SUMMER_EXECUTION_CONFLICT", "Permit is not claimable");
  assertUpstreamCode(rejected, com.abluepoint.summer.sample.mcpauthorization.McpAuthorizationErrorCode.CONFLICT);
  String claimed = permit("ctx-claimed");
  mockMvc.perform(post("/internal/v1/mcp/execution-permits/{id}:claim", claimed).contentType(MediaType.APPLICATION_JSON)
    .content("{\"executionAttemptId\":\"attempt-first\"}").with(service("cs-1", "ctx-claimed")))
    .andExpect(status().isOk());
  rejected = physicalFailure(invokeBody(claimed, "attempt-second", "session-claimed"), "ctx-claimed", 409,
    "SUMMER_EXECUTION_CONFLICT", "Permit already claimed by another attempt");
  assertUpstreamCode(rejected, com.abluepoint.summer.sample.mcpauthorization.McpAuthorizationErrorCode.CONFLICT);
 }

 @Test void auditConflictsKeepPhysicalStatusMessagesCausesAndCachedAttempts() throws Exception {
  for (boolean immutable : List.of(false, true)) {
   String ctx = immutable ? "ctx-audit-immutable" : "ctx-audit-idempotency";
   MvcResult decision = decide(ctx);
   String json = decision.getResponse().getContentAsString();
   String p = JsonPath.read(json, "$.data.permit.permitId");
   String invocation = JsonPath.read(json, "$.data.invocationId");
   String attempt = immutable ? "attempt-immutable" : "attempt-idempotency";
   String eventId = immutable ? "physical-execution-" + invocation + "-" + attempt : "preseed-" + invocation;
   String key = immutable ? "preseed-key-" + invocation : "physical-execution:" + invocation + ":" + attempt;
   String event = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"PRESEEDED\",\"enterpriseId\":\"ent-acme\","
     + "\"invocationId\":\"" + invocation + "\",\"detail\":{\"fixture\":\"original\"}}";
   mockMvc.perform(post("/internal/v1/audit-events").contentType(MediaType.APPLICATION_JSON).content(event)
     .header("Idempotency-Key", key).with(service("cs-1", ctx))).andExpect(status().isCreated());
   String body = invokeBody(p, attempt, "session-" + attempt);
   MvcResult rejected = physicalFailure(body, ctx, immutable ? 500 : 409,
     immutable ? "SUMMER_INTERNAL_ERROR" : "SUMMER_EXECUTION_CONFLICT",
     immutable ? "Event is immutable" : "Idempotency key reused");
   assertUpstreamCode(rejected, immutable
     ? com.abluepoint.summer.sample.auditledger.AuditLedgerErrorCode.EVENT_IMMUTABLE
     : com.abluepoint.summer.sample.auditledger.AuditLedgerErrorCode.IDEMPOTENCY_CONFLICT);
   mockMvc.perform(get("/api/v1/enterprises/ent-acme/audit-events/{id}", eventId).with(auditor()))
     .andExpect(status().isOk()).andExpect(jsonPath("$.data.detail.fixture").value("original"));
   // The original execution caches the attempt before audit; retry returns that result without rerunning audit.
   mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON)
     .content(body).with(service("cs-1", ctx))).andExpect(status().isOk())
     .andExpect(jsonPath("$.data.executionAttemptId").value(attempt))
     .andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED"));
  }
 }

 private MvcResult physicalFailure(String body, String ctx, int http, String code, String message) throws Exception {
  return mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON)
    .content(body).with(service("cs-1", ctx)).header("X-Request-Id", "physical-rejected"))
    .andExpect(status().is(http)).andExpect(jsonPath("$.error.code").value(code))
    .andExpect(jsonPath("$.error.message").value(message)).andExpect(jsonPath("$.requestId").value("physical-rejected"))
    .andExpect(header().string("X-Request-Id", "physical-rejected"))
    .andExpect(jsonPath("$.error.cause").doesNotExist()).andReturn();
 }
 private static void assertUpstreamCode(MvcResult result, com.abluepoint.summer.common.error.ErrorCode code) {
  var error = org.junit.jupiter.api.Assertions.assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class,
    result.getResolvedException());
  var cause = org.junit.jupiter.api.Assertions.assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class,
    error.getCause());
  org.junit.jupiter.api.Assertions.assertSame(code, cause.errorCode());
 }
 private static String invokeBody(String permit, String attempt, String session) {
  return "{\"permitId\":\"" + permit + "\",\"executionAttemptId\":\"" + attempt
    + "\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"sessionRef\":\"" + session + "\"}";
 }
 @Test void missingPermitKeepsPhysicalConflictAndItsOriginalMessage() throws Exception {
  mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke")
    .contentType(MediaType.APPLICATION_JSON)
    .header("X-Request-Id", "physical-missing-permit")
    .content("{\"permitId\":\"missing\",\"executionAttemptId\":\"attempt-missing\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"sessionRef\":\"session-a\"}")
    .with(service("cs-1", "ctx-missing")))
    .andExpect(status().isConflict())
    .andExpect(jsonPath("$.error.code").value("SUMMER_EXECUTION_CONFLICT"))
    .andExpect(jsonPath("$.error.message").value("Permit not found"))
    .andExpect(jsonPath("$.requestId").value("physical-missing-permit"))
    .andExpect(header().string("X-Request-Id", "physical-missing-permit"));
 }
 @Test void invokesWithPermitAndReturnsRedactedExecutionEvidence()throws Exception{String p=permit("ctx-p");String b="{\"permitId\":\""+p+"\",\"executionAttemptId\":\"attempt-1\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"sessionRef\":\"session-a\",\"timeoutMs\":1000,\"maxRetries\":1,\"mcpAccessToken\":\"secret-token\"}";mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(b).with(service("cs-1","ctx-p"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED")).andExpect(jsonPath("$.data.executionAttemptId").value("attempt-1")).andExpect(jsonPath("$.data.mcpAccessToken").doesNotExist()).andExpect(jsonPath("$.data.result").doesNotExist());}
 @Test void sameAttemptIsIdempotentDifferentAttemptConflictsAndSessionCannotChange()throws Exception{String p=permit("ctx-i");String b="{\"permitId\":\""+p+"\",\"executionAttemptId\":\"attempt-idem\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"sessionRef\":\"session-a\"}";mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(b).with(service("cs-1","ctx-i"))).andExpect(status().isOk());mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(b).with(service("cs-1","ctx-i"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.executionAttemptId").value("attempt-idem"));mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(b.replace("attempt-idem","attempt-other")).with(service("cs-1","ctx-i"))).andExpect(status().isConflict());mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(b.replace("session-a","session-b")).with(service("cs-1","ctx-i"))).andExpect(status().isConflict());}
 @Test void successfulPhysicalExecutionIsVisibleInAuditLedger()throws Exception{String p=permit("ctx-audit");String body="{\"permitId\":\""+p+"\",\"executionAttemptId\":\"attempt-audit\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"sessionRef\":\"session-a\"}";MvcResult result=mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(body).with(service("cs-1","ctx-audit"))).andExpect(status().isOk()).andReturn();String invocationId=JsonPath.read(result.getResponse().getContentAsString(),"$.data.invocationId");mockMvc.perform(get("/api/v1/enterprises/ent-acme/mcp-invocations/{id}",invocationId).with(auditor())).andExpect(status().isOk()).andExpect(jsonPath("$.data.invocationId").value(invocationId)).andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED")).andExpect(jsonPath("$.data.actor").value("agent")).andExpect(jsonPath("$.data.effectiveSubject").value("user"));}
 @Test void validatesSchemaArgumentsAndTimeoutUnknownState()throws Exception{String p=permit("ctx-validation");String mismatch="{\"permitId\":\""+p+"\",\"executionAttemptId\":\"attempt-schema\",\"schemaDigest\":\"wrong\",\"argumentsDigest\":\"args\",\"sessionRef\":\"session-a\"}";mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(mismatch).with(service("cs-1","ctx-validation"))).andExpect(status().isConflict());String p2=permit("ctx-timeout");String timeout="{\"permitId\":\""+p2+"\",\"executionAttemptId\":\"attempt-timeout\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"sessionRef\":\"session-a\",\"timeoutMs\":1,\"maxRetries\":2}";mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(timeout).with(service("cs-1","ctx-timeout"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.resultStatus").value("EXECUTION_UNKNOWN")).andExpect(jsonPath("$.data.attempts").value(3));}
 @Test void untrustedServiceCannotInvoke()throws Exception{String p=permit("ctx-untrusted");String body="{\"permitId\":\""+p+"\",\"executionAttemptId\":\"attempt-untrusted\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"sessionRef\":\"session-a\"}";mockMvc.perform(post("/internal/v1/connections/ext-1/tools/search:invoke").contentType(MediaType.APPLICATION_JSON).content(body).with(service("cs-1","ctx-untrusted",false))).andExpect(status().isForbidden());}
 private MvcResult decide(String ctx)throws Exception{MvcResult r=mockMvc.perform(post("/internal/v1/mcp/execution-decisions").contentType(MediaType.APPLICATION_JSON).content("{\"enterpriseId\":\"ent-acme\",\"connectionServiceId\":\"cs-1\",\"connectionBindingId\":\"bind\",\"externalConnectionId\":\"ext-1\",\"executionContextId\":\""+ctx+"\",\"actorPrincipalId\":\"agent\",\"effectiveSubjectPrincipalId\":\"user\",\"toolId\":\"search\",\"toolName\":\"search\",\"schemaDigest\":\"schema\",\"argumentsDigest\":\"args\",\"audience\":\"urn:aict:cs-1\",\"risk\":\"LOW\"}").with(service("cs-1",ctx))).andExpect(status().isOk()).andReturn();return r;}
 private String permit(String ctx)throws Exception{return JsonPath.read(decide(ctx).getResponse().getContentAsString(),"$.data.permit.permitId");}
 private static org.springframework.test.web.servlet.request.RequestPostProcessor service(String id,String ctx){return jwt().jwt(t->t.subject("connection-service").claim("connection_service_id",id).claim("service_trust_verified",true).claim("execution_context_id",ctx).claim("actor_principal_id","agent").claim("effective_subject_principal_id","user")).authorities(new SimpleGrantedAuthority("SCOPE_internal.mcp.execution.decide"),new SimpleGrantedAuthority("SCOPE_internal.audit.write"));}
 private static org.springframework.test.web.servlet.request.RequestPostProcessor service(String id,String ctx,boolean trusted){return jwt().jwt(t->t.subject("connection-service").claim("connection_service_id",id).claim("service_trust_verified",trusted).claim("execution_context_id",ctx).claim("actor_principal_id","agent").claim("effective_subject_principal_id","user")).authorities(new SimpleGrantedAuthority("SCOPE_internal.mcp.execution.decide"),new SimpleGrantedAuthority("SCOPE_internal.audit.write"));}
 private static org.springframework.test.web.servlet.request.RequestPostProcessor auditor(){return jwt().jwt(t->t.claim("scope", "enterprise.audit.read").subject("auditor").claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))).claim("enterprise_capabilities",Map.of("ent-acme",List.of("enterprise.audit.read")))).authorities(new SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));}
}
