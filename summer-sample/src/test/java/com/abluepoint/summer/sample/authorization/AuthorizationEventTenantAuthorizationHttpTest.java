package com.abluepoint.summer.sample.authorization;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real persisted authorization and controller dispatch; synthetic JWT, H2 fixtures, no mocked decision. */
@SpringBootTest(properties = "summer.sample.authorization.legacy-scope-compatibility=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthorizationEventTenantAuthorizationHttpTest {
    private static final String A = "ent-acme", READ = "enterprise.roles.read", WRITE = "enterprise.roles.write";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
    @Autowired AuthorizationChangeEventService events;
    String b, subject, eventA, eventB;
    RequestPostProcessor oldJwt;

    @BeforeEach void fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        b = "ent-event-" + suffix; subject = "event-reader-" + suffix;
        jdbc.update("insert into summer_enterprise(enterprise_id,name,code,status,review_status,version,created_at,updated_at) values(?,?,?,'ACTIVE','PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", b, "Event tenant", b);
        for (String tenant : List.of(A, b)) {
            members.provision(tenant, "member-" + tenant + suffix, subject, "Event fixture", "event@example.test");
            String role = "role-" + tenant + suffix;
            String read = tenant.equals(A) ? "event-fixture-A-read" : "read-" + tenant + suffix;
            String write = tenant.equals(A) ? "event-fixture-A-write" : "write-" + tenant + suffix;
            for (String code : List.of(READ, WRITE)) {
                if (jdbc.queryForObject("select count(*) from summer_authorization_capability where capability_id=?", Integer.class, code.equals(READ) ? read : write) == 0)
                    jdbc.update("insert into summer_authorization_capability(capability_id,enterprise_id,scope,code,resource,action,risk_level,status,version,created_at,updated_at) values (?,?,'ENTERPRISE',?,?,'event','LOW','ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", code.equals(READ) ? read : write, tenant, code, code);
            }
            jdbc.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,capability_ids,capability_codes,version,created_at,updated_at) values (?,?,?, ?,false,'ACTIVE',?, ?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", role, tenant, "Event fixture", role, read + "\n" + write, READ + "\n" + WRITE);
            jdbc.update("insert into summer_authorization_user_binding(binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) values (?,?,?,?,'[]',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "binding-" + tenant + suffix, tenant, subject, "[\"" + role + "\"]");
            events.enqueue(tenant, "ROLE", subject, "ROLE_CREATED", 1, "fixture-" + suffix);
        }
        eventA = eventId(A); eventB = eventId(b);
        deadLetter(eventA); deadLetter(eventB);
        oldJwt = token(List.of(READ, WRITE), List.of(READ, WRITE));
    }

    @Test void sameJwtLosesBReadImmediatelyWhileAStaysAllowed() throws Exception {
        mvc.perform(list(b).with(oldJwt)).andExpect(status().isOk());
        deny(b, READ);
        forbidden(list(b).with(oldJwt));
        mvc.perform(list(A).with(oldJwt)).andExpect(status().isOk());
    }

    @Test void sameJwtLosesBRetryImmediatelyWithoutChangingOutbox() throws Exception {
        mvc.perform(retry(b, eventB).with(oldJwt)).andExpect(status().isOk());
        deadLetter(eventB);
        Map<String, Object> before = outbox(eventB);
        deny(b, WRITE);
        forbidden(retry(b, eventB).with(oldJwt));
        assertThat(outbox(eventB)).isEqualTo(before);
        mvc.perform(retry(A, eventA).with(oldJwt)).andExpect(status().isOk());
    }

    @Test void aDenyCannotBlockLegitimateBRead() throws Exception {
        deny(A, READ);
        forbidden(list(A).with(oldJwt));
        mvc.perform(list(b).with(oldJwt).header("X-Request-Id", "event-B-read"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", "event-B-read"))
                .andExpect(jsonPath("$.requestId").value("event-B-read"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].enterpriseId").value(b));
    }

    @Test void aDenyCannotBlockLegitimateBRetry() throws Exception {
        deny(A, WRITE);
        Map<String, Object> before = outbox(eventA);
        forbidden(retry(A, eventA).with(oldJwt));
        assertThat(outbox(eventA)).isEqualTo(before);
        mvc.perform(retry(b, eventB).with(oldJwt).header("X-Request-Id", "event-B-retry"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", "event-B-retry"))
                .andExpect(jsonPath("$.requestId").value("event-B-retry"))
                .andExpect(jsonPath("$.data.enterpriseId").value(b))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test void foreignEventIdCannotBeRetriedThroughB() throws Exception {
        Map<String, Object> before = outbox(eventA);
        mvc.perform(retry(b, eventA).with(oldJwt).header("X-Request-Id", "foreign-event"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.requestId").value("foreign-event"));
        assertThat(outbox(eventA)).isEqualTo(before);
        mvc.perform(list(b).with(oldJwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].eventId").value(eventB));
    }

    @Test void persistedMemberRevocationStillRejectsReadAndRetry() throws Exception {
        jdbc.update("update summer_enterprise_member set status='DISABLED' where enterprise_id=? and subject=?", b, subject);
        forbidden(list(b).with(oldJwt)); forbidden(retry(b, eventB).with(oldJwt));
    }

    @Test void missingBClaimsCannotBeReplacedByPersistedPermission() throws Exception {
        var missing = token(List.of(), List.of(READ, WRITE));
        forbidden(list(b).with(missing)); forbidden(retry(b, eventB).with(missing));
    }

    @Test void missingScopesCannotBeReplacedByPersistedPermission() throws Exception {
        var missingRead = token(List.of(READ, WRITE), List.of(WRITE));
        var missingWrite = token(List.of(READ, WRITE), List.of(READ));
        forbidden(list(b).with(missingRead)); forbidden(retry(b, eventB).with(missingWrite));
    }

    @Test void omittedOrEmptyEnterpriseKeepsExistingDefaultAndMalformedSelectionFailsClosed() throws Exception {
        mvc.perform(get("/api/authorization-events").with(oldJwt)).andExpect(status().isOk());
        mvc.perform(list("").with(oldJwt)).andExpect(status().isOk());
        Map<String, Object> before = outbox(eventB);
        forbidden(list("bad/tenant").with(oldJwt));
        forbidden(retry("bad/tenant", eventB).with(oldJwt));
        forbidden(post("/api/authorization-events/" + eventB + "/retry").param("enterpriseId", A, b).with(oldJwt));
        assertThat(outbox(eventB)).isEqualTo(before);
        forbidden(get("/api/authorization-events").param("enterpriseId", A, b).with(oldJwt));
    }

    @Test void removingBRoleAlsoRevokesTheSameJwtWithoutAffectingA() throws Exception {
        jdbc.update("update summer_authorization_user_binding set role_ids='[]' where enterprise_id=? and username=?", b, subject);
        forbidden(list(b).with(oldJwt)); forbidden(retry(b, eventB).with(oldJwt));
        mvc.perform(list(A).with(oldJwt)).andExpect(status().isOk());
    }

    @Test void anotherGlobalControllerIgnoresEnterpriseQueryAndKeepsSampleBoundary() throws Exception {
        var service = org.mockito.Mockito.mock(AuthorizationService.class);
        var interceptor = new AuthorizationRequestInterceptor(service, () -> "global-event-control");
        var auth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("synthetic").header("alg", "none").subject(subject).build());
        org.mockito.Mockito.when(service.decision(auth, A, READ, "global-event-control"))
                .thenReturn(new AuthorizationService.Decision(true, "ROLE_ALLOW"));
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/other-global");
        request.addParameter("enterpriseId", b);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            assertThat(interceptor.preHandle(request, new org.springframework.mock.web.MockHttpServletResponse(),
                    new org.springframework.web.method.HandlerMethod(new OtherGlobalController(), OtherGlobalController.class.getDeclaredMethod("read")))).isTrue();
            org.mockito.Mockito.verify(service).decision(auth, A, READ, "global-event-control");
            org.mockito.Mockito.verifyNoMoreInteractions(service);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    static class OtherGlobalController {
        @com.abluepoint.summer.sample.resource.api.ResourcePermission(READ) public void read() {}
    }

    private RequestPostProcessor token(List<String> bCapabilities, List<String> scopes) {
        return jwt().jwt(j -> j.subject(subject).claim("summer_managed_grants", true)
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", A, "status", "ACTIVE"), Map.of("enterpriseId", b, "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of(A, List.of(READ, WRITE), b, bCapabilities)))
                .authorities(scopes.stream().map(c -> new SimpleGrantedAuthority("SCOPE_" + c)).toArray(SimpleGrantedAuthority[]::new));
    }
    private MockHttpServletRequestBuilder list(String tenant) { return get("/api/authorization-events").param("enterpriseId", tenant); }
    private MockHttpServletRequestBuilder retry(String tenant, String event) { return post("/api/authorization-events/" + event + "/retry").param("enterpriseId", tenant); }
    private void forbidden(MockHttpServletRequestBuilder request) throws Exception {
        String requestId = "event-denied-" + UUID.randomUUID();
        mvc.perform(request.header("X-Request-Id", requestId))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(header().string("X-Request-Id", requestId)).andExpect(jsonPath("$.requestId").value(requestId));
    }
    private void deny(String tenant, String permission) {
        jdbc.update("update summer_authorization_user_binding set denied_capability_codes=? where enterprise_id=? and username=?", "[\"" + permission + "\"]", tenant, subject);
    }
    private String eventId(String tenant) {
        return jdbc.queryForObject("select event_id from summer_authorization_change_outbox where enterprise_id=? and resource_id=?", String.class, tenant, subject);
    }
    private void deadLetter(String event) {
        jdbc.update("update summer_authorization_change_outbox set status='DEAD_LETTER',attempts=3,last_error='fixture-delivery-failed' where event_id=?", event);
    }
    private Map<String, Object> outbox(String event) {
        return jdbc.queryForMap("select status,attempts,last_error,next_attempt_at,updated_at from summer_authorization_change_outbox where event_id=?", event);
    }
}
