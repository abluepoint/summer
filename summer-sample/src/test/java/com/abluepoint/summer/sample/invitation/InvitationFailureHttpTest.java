package com.abluepoint.summer.sample.invitation;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class InvitationFailureHttpTest {
    @Autowired CurrentManagedAuthorizationTestFixture managed;

    @BeforeEach
    void prepareCurrentAuthorization() throws Exception {
        managed.grantHandlers(Domain.enterprise("ent-acme"), "invitation-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.invitation.InvitationController")
                && java.util.Set.of("create POST /api/v1/enterprises/{enterpriseId}/invitations",
                        "get GET /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}",
                        "list GET /api/v1/enterprises/{enterpriseId}/invitations",
                        "audit GET /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}/audit",
                        "resend POST /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:resend",
                        "revoke POST /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:revoke",
                        "applications GET /api/v1/enterprises/{enterpriseId}/join-applications",
                        "appAudit GET /api/v1/enterprises/{enterpriseId}/join-applications/{id}/audit",
                        "approve POST /api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve",
                        "reject POST /api/v1/enterprises/{enterpriseId}/join-applications/{id}:reject")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "invitation-admin", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.member.MemberController")
                && java.util.Set.of("list GET /api/v1/enterprises/{enterpriseId}/members")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));
        managed.grantHandlers(Domain.enterprise("ent-acme"), "alice", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.organization.OrganizationController")
                && java.util.Set.of("members GET /api/v1/enterprises/{enterpriseId}/org-units/{unitId}/members")
                        .contains(handler.getMethod().getName() + " " + method + " " + path));

    }

    @Autowired MockMvc mvc;
    @Autowired org.springframework.core.env.Environment environment;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean InvitationService invitationService;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.abluepoint.summer.sample.enterprise.EnterpriseRegistry enterpriseRegistry;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private static final String INV = "/api/v1/enterprises/ent-acme/invitations";
    private static final String JOIN = "/api/v1/enterprises/ent-acme/join-applications";
    private static final String UNUSABLE = "SUMMER_INVITATION_UNUSABLE";
    private static final String IDEMPOTENCY = "SUMMER_IDEMPOTENCY_CONFLICT";
    private static final String PRECONDITION = "SUMMER_PRECONDITION_FAILED";
    private static final String JOIN_CONFLICT = "SUMMER_JOIN_APPLICATION_CONFLICT";

    @Test void invitationLookupKeeps404WhileMissingMutationTargetsKeep409() throws Exception {
        failure(get(INV + "/absent").with(admin("enterprise.invitation.read")), 404, "SUMMER_NOT_FOUND", "Invitation not found");
        // An actually missing enterprise cannot satisfy the current HTTP guard.
        mvc.perform(get("/api/v1/enterprises/absent/invitations/absent")
                        .with(adminFor("absent", "enterprise.invitation.read")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
        // Model a directory lookup disappearing after authorization: keep real current guard facts,
        // then inject only the business existence lookup to exercise the native 404/409 adapters.
        jdbc.update("insert into summer_enterprise(enterprise_id,name,code,status,review_status,version,created_at,updated_at) values('absent','Native lookup boundary','native-lookup-boundary','ACTIVE','PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        try {
            var domain = Domain.enterprise("absent");
            managed.activeUser(domain, environment.getRequiredProperty("summer.security.authorization-server.user.username"));
            managed.install(domain);
            managed.grantHandlers(domain, "invitation-admin", (handler, path, method) ->
                    handler.getBeanType().getName().equals("com.abluepoint.summer.sample.invitation.InvitationController")
                    && java.util.Set.of("get GET /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}",
                            "resend POST /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:resend",
                            "revoke POST /api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:revoke")
                            .contains(handler.getMethod().getName() + " " + method + " " + path));
            org.mockito.Mockito.doReturn(false).when(enterpriseRegistry).exists("absent");
            failure(get("/api/v1/enterprises/absent/invitations/absent").with(adminFor("absent", "enterprise.invitation.read")),
                    404, "SUMMER_NOT_FOUND", "Enterprise not found");
            for (String action : List.of("resend", "revoke")) {
                failure(post(INV + "/absent:" + action).header("If-Match", "\"v1\"").header("Idempotency-Key", key())
                        .with(admin("enterprise.invitation.write")), 409, UNUSABLE, "Invitation not found");
                failure(post("/api/v1/enterprises/absent/invitations/absent:" + action).header("If-Match", "\"v1\"")
                        .header("Idempotency-Key", key()).with(adminFor("absent", "enterprise.invitation.write")), 409, UNUSABLE, "Enterprise not found");
            }
        } finally {
            org.mockito.Mockito.reset(enterpriseRegistry);
            jdbc.update("delete from summer_enterprise where enterprise_id='absent' and code='native-lookup-boundary'");
        }
    }

    @Test void validationAndRequiredKeysKeepTheirLegacyStatusAndMessages() throws Exception {
        failure(post(INV).contentType("application/json").content("{\"inviteeEmail\":\"\"}")
                .header("Idempotency-Key", key()).with(admin("enterprise.invitation.write")), 400, "SUMMER_INVITATION_INVALID", "inviteeEmail is required");
        failure(post(INV).contentType("application/json").content("{\"inviteeEmail\":\"valid@example.test\"}")
                .with(admin("enterprise.invitation.write")), 409, IDEMPOTENCY, "Idempotency-Key is required");
        String email = key() + "@example.test";
        failure(post("/api/v1/enterprises/absent/join-applications").contentType("application/json")
                .content(json.writeValueAsString(Map.of("applicantEmail", email))).header("Idempotency-Key", key())
                .with(applicant(email)), 400, "SUMMER_INVITATION_INVALID", "Invalid join application");
        failure(post(JOIN).contentType("application/json").content(json.writeValueAsString(Map.of("applicantEmail", email)))
                .with(applicant(email)), 409, IDEMPOTENCY, "Idempotency-Key is required");
    }

    @Test void creationAndSubmissionReceiptsRejectChangedPayloadAndReplayExactlyOnce() throws Exception {
        String createKey = key(); String email = key() + "@example.test";
        JsonNode invitation = issue(email, null, createKey, 3600);
        assertEquals(invitation, issue(email, null, createKey, 3600));
        failure(post(INV).contentType("application/json").content(json.writeValueAsString(Map.of("inviteeEmail", "changed@example.test")))
                .header("Idempotency-Key", createKey).with(admin("enterprise.invitation.write")),
                409, IDEMPOTENCY, "Idempotency-Key was already used for another mutation");
        assertEquals(1, invitationAudit(invitation.path("invitationId").asString()).size());
        String submitKey = key(); JsonNode application = submit(email, null, submitKey);
        assertEquals(application, submit(email, null, submitKey));
        failure(post(JOIN).contentType("application/json").content(json.writeValueAsString(Map.of("applicantEmail", email, "applicantName", "Changed")))
                .header("Idempotency-Key", submitKey).with(applicant(email)), 409, IDEMPOTENCY, "Idempotency-Key was already used for another mutation");
        assertEquals(1, applicationAudit(application.path("applicationId").asString()).size());
    }

    @ParameterizedTest(name = "invitationMutationFailuresAndReplayPreserveRevisionTokenAndAudit[{index}] action={0}") @ValueSource(strings = {"resend", "revoke"})
    void invitationMutationFailuresAndReplayPreserveRevisionTokenAndAudit(String action) throws Exception {
        JsonNode created = issue(key() + "@example.test", null, key(), 3600); String id = created.path("invitationId").asString();
        String url = INV + "/" + id + ":" + action;
        failure(post(url).header("If-Match", "\"stale\"").header("Idempotency-Key", key()).with(admin("enterprise.invitation.write")),
                412, PRECONDITION, "The resource has changed");
        failure(post(url).header("If-Match", "\"v1\"").with(admin("enterprise.invitation.write")), 409, IDEMPOTENCY, "Idempotency-Key is required");
        assertEquals("v1", invitation(id).path("revision").asString()); assertEquals(1, invitationAudit(id).size());
        String mutationKey = key(); JsonNode first = success(post(url).header("If-Match", "\"v1\"").header("Idempotency-Key", mutationKey)
                .with(admin("enterprise.invitation.write")), 200);
        assertEquals(first, success(post(url).header("If-Match", "\"v1\"").header("Idempotency-Key", mutationKey)
                .with(admin("enterprise.invitation.write")), 200));
        failure(post(url).header("If-Match", "\"v2\"").header("Idempotency-Key", mutationKey).with(admin("enterprise.invitation.write")),
                409, IDEMPOTENCY, "Idempotency-Key was already used for another mutation");
        assertEquals("v2", invitation(id).path("revision").asString()); assertEquals(2, invitationAudit(id).size());
        if (action.equals("revoke")) failure(post(url).header("If-Match", "\"v2\"").header("Idempotency-Key", key())
                .with(admin("enterprise.invitation.write")), 409, UNUSABLE, "Invitation is no longer pending");
    }

    @ParameterizedTest(name = "consumptionKeepsContextUnusableAndReceiptFailuresDistinct[{index}] action={0}") @ValueSource(strings = {"accept", "reject"})
    void consumptionKeepsContextUnusableAndReceiptFailuresDistinct(String action) throws Exception {
        String email = key() + "@example.test"; JsonNode created = issue(email, null, key(), 3600);
        String id = created.path("invitationId").asString(); String url = "/api/v1/invitations/" + created.path("token").asString() + ":" + action;
        failure(post(url).contentType("application/json").content("{\"enterpriseId\":\"other\"}").header("Idempotency-Key", key())
                .with(invitee(email)), 409, "SUMMER_INVITATION_CONTEXT_MISMATCH", "Invitation enterprise context does not match token");
        failure(post("/api/v1/invitations/absent:" + action).header("Idempotency-Key", key()).with(invitee(email)),
                409, UNUSABLE, "Invitation token is invalid");
        failure(post(url).with(invitee(email)), 409, IDEMPOTENCY, "Idempotency-Key is required");
        assertEquals("PENDING", invitation(id).path("state").asString()); assertEquals(1, invitationAudit(id).size());
        String consumeKey = key(); JsonNode first = success(post(url).header("Idempotency-Key", consumeKey).with(invitee(email)), 200);
        assertEquals(first, success(post(url).header("Idempotency-Key", consumeKey).with(invitee(email)), 200));
        // A second authenticated subject with the same verified email is a legal identity but a different fingerprint.
        failure(post(url).header("Idempotency-Key", consumeKey).with(jwt().jwt(token -> token.subject("alternate-subject")
                .claim("email", email).claim("email_verified", true)).authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))),
                409, IDEMPOTENCY, "Idempotency-Key was already used for another mutation");
        failure(post(url).header("Idempotency-Key", key()).with(invitee(email)), 409, UNUSABLE, "Invitation is not usable");
        assertEquals(2, invitationAudit(id).size()); assertEquals(first.path("state"), invitation(id).path("state"));
    }

    @Test void expirationStillMarksTheInvitationExpiredBeforeRefusingConsumption() throws Exception {
        String email = key() + "@example.test"; JsonNode created = issue(email, null, key(), -1);
        failure(post("/api/v1/invitations/" + created.path("token").asString() + ":accept").header("Idempotency-Key", key()).with(invitee(email)),
                409, UNUSABLE, "Invitation has expired");
        JsonNode view = invitation(created.path("invitationId").asString());
        assertEquals("EXPIRED", view.path("state").asString()); assertEquals("v1", view.path("revision").asString());
        assertEquals(1, invitationAudit(created.path("invitationId").asString()).size());
    }

    @ParameterizedTest(name = "joinMutationFailuresKeepTheirEntryContractsAndDoNotAppendAudit[{index}] action={0}") @ValueSource(strings = {"supplement", "approve", "reject", "withdraw"})
    void joinMutationFailuresKeepTheirEntryContractsAndDoNotAppendAudit(String action) throws Exception {
        String email = key() + "@example.test"; JsonNode created = submit(email, null, key()); String id = created.path("applicationId").asString();
        failure(joinRequest(action, "absent", email).header("If-Match", "\"v1\"").header("Idempotency-Key", key()),
                409, JOIN_CONFLICT, "Join application not found");
        failure(joinRequest(action, id, email).header("If-Match", "\"stale\"").header("Idempotency-Key", key()),
                412, PRECONDITION, "The resource has changed");
        failure(joinRequest(action, id, email).header("If-Match", "\"v1\""), 409, IDEMPOTENCY, "Idempotency-Key is required");
        assertEquals(created, application(id)); assertEquals(1, applicationAudit(id).size());
        String mutationKey = key(); JsonNode first = success(joinRequest(action, id, email).header("If-Match", "\"v1\"")
                .header("Idempotency-Key", mutationKey), 200);
        assertEquals(first, success(joinRequest(action, id, email).header("If-Match", "\"v1\"").header("Idempotency-Key", mutationKey), 200));
        failure(joinRequest(action, id, email).header("If-Match", "\"v2\"").header("Idempotency-Key", mutationKey),
                409, IDEMPOTENCY, "Idempotency-Key was already used for another mutation");
        assertEquals(2, applicationAudit(id).size());
        if (action.equals("supplement")) {
            first = success(joinRequest("withdraw", id, email).header("If-Match", "\"v2\"").header("Idempotency-Key", key()), 200);
        }
        String expected = switch (action) {
            case "supplement" -> "Join application is no longer editable";
            case "approve" -> "Join application cannot be approved in current state";
            case "reject" -> "Join application cannot be changed in current state";
            default -> "Join application cannot be withdrawn";
        };
        JsonNode beforeAudit = applicationAudit(id);
        failure(joinRequest(action, id, email).header("If-Match", quote(first.path("revision").asString())).header("Idempotency-Key", key()),
                409, JOIN_CONFLICT, expected);
        assertEquals(first, application(id)); assertEquals(beforeAudit, applicationAudit(id));
    }

    @Test void invitationProvisioningFailureRollsBackMembersAndLeavesNoReceiptOrAudit() throws Exception {
        JsonNode members = members(); JsonNode organization = organizationMembers();
        String email = key() + "@example.test"; JsonNode created = issue(email, "missing-organization", key(), 3600);
        String id = created.path("invitationId").asString(); String consumeKey = key();
        for (int attempt = 0; attempt < 2; attempt++) failure(post("/api/v1/invitations/" + created.path("token").asString() + ":accept")
                .header("Idempotency-Key", consumeKey).with(invitee(email)), 409, UNUSABLE, "An active organization in this enterprise is required");
        assertEquals(members, members()); assertEquals(organization, organizationMembers());
        assertEquals("PENDING", invitation(id).path("state").asString()); assertEquals("v1", invitation(id).path("revision").asString());
        assertEquals(1, invitationAudit(id).size());
    }

    @Test void approvalProvisioningFailureRollsBackMembersAndLeavesApplicationUnchanged() throws Exception {
        JsonNode members = members(); JsonNode organization = organizationMembers();
        String email = key() + "@example.test"; JsonNode created = submit(email, "missing-organization", key()); String id = created.path("applicationId").asString();
        String approveKey = key();
        for (int attempt = 0; attempt < 2; attempt++) failure(joinRequest("approve", id, email).header("If-Match", "\"v1\"")
                .header("Idempotency-Key", approveKey), 500, "SUMMER_INTERNAL_ERROR", "An unexpected error occurred");
        assertEquals(members, members()); assertEquals(organization, organizationMembers());
        assertEquals(created, application(id)); assertEquals(1, applicationAudit(id).size());
    }

    @Test void inactiveMembershipKeepsTheConsumptionAndApprovalFailureContracts() throws Exception {
        JsonNode invitation = issue("bob", null, key(), 3600);
        failure(post("/api/v1/invitations/" + invitation.path("token").asString() + ":accept")
                .header("Idempotency-Key", key()).with(invitee("bob")), 409, UNUSABLE, "Membership is not active");
        JsonNode application = submit("bob", null, key());
        failure(joinRequest("approve", application.path("applicationId").asString(), "bob").header("If-Match", "\"v1\"")
                .header("Idempotency-Key", key()), 500, "SUMMER_INTERNAL_ERROR", "An unexpected error occurred");
        assertEquals("PENDING", invitation(invitation.path("invitationId").asString()).path("state").asString());
        assertEquals(application, application(application.path("applicationId").asString()));
    }

    @Test void auditMissingIdsRemainEmptyAndNativeListPermissionAdaptersKeepTheirMessage() throws Exception {
        assertEquals(0, invitationAudit("absent").size()); assertEquals(0, applicationAudit("absent").size());
        for (var entry : Map.of(INV, "enterprise.invitation.read", JOIN, "enterprise.join-application.read").entrySet()) {
            var denied = new org.springframework.security.access.AccessDeniedException("Enterprise capability required");
            // Exercise the controller's native adapter after the real current HTTP authorization succeeds.
            try {
                if (entry.getKey().equals(INV)) {
                    org.mockito.Mockito.doThrow(denied).when(invitationService).listInvitations(
                            org.mockito.ArgumentMatchers.eq("ent-acme"), org.mockito.ArgumentMatchers.isNull(),
                            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1),
                            org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));
                } else {
                    org.mockito.Mockito.doThrow(denied).when(invitationService).listApplications(
                            org.mockito.ArgumentMatchers.eq("ent-acme"), org.mockito.ArgumentMatchers.isNull(),
                            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1),
                            org.mockito.ArgumentMatchers.eq(20), org.mockito.ArgumentMatchers.any(org.springframework.security.core.Authentication.class));
                }
                var result = failure(get(entry.getKey()).with(admin(entry.getValue())),
                        403, "SUMMER_FORBIDDEN", "Enterprise capability required");
                var wrapped = assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class, result.getResolvedException());
                assertSame(denied, wrapped.getCause());
            } finally {
                org.mockito.Mockito.reset(invitationService);
            }
        }
    }

    @Test void authorizedListFailuresKeepTheirExistingLocal403Adapters() throws Exception {
        for (var entry : Map.of(INV, "enterprise.invitation.read", JOIN, "enterprise.join-application.read").entrySet()) {
            // Existing pagination overflows before subList; retain the local refusal adapter, including the JDK message.
            mvc.perform(get(entry.getKey()).param("page", Integer.toString(Integer.MAX_VALUE)).param("pageSize", "100")
                            .header("X-Request-Id", "native-list-failure").with(admin(entry.getValue())))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                    .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("-200")))
                    .andExpect(jsonPath("$.requestId").value("native-list-failure"))
                    .andExpect(header().string("X-Request-Id", "native-list-failure"))
                    .andExpect(jsonPath("$.error.cause").doesNotExist());
        }
    }

    private MockHttpServletRequestBuilder joinRequest(String action, String id, String email) {
        boolean own = action.equals("supplement") || action.equals("withdraw");
        MockHttpServletRequestBuilder request = action.equals("supplement") ? patch(JOIN + "/" + id).contentType("application/json").content("{\"materials\":[\"evidence\"]}") : post(JOIN + "/" + id + ":" + action);
        return request.with(own ? applicant(email) : admin("enterprise.join-application.review"));
    }
    private JsonNode issue(String email, String organization, String key, int ttl) throws Exception {
        var content = new java.util.HashMap<String, Object>(); content.put("inviteeEmail", email); content.put("expiresInSeconds", ttl);
        if (organization != null) content.put("organizationId", organization);
        return success(post(INV).contentType("application/json").content(json.writeValueAsString(content)).header("Idempotency-Key", key)
                .with(admin("enterprise.invitation.write")), 201);
    }
    private JsonNode submit(String email, String organization, String key) throws Exception {
        var content = new java.util.HashMap<String, Object>(); content.put("applicantEmail", email); content.put("applicantName", "Applicant");
        if (organization != null) content.put("organizationId", organization);
        return success(post(JOIN).contentType("application/json").content(json.writeValueAsString(content)).header("Idempotency-Key", key).with(applicant(email)), 201);
    }
    private JsonNode invitation(String id) throws Exception { return success(get(INV + "/" + id).with(admin("enterprise.invitation.read")), 200); }
    private JsonNode invitationAudit(String id) throws Exception { return success(get(INV + "/" + id + "/audit").with(admin("enterprise.invitation.read")), 200); }
    private JsonNode application(String id) throws Exception { return success(get(JOIN).param("search", id).with(admin("enterprise.join-application.read")), 200).path("items").get(0); }
    private JsonNode applicationAudit(String id) throws Exception { return success(get(JOIN + "/" + id + "/audit").with(admin("enterprise.join-application.read")), 200); }
    private JsonNode members() throws Exception { return success(get("/api/v1/enterprises/ent-acme/members").param("size", "200").with(admin("enterprise.members.read")), 200); }
    private JsonNode organizationMembers() throws Exception { return success(get("/api/v1/enterprises/ent-acme/org-units/org-engineering/members").with(jwt().jwt(token -> token.subject("alice").claim("scope", "enterprise.organization.read")
            .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
            )
            .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.organization.read"))), 200); }
    private JsonNode success(MockHttpServletRequestBuilder request, int status) throws Exception { return json.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString()).path("data"); }
    private MvcResult failure(MockHttpServletRequestBuilder request, int status, String code, String message) throws Exception {
        return mvc.perform(request.header("X-Request-Id", "invitation-failure")).andExpect(status().is(status))
                .andExpect(header().string("X-Request-Id", "invitation-failure")).andExpect(jsonPath("$.requestId").value("invitation-failure"))
                .andExpect(jsonPath("$.error.code").value(code)).andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.error.violations").isEmpty()).andExpect(jsonPath("$.error.cause").doesNotExist())
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist()).andReturn();
    }
    private static String key() { return UUID.randomUUID().toString(); }
    private static String quote(String revision) { return "\"" + revision + "\""; }
    private static RequestPostProcessor admin(String capability) { return adminFor("ent-acme", capability); }
    private static RequestPostProcessor adminFor(String enterprise, String capability) {
        return jwt().jwt(token -> token.subject("invitation-admin").claim("scope", capability).claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterprise, "status", "ACTIVE")))
                ).authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
    private static RequestPostProcessor applicant(String subject) { return jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("SCOPE_enterprise.join-application.submit")); }
    private static RequestPostProcessor invitee(String subject) { return jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept")); }
}
