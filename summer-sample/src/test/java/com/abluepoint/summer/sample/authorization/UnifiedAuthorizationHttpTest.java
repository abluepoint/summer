package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import({UnifiedAuthorizationHttpTest.PolicyFixture.class, ExplicitLegacyBusinessDomains.AcmeAndPlatform.class})
class UnifiedAuthorizationHttpTest {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class PolicyFixture {
        @org.springframework.context.annotation.Bean java.util.concurrent.atomic.AtomicInteger replacementPolicyCalls() {
            return new java.util.concurrent.atomic.AtomicInteger();
        }
        @org.springframework.context.annotation.Bean AuthorizationPolicy replacementPolicy(AuthorizationService legacy,
                java.util.concurrent.atomic.AtomicInteger replacementPolicyCalls) {
            return new AuthorizationPolicy() {
                public String mode() { return "FIXTURE"; }
                public com.abluepoint.summer.security.authorization.ResourceAuthorization.Decision decide(
                        com.abluepoint.summer.security.authorization.ResourceAuthorization.Request request, long revision) {
                    replacementPolicyCalls.incrementAndGet();
                    legacy.decision(request.principal(), request.domain().id(), "customer.read", request.requestId());
                    return new com.abluepoint.summer.security.authorization.ResourceAuthorization.Decision(false,
                            "FIXTURE_DENY", revision, request.domain(), request.resource());
                }
            };
        }
    }
    @Autowired MockMvc mvc;
    @Autowired AuthorizationService authorization;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired java.util.concurrent.atomic.AtomicInteger replacementPolicyCalls;

    @Test
    void legacyHttpDecisionCarriesStableResourceAndAuthoritativeRevision() throws Exception {
        var token = Jwt.withTokenValue("test").header("alg", "none").subject("foundation-reader")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.read"))).build();
        var principal = new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("SCOPE_customer.read"),
                new SimpleGrantedAuthority("SCOPE_enterprise.roles.read")));
        mvc.perform(get("/api/customers/foundation-missing").with(authentication(principal)))
                .andExpect(status().isNotFound());
        assertThat(authorization.audit("ent-acme", "foundation-reader", principal))
                .anySatisfy(row -> {
                    assertThat(row).containsEntry("decision", "ALLOW");
                    assertThat(row.get("resourceId")).isNotNull();
                    assertThat(row.get("authorizationRevision")).isInstanceOf(Number.class);
                });
    }
    @Test
    void unconfirmedModeNeverSelectsARegisteredPolicyOrRequestFallback() throws Exception {
        var before = jdbc.queryForMap("select engine_mode,revision from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'");
        int calls = replacementPolicyCalls.get();
        jdbc.update("insert into summer_authorization_domain(domain_key, engine_mode, revision) values ('ENTERPRISE:ent-acme', 'FIXTURE', 41) on duplicate key update engine_mode='FIXTURE', revision=41");
        var token = Jwt.withTokenValue("test").header("alg", "none").subject("replacement-reader")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.read"))).build();
        var principal = new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("SCOPE_customer.read"),
                new SimpleGrantedAuthority("SCOPE_enterprise.roles.read")));
        try {
            mvc.perform(get("/api/customers/foundation-missing").param("authorizationEngine", "LEGACY")
                    .with(authentication(principal))).andExpect(status().isForbidden());
            assertThat(replacementPolicyCalls.get()).isEqualTo(calls);
            assertThat(jdbc.queryForObject("select engine_mode from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'", String.class))
                    .isEqualTo("FIXTURE");
        } finally {
            jdbc.update("update summer_authorization_domain set engine_mode=?,revision=? where domain_key='ENTERPRISE:ent-acme'",
                    before.get("engine_mode"), before.get("revision"));
        }
    }

    @Test
    void unavailableAuthoritativeStoreReturnsDependencyFailureBeforeBusinessHandler() throws Exception {
        var token = Jwt.withTokenValue("test").header("alg", "none").subject("store-failure-reader")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).build();
        var principal = new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("SCOPE_customer.read")));
        jdbc.execute("alter table summer_authorization_domain rename to summer_authorization_domain_unavailable");
        try {
            mvc.perform(get("/api/customers/foundation-missing").with(authentication(principal)))
                    .andExpect(status().isServiceUnavailable());
        } finally { jdbc.execute("alter table summer_authorization_domain_unavailable rename to summer_authorization_domain"); }
    }

}
