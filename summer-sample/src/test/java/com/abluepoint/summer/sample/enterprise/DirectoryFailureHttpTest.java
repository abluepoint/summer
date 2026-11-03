package com.abluepoint.summer.sample.enterprise;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DirectoryFailureHttpTest {
    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PLATFORM = "/api/v1/platform/enterprises";
    private static final String REVIEW = "/api/v1/platform/enterprise-reviews/application-acme";
    private static final String MEMBERS = "/api/v1/enterprises/ent-acme/members";

    @Test void enterpriseFailuresKeepTheirMessagesAndLeaveTheDirectoryAndAuditUnchanged() throws Exception {
        JsonNode before = data(get(PLATFORM).with(platform("platform.enterprise.read")));
        JsonNode audit = data(get(PLATFORM + "/ent-acme/audit").with(platform("platform.enterprise.read")));
        failure(post(PLATFORM + "/absent:freeze").header("If-Match", "\"stale\"")
                .header("Idempotency-Key", "missing").with(platform("platform.enterprise.write")),
                404, "SUMMER_ENTERPRISE_NOT_FOUND", "Enterprise not found: absent");
        failure(post(PLATFORM + "/ent-acme:freeze").header("If-Match", "\"stale\"")
                .header("Idempotency-Key", "stale").with(platform("platform.enterprise.write")),
                412, "SUMMER_PRECONDITION_FAILED", "The enterprise has changed");
        failure(post(PLATFORM + "/ent-acme:disable").header("If-Match", "\"stale\"")
                .header("Idempotency-Key", " ").with(platform("platform.enterprise.write")),
                400, "SUMMER_INVALID_ENTERPRISE_ACTION", "Idempotency-Key is required (maximum 128 characters)");
        assertEquals(before, data(get(PLATFORM).with(platform("platform.enterprise.read"))));
        assertEquals(audit, data(get(PLATFORM + "/ent-acme/audit").with(platform("platform.enterprise.read"))));
    }

    @Test void enterpriseReceiptReplayPrecedesStaleRevisionAndConflictingReplayDoesNotWrite() throws Exception {
        String revision = data(get(PLATFORM).with(platform("platform.enterprise.read"))).path("items").get(0).path("revision").asString();
        var first = mvc.perform(post(PLATFORM + "/ent-acme:freeze").header("If-Match", quote(revision))
                .header("Idempotency-Key", "receipt").with(platform("platform.enterprise.write"))).andExpect(status().isOk()).andReturn();
        var replay = mvc.perform(post(PLATFORM + "/ent-acme:freeze").header("If-Match", quote(revision))
                .header("Idempotency-Key", "receipt").with(platform("platform.enterprise.write"))).andExpect(status().isOk()).andReturn();
        assertEquals(body(first).path("data"), body(replay).path("data"));
        assertEquals(first.getResponse().getHeader("ETag"), replay.getResponse().getHeader("ETag"));
        failure(post(PLATFORM + "/ent-acme:disable").header("If-Match", quote(revision))
                .header("Idempotency-Key", "receipt").with(platform("platform.enterprise.write")),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another enterprise action");
        JsonNode audit = data(get(PLATFORM + "/ent-acme/audit").with(platform("platform.enterprise.read")));
        assertEquals(1, audit.size());
        assertEquals("freeze", audit.get(0).path("action").asString());
    }

    @Test void reviewFailuresKeepDistinctValidationConflictAndPreconditionContracts() throws Exception {
        var detail = mvc.perform(get(REVIEW).with(platform("platform.enterprise-review.read"))).andExpect(status().isOk()).andReturn();
        String etag = detail.getResponse().getHeader("ETag");
        JsonNode audit = data(get(REVIEW + "/audit").with(platform("platform.enterprise-review.read")));
        failure(post(REVIEW + ":approve").header("If-Match", etag).header("Idempotency-Key", " ")
                .with(platform("platform.enterprise-review.write")), 409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key is required (maximum 128 characters)");
        failure(post(REVIEW + ":reject").contentType("application/json").content("{\"reason\":\"\"}")
                .header("If-Match", etag).header("Idempotency-Key", "blank-reason").with(platform("platform.enterprise-review.write")),
                400, "SUMMER_INVALID_REVIEW", "reason is required");
        failure(post(REVIEW + ":approve").header("If-Match", "\"stale\"").header("Idempotency-Key", "review-stale")
                .with(platform("platform.enterprise-review.write")), 412, "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        assertEquals(body(detail).path("data"), data(get(REVIEW).with(platform("platform.enterprise-review.read"))));
        assertEquals(audit, data(get(REVIEW + "/audit").with(platform("platform.enterprise-review.read"))));
        var first = mvc.perform(post(REVIEW + ":approve").header("If-Match", etag).header("Idempotency-Key", "approve")
                .with(platform("platform.enterprise-review.write"))).andExpect(status().isOk()).andReturn();
        assertEquals(body(first).path("data"), body(mvc.perform(post(REVIEW + ":approve").header("If-Match", etag)
                .header("Idempotency-Key", "approve").with(platform("platform.enterprise-review.write"))).andExpect(status().isOk()).andReturn()).path("data"));
        failure(post(REVIEW + ":reject").contentType("application/json").content("{\"reason\":\"changed\"}")
                .header("If-Match", etag).header("Idempotency-Key", "approve").with(platform("platform.enterprise-review.write")),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another decision");
        failure(post(REVIEW + ":approve").header("If-Match", first.getResponse().getHeader("ETag")).header("Idempotency-Key", "illegal")
                .with(platform("platform.enterprise-review.write")), 400, "SUMMER_INVALID_REVIEW", "illegal status transition");
        assertEquals(1, data(get(REVIEW + "/audit").with(platform("platform.enterprise-review.read"))).size());
    }

    @Test void memberCreationKeepsNativeRefusalsAndTypedConflictsWithoutAddingMembers() throws Exception {
        JsonNode before = data(get(MEMBERS).with(tenant("enterprise.members.read")));
        var create = post(MEMBERS).contentType("application/json").content("{\"subject\":\"summer-user\",\"displayName\":\"Existing\",\"email\":\"existing@example.test\"}");
        failure(create.header("Idempotency-Key", "duplicate").with(tenant("enterprise.members.write")),
                409, "SUMMER_MEMBER_CONFLICT", "Subject already has a membership in this enterprise");
        failure(post(MEMBERS).contentType("application/json").content("{\"subject\":\"missing-login\",\"displayName\":\"Missing\",\"email\":\"missing@example.test\"}")
                .header("Idempotency-Key", "missing-login").with(tenant("enterprise.members.write")),
                400, "SUMMER_DIRECTORY_REQUEST_REJECTED", "An existing active login identity is required");
        failure(post(MEMBERS).contentType("application/json").content("{\"subject\":\"summer-user\",\"displayName\":\"Existing\",\"email\":\"existing@example.test\"}")
                .header("Idempotency-Key", " ").with(tenant("enterprise.members.write")),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key is required");
        failure(get(MEMBERS + "/absent").with(tenant("enterprise.members.read")),
                404, "SUMMER_DIRECTORY_REQUEST_REJECTED", "Directory resource was not found");
        failure(get(MEMBERS).param("page", "-1").with(tenant("enterprise.members.read")),
                400, "SUMMER_DIRECTORY_REQUEST_REJECTED", "page must be non-negative and size between 1 and 200");
        assertEquals(before, data(get(MEMBERS).with(tenant("enterprise.members.read"))));
    }

    @Test void memberMutationReplayAndFailuresPreserveOneAuditAndCurrentState() throws Exception {
        String etag = mvc.perform(get(MEMBERS + "/member-alice").with(tenant("enterprise.members.read")))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        failure(post(MEMBERS + "/member-alice:disable").header("If-Match", "\"stale\"").header("Idempotency-Key", "stale")
                .with(tenant("enterprise.members.write")), 412, "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        assertEquals(0, data(get(MEMBERS + "/member-alice/audit").with(tenant("enterprise.members.read"))).size());
        var first = mvc.perform(post(MEMBERS + "/member-alice:disable").header("If-Match", etag).header("Idempotency-Key", "disable")
                .with(tenant("enterprise.members.write"))).andExpect(status().isOk()).andReturn();
        var replay = mvc.perform(post(MEMBERS + "/member-alice:disable").header("If-Match", etag).header("Idempotency-Key", "disable")
                .with(tenant("enterprise.members.write"))).andExpect(status().isOk()).andReturn();
        assertEquals(body(first).path("data"), body(replay).path("data"));
        failure(post(MEMBERS + "/member-alice:enable").header("If-Match", etag).header("Idempotency-Key", "disable")
                .with(tenant("enterprise.members.write")), 409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another mutation");
        assertEquals("DISABLED", data(get(MEMBERS + "/member-alice").with(tenant("enterprise.members.read"))).path("status").asString());
        assertEquals(1, data(get(MEMBERS + "/member-alice/audit").with(tenant("enterprise.members.read"))).size());
    }

    @Test void memberCreationReceiptsReplayBeforeDuplicateMembershipChecks() throws Exception {
        JsonNode enterprise = body(mvc.perform(post(PLATFORM).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "Receipt enterprise", "code", "receipt-" + java.util.UUID.randomUUID())))
                .with(platform("platform.enterprise.write"))).andExpect(status().isCreated()).andReturn()).path("data");
        String id = enterprise.path("enterpriseId").asString(); String url = "/api/v1/enterprises/" + id + "/members";
        String payload = json.writeValueAsString(Map.of("subject", "summer-user", "displayName", "Receipt member", "email", "receipt@example.test"));
        var first = mvc.perform(post(url).contentType("application/json").content(payload).header("Idempotency-Key", "member-create-receipt")
                .with(tenantFor(id, "enterprise.members.write"))).andExpect(status().isCreated()).andReturn();
        var replay = mvc.perform(post(url).contentType("application/json").content(payload).header("Idempotency-Key", "member-create-receipt")
                .with(tenantFor(id, "enterprise.members.write"))).andExpect(status().isCreated()).andReturn();
        assertEquals(body(first).path("data"), body(replay).path("data"));
        failure(post(url).contentType("application/json").content(payload.replace("Receipt member", "Changed member"))
                .header("Idempotency-Key", "member-create-receipt").with(tenantFor(id, "enterprise.members.write")),
                409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another mutation");
        String memberId = body(first).path("data").path("memberId").asString();
        assertEquals(1, data(get(url + "/" + memberId + "/audit").with(tenantFor(id, "enterprise.members.read"))).size());
    }

    @Test void narrowPlatformPermissionAdapterRetainsItsExplicitMessage() throws Exception {
        failure(post(PLATFORM + "/ent-acme:freeze").header("If-Match", "\"stale\"").header("Idempotency-Key", "denied")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_platform.enterprise.write"))),
                403, "SUMMER_FORBIDDEN", "platform capability required");
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception { return body(mvc.perform(request).andExpect(status().isOk()).andReturn()).path("data"); }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    private void failure(MockHttpServletRequestBuilder request, int code, String error, String message) throws Exception {
        mvc.perform(request.header("X-Request-Id", "directory-failure"))
                .andExpect(status().is(code)).andExpect(header().string("X-Request-Id", "directory-failure"))
                .andExpect(jsonPath("$.requestId").value("directory-failure"))
                .andExpect(jsonPath("$.error.code").value(error)).andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.violations").isEmpty()).andExpect(jsonPath("$.error.cause").doesNotExist())
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }
    private static String quote(String revision) { return "\"" + revision + "\""; }
    private static RequestPostProcessor platform(String capability) {
        return jwt().jwt(token -> token.subject("directory-operator").claim("platform_capabilities", List.of(capability)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
    private static RequestPostProcessor tenant(String capability) { return tenantFor("ent-acme", capability); }
    private static RequestPostProcessor tenantFor(String enterprise, String capability) {
        return jwt().jwt(token -> token.subject("directory-admin").claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterprise, "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of(enterprise, List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
