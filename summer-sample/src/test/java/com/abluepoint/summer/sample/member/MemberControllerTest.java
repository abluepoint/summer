package com.abluepoint.summer.sample.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void listsMembersWithinTheExplicitEnterpriseContextWithPagingSearchAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/members", "ent-acme")
                        .queryParam("page", "0")
                        .queryParam("size", "2")
                        .queryParam("q", "alice")
                        .queryParam("status", "ACTIVE")
                        .with(jwt().jwt(token -> token
                                .subject("admin")
                                .claim("enterprise_memberships", java.util.List.of(
                                        java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].displayName").value("Alice Zhang"))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
    }

    @Test
    void refusesToReadMembersFromAnEnterpriseWhereThePrincipalIsNotAnActiveMember() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/members", "ent-other")
                        .with(jwt().jwt(token -> token
                                .subject("admin")
                                .claim("enterprise_memberships", java.util.List.of(
                                        java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void readsMemberDetailsAndUsesEtagForConditionalWrites() throws Exception {
        var authenticated = jwt().jwt(token -> token
                        .subject("admin")
                        .claim("enterprise_memberships", java.util.List.of(
                                java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")
                        )))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"));
        var detail = mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/members/{memberId}", "ent-acme", "member-alice")
                        .with(authenticated))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.data.memberId").value("member-alice"))
                .andReturn();
        String etag = detail.getResponse().getHeader("ETag");

        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/members/{memberId}:disable", "ent-acme", "member-alice")
                        .header("If-Match", etag)
                        .header("Idempotency-Key", "disable-alice-1")
                        .with(jwt().jwt(token -> token
                                .subject("admin")
                                .claim("enterprise_memberships", java.util.List.of(
                                        java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.write"))))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", org.hamcrest.Matchers.not(etag)))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void rejectsAStaleEtagForMemberStateChanges() throws Exception {
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/members/{memberId}:enable", "ent-acme", "member-alice")
                        .header("If-Match", "\"stale\"")
                        .header("Idempotency-Key", "enable-alice-stale")
                        .with(jwt().jwt(token -> token
                                .subject("admin")
                                .claim("enterprise_memberships", java.util.List.of(
                                        java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.write"))))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PRECONDITION_FAILED"));
    }

    @Test
    void replaysAnIdempotentMemberMutationWithoutApplyingItTwice() throws Exception {
        String etag = mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/members/{memberId}", "ent-acme", "member-alice")
                        .with(jwt().jwt(token -> token.subject("admin").claim("enterprise_memberships", java.util.List.of(
                                java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"))))
                .andReturn().getResponse().getHeader("ETag");
        java.util.function.Supplier<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> request = () -> post("/api/v1/enterprises/{enterpriseId}/members/{memberId}:disable", "ent-acme", "member-alice")
                .header("If-Match", etag)
                .header("Idempotency-Key", "disable-alice-replay")
                .with(jwt().jwt(token -> token.subject("admin").claim("enterprise_memberships", java.util.List.of(
                        java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.write")));

        var first = mockMvc.perform(request.get()).andExpect(status().isOk()).andReturn();
        mockMvc.perform(request.get())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(header().string("ETag", first.getResponse().getHeader("ETag")));
    }

    @Test
    void rejectsReusingAnIdempotencyKeyForAnotherMutation() throws Exception {
        var writeAuth = jwt().jwt(token -> token.subject("admin").claim("enterprise_memberships", java.util.List.of(
                        java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.write"));
        var readAuth = jwt().jwt(token -> token.subject("admin").claim("enterprise_memberships", java.util.List.of(
                        java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"));
        String etag = mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/members/{memberId}", "ent-acme", "member-alice")
                        .with(readAuth))
                .andReturn().getResponse().getHeader("ETag");
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/members/{memberId}:disable", "ent-acme", "member-alice")
                        .header("If-Match", etag).header("Idempotency-Key", "same-key").with(writeAuth))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/members/{memberId}:enable", "ent-acme", "member-alice")
                        .header("If-Match", etag).header("Idempotency-Key", "same-key").with(writeAuth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void supportsLeaveAndExposesAnAuditRecordWithRequestCorrelation() throws Exception {
        String etag = mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/members/{memberId}", "ent-acme", "member-alice")
                        .with(jwt().jwt(token -> token.subject("admin").claim("enterprise_memberships", java.util.List.of(
                                java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"))))
                .andReturn().getResponse().getHeader("ETag");
        mockMvc.perform(post("/api/v1/enterprises/{enterpriseId}/members/{memberId}:resign", "ent-acme", "member-alice")
                        .header("If-Match", etag).header("Idempotency-Key", "leave-alice-1")
                        .with(jwt().jwt(token -> token.subject("admin").claim("enterprise_memberships", java.util.List.of(
                                java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.write"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESIGNED"));
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/members/{memberId}/audit", "ent-acme", "member-alice")
                        .with(jwt().jwt(token -> token.subject("admin").claim("enterprise_memberships", java.util.List.of(
                                java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.members.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].actorSubject").value("admin"))
                .andExpect(jsonPath("$.data[0].enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data[0].memberId").value("member-alice"))
                .andExpect(jsonPath("$.data[0].afterStatus").value("RESIGNED"))
                .andExpect(jsonPath("$.data[0].requestId").isNotEmpty());
    }
}
