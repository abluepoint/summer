package com.abluepoint.summer.sample.invitation;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InvitationControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired com.abluepoint.summer.sample.member.infrastructure.MemberDirectory directory;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void createsInvitationBoundToEnterpriseOrganizationAndCapabilityBundle() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/invitations", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\":\"new.user@example.test\",\"organizationId\":\"org-engineering\",\"roleIds\":[\"role-member\"],\"capabilityCodes\":[\"enterprise.members.read\"],\"expiresInSeconds\":3600}")
                        .header("Idempotency-Key", "invite-create-1")
                        .with(admin("ent-acme", "enterprise.invitation.write")))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.data.invitationId").isNotEmpty())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.organizationId").value("org-engineering"))
                .andExpect(jsonPath("$.data.capabilityCodes[0]").value("enterprise.members.read"))
                .andReturn();
        String invitationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.invitationId");
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}", "ent-acme", invitationId)
                        .with(admin("ent-acme", "enterprise.invitation.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.state").value("PENDING"));
    }

    @Test
    void listsInvitationsAndJoinApplicationsAsTenantBoundPages() throws Exception {
        createInvitation("invite-list-1", "list.user@example.test", "3600");
        submitApplication("join-list-1", "list.applicant@example.test");

        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/invitations", "ent-acme")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "20")
                        .with(admin("ent-acme", "enterprise.invitation.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].invitationId").isNotEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/join-applications", "ent-acme")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "20")
                        .queryParam("status", "PENDING")
                        .with(admin("ent-acme", "enterprise.join-application.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].applicationId").isNotEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void resendsAndRevokesAnInvitationWithOneTimeTokenRotation() throws Exception {
        MvcResult created = createInvitation("invite-resend-1", "new.user@example.test", "3600");
        String invitationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.invitationId");
        String originalToken = JsonPath.read(created.getResponse().getContentAsString(), "$.data.token");
        String etag = created.getResponse().getHeader("ETag");
        MvcResult resent = mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:resend", "ent-acme", invitationId)
                        .header("If-Match", etag).header("Idempotency-Key", "invite-resend-2")
                        .with(admin("ent-acme", "enterprise.invitation.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isNotEmpty()).andReturn();
        String rotatedToken = JsonPath.read(resent.getResponse().getContentAsString(), "$.data.token");
        org.junit.jupiter.api.Assertions.assertNotEquals(originalToken, rotatedToken);
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:resend", "ent-acme", invitationId)
                        .header("If-Match", etag).header("Idempotency-Key", "invite-resend-2")
                        .with(admin("ent-acme", "enterprise.invitation.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").value(rotatedToken));
        String rotatedEtag = resent.getResponse().getHeader("ETag");
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:revoke", "ent-acme", invitationId)
                        .header("If-Match", rotatedEtag).header("Idempotency-Key", "invite-revoke-1")
                        .with(admin("ent-acme", "enterprise.invitation.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("REVOKED"));
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", rotatedToken)
                        .header("Idempotency-Key", "invite-accept-revoked")
                        .with(jwt().jwt(token -> token.subject("new.user@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVITATION_UNUSABLE"));
    }

    @Test
    void acceptsAnInvitationOnceAndWritesAnAuditRecord() throws Exception {
        MvcResult created = createInvitation("invite-accept-1", "new.user@example.test", "3600");
        String invitationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.invitationId");
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enterpriseId\":\"ent-acme\"}")
                        .header("Idempotency-Key", "invite-accept-1")
                        .with(jwt().jwt(value -> value.subject("new.user@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.memberId").isNotEmpty());
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enterpriseId\":\"ent-acme\"}")
                        .header("Idempotency-Key", "invite-accept-1")
                        .with(jwt().jwt(value -> value.subject("new.user@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("ACCEPTED"));
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}/audit", "ent-acme", invitationId)
                        .with(admin("ent-acme", "enterprise.invitation.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("INVITATION_ACCEPTED"))
                .andExpect(jsonPath("$.data[0].requestId").isNotEmpty());
        mockMvc.perform(post("/api/v1/invitations/{token}:reject", token)
                        .header("Idempotency-Key", "invite-reject-after-accept")
                        .with(jwt().jwt(value -> value.subject("new.user@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVITATION_UNUSABLE"));
    }

    @Test
    void rejectsExpiredAndCrossEnterpriseInvitationUse() throws Exception {
        MvcResult expired = createInvitation("invite-expired-1", "expired@example.test", "-1");
        String expiredToken = JsonPath.read(expired.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", expiredToken)
                        .header("Idempotency-Key", "invite-expired-accept")
                        .with(jwt().jwt(value -> value.subject("expired@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVITATION_UNUSABLE"));
        MvcResult normal = createInvitation("invite-cross-enterprise-1", "new.user@example.test", "3600");
        String token = JsonPath.read(normal.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enterpriseId\":\"ent-other\"}")
                        .header("Idempotency-Key", "invite-cross-enterprise-accept")
                        .with(jwt().jwt(value -> value.subject("new.user@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVITATION_CONTEXT_MISMATCH"));
    }

    @Test
    void deniesInvitationCreationWithoutEnterpriseCapability() throws Exception {
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/invitations", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\":\"new.user@example.test\",\"organizationId\":\"org-engineering\",\"expiresInSeconds\":3600}")
                        .header("Idempotency-Key", "invite-unauthorized")
                        .with(admin("ent-acme", "enterprise.members.read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void submitsSupplementsAndApprovesJoinApplicationIdempotently() throws Exception {
        MvcResult submitted = submitApplication("join-submit-1", "joiner@example.test");
        String applicationId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.applicationId");
        String etag = submitted.getResponse().getHeader("ETag");
        MvcResult supplemented = mockMvc.perform(patch("/api/v1/enterprises/{enterpriseId}/join-applications/{id}", "ent-acme", applicationId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"materials\":[\"business-license.pdf\"]}")
                        .header("If-Match", etag).header("Idempotency-Key", "join-supplement-1")
                        .with(applicant("joiner@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.materials[0]").value("business-license.pdf")).andReturn();
        String reviewEtag = supplemented.getResponse().getHeader("ETag");
        MvcResult approved = mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve", "ent-acme", applicationId)
                        .header("If-Match", reviewEtag).header("Idempotency-Key", "join-approve-1")
                        .with(admin("ent-acme", "enterprise.join-application.review")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("APPROVED"))
                .andExpect(jsonPath("$.data.memberId").isNotEmpty()).andExpect(jsonPath("$.data.organizationId").value("org-engineering")).andReturn();
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve", "ent-acme", applicationId)
                        .header("If-Match", reviewEtag).header("Idempotency-Key", "join-approve-1")
                        .with(admin("ent-acme", "enterprise.join-application.review")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("APPROVED"))
                .andExpect(jsonPath("$.data.memberId").isNotEmpty());
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/join-applications/{id}/audit", "ent-acme", applicationId)
                        .with(admin("ent-acme", "enterprise.join-application.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].action").value("JOIN_APPLICATION_APPROVED"));
    }

    @Test
    void rejectsStaleJoinReviewAndAllowsApplicantWithdrawal() throws Exception {
        MvcResult submitted = submitApplication("join-withdraw-1", "withdraw@example.test");
        String applicationId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.applicationId");
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve", "ent-acme", applicationId)
                        .header("If-Match", "\"stale\"").header("Idempotency-Key", "join-stale-approve")
                        .with(admin("ent-acme", "enterprise.join-application.review")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"));
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:withdraw", "ent-acme", applicationId)
                        .header("If-Match", submitted.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "join-withdraw-1")
                        .with(applicant("withdraw@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("WITHDRAWN"));
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve", "ent-acme", applicationId)
                        .header("If-Match", submitted.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "join-approve-withdrawn")
                        .with(admin("ent-acme", "enterprise.join-application.review")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SUMMER_JOIN_APPLICATION_CONFLICT"));
    }

    @Test
    void applicantEndpointsStillRequireTheirScopeAndMatchingSubject() throws Exception {
        String body = "{\"applicantEmail\":\"owner@example.test\",\"applicantName\":\"Owner\",\"organizationId\":\"org-engineering\",\"capabilityCodes\":[]}";
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/join-applications")
                        .contentType(MediaType.APPLICATION_JSON).content(body).header("Idempotency-Key", "join-missing-scope")
                        .with(jwt().jwt(token -> token.subject("owner@example.test").claim("summer_identity_source", "local-user"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/join-applications")
                        .contentType(MediaType.APPLICATION_JSON).content(body).header("Idempotency-Key", "join-wrong-subject")
                        .with(applicant("other@example.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invitationConsumptionAllowsOnlyTheInvitedSubjectWithoutMembership() throws Exception {
        var created = createInvitation("invite-local-owner", "owner@example.test", "3600");
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", token)
                        .header("Idempotency-Key", "invite-local-wrong-owner").with(invitee("other@example.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", token)
                        .header("Idempotency-Key", "invite-local-correct-owner").with(invitee("owner@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("ACCEPTED"));
        var declined = createInvitation("invite-local-decline", "decline@example.test", "3600");
        String declinedToken = JsonPath.read(declined.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(post("/api/v1/invitations/{token}:reject", declinedToken)
                        .header("Idempotency-Key", "invite-local-reject").with(invitee("decline@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("REJECTED"));
    }

    @Test
    void applicantExceptionCannotChangeAnotherSubjectsApplicationOrReviewApplications() throws Exception {
        MvcResult submitted = submitApplication("join-owner-boundary", "owner@example.test");
        String id = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.applicationId");
        String etag = submitted.getResponse().getHeader("ETag");
        mockMvc.perform(patch("/api/v1/enterprises/ent-acme/join-applications/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"materials\":[\"unauthorized.pdf\"]}")
                        .header("If-Match", etag).header("Idempotency-Key", "join-wrong-subject-edit")
                        .with(applicant("other@example.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/join-applications/{id}:withdraw", id)
                        .header("If-Match", etag).header("Idempotency-Key", "join-wrong-subject-withdraw")
                        .with(applicant("other@example.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/join-applications/{id}:approve", id)
                        .header("If-Match", etag).header("Idempotency-Key", "join-applicant-review")
                        .with(jwt().jwt(token -> token.subject("owner@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.join-application.review"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/join-applications")
                        .with(jwt().jwt(token -> token.subject("owner@example.test"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.join-application.read"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/join-applications/{id}:withdraw", id)
                        .header("If-Match", etag).header("Idempotency-Key", "join-owner-withdraw")
                        .with(applicant("owner@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("WITHDRAWN"));
    }

    @Test
    void refusesCrossEnterpriseJoinApplicationReview() throws Exception {
        MvcResult submitted = submitApplication("join-cross-enterprise-1", "joiner@example.test");
        String applicationId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.applicationId");
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve", "ent-other", applicationId)
                        .header("If-Match", submitted.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", "join-cross-enterprise-approve")
                        .with(admin("ent-acme", "enterprise.join-application.review")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void acceptingWithVerifiedEmailPersistsTheOpaqueSubjectAndOrganizationAssignment() throws Exception {
        var created = createInvitation("opaque-invite", "opaque@example.test", "3600");
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.data.token");
        mockMvc.perform(post("/api/v1/invitations/{token}:accept", token).header("Idempotency-Key", "unverified")
                .with(jwt().jwt(j -> j.subject("issuer-subject-123").claim("email", "opaque@example.test").claim("email_verified", false))
                        .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isForbidden());
        var accepted = mockMvc.perform(post("/api/v1/invitations/{token}:accept", token).header("Idempotency-Key", "verified")
                .with(jwt().jwt(j -> j.subject("issuer-subject-123").claim("email", "opaque@example.test").claim("email_verified", true))
                        .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"))))
                .andExpect(status().isOk()).andReturn();
        String memberId = JsonPath.read(accepted.getResponse().getContentAsString(), "$.data.memberId");
        org.assertj.core.api.Assertions.assertThat(directory.forSubject("ent-acme", "issuer-subject-123").orElseThrow().memberId()).isEqualTo(memberId);
        org.assertj.core.api.Assertions.assertThat(directory.forSubject("ent-acme", "opaque@example.test")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from summer_organization_membership where enterprise_id='ent-acme' and member_id=? and organization_id='org-engineering'", Integer.class, memberId)).isEqualTo(1);
    }

    private MvcResult createInvitation(String key, String email, String expiresInSeconds) throws Exception {
        return mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/invitations", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\":\"" + email + "\",\"organizationId\":\"org-engineering\",\"roleIds\":[\"role-member\"],\"capabilityCodes\":[\"enterprise.members.read\"],\"expiresInSeconds\":" + expiresInSeconds + "}")
                        .header("Idempotency-Key", key).with(admin("ent-acme", "enterprise.invitation.write")))
                .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult submitApplication(String key, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/join-applications", "ent-acme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applicantEmail\":\"" + email + "\",\"applicantName\":\"Joiner\",\"organizationId\":\"org-engineering\",\"capabilityCodes\":[\"enterprise.members.read\"]}")
                        .header("Idempotency-Key", key).with(applicant(email)))
                .andExpect(status().isCreated()).andReturn();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String enterpriseId, String capability) {
        return jwt().jwt(token -> token.subject("enterprise-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterpriseId, "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of(enterpriseId, List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor applicant(String email) {
        return jwt().jwt(token -> token.subject(email).claim("summer_identity_source", "local-user"))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.join-application.submit"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor invitee(String email) {
        return jwt().jwt(token -> token.subject(email).claim("summer_identity_source", "local-user"))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.invitation.accept"));
    }
}
