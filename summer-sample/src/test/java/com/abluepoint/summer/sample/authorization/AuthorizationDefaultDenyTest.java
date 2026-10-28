package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.application.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.application.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationCapabilityRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationRoleRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationUserBindingEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationUserBindingRepository;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationDefaultDenyTest {
    private final AuthorizationUserBindingRepository bindings = mock(AuthorizationUserBindingRepository.class);
    private final org.springframework.jdbc.core.JdbcTemplate membershipJdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    private final AuthorizationService service = new AuthorizationService(
            bindings, mock(AuthorizationCapabilityRepository.class),
            mock(AuthorizationRoleRepository.class), mock(AuthorizationChangeEventService.class), mock(AuthorizationAuditStore.class), membershipJdbc);

    @org.junit.jupiter.api.BeforeEach void persistedMembershipFixture() {
        org.mockito.Mockito.when(membershipJdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "bootstrapUsername", "unassigned");
    }

    @Test
    void requestedOAuthScopesDoNotGrantUnassignedLocalUsersBusinessAccess() {
        var user = token(Map.of("summer_identity_source", "local-user"));
        assertThat(service.decision(user, "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
        assertThat(service.decision(user, "ent-acme", "POST:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void unassignedExternalPrincipalsAreDeniedByDefault() {
        assertThat(service.decision(token(Map.of()), "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void managedPrincipalCannotFallBackAfterItsBindingIsRemoved() {
        assertThat(service.decision(token(Map.of("summer_managed_grants", true)),
                "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void bootstrapAdministratorCanConfigureRolesButCannotReadBusinessData() {
        var admin = token(Map.of("summer_identity_source", "local-user",
                "enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.write")),
                "platform_capabilities", List.of("platform.capabilities.write")));
        assertThat(service.decision(admin, "ent-acme", "POST:/api/v1/enterprises/{enterpriseId}/roles", "review").allowed()).isTrue();
        assertThat(service.decision(admin, "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void bootstrapCannotBeUsedByAnOrdinaryLocalUserOrForAnotherTenant() {
        var user = token(Map.of("summer_identity_source", "local-user"));
        assertThat(service.decision(user, "ent-acme", "POST:/api/v1/enterprises/{enterpriseId}/roles", "review").allowed()).isFalse();
        var admin = token(Map.of("summer_identity_source", "local-user",
                "enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.write")),
                "platform_capabilities", List.of("platform.capabilities.write")));
        assertThat(service.decision(admin, "ent-other", "POST:/api/v1/enterprises/{enterpriseId}/roles", "review").allowed()).isFalse();
    }

    @Test
    void persistedAssignmentsCannotGrantAccessWithoutAnActiveMembership() {
        when(bindings.findAllByOrderByEnterpriseIdAscUsernameAsc()).thenReturn(List.of(
                new AuthorizationUserBindingEntity("binding-test", "ent-acme", "unassigned", "[]", "[]", 1)));
        var missingMembership = token(Map.of("enterprise_memberships", List.of()));
        assertThat(service.decision(missingMembership, "ent-acme", "GET:/api/customers", "review"))
                .isEqualTo(new AuthorizationService.Decision(false, "INACTIVE_MEMBERSHIP"));
        var inactiveMembership = token(Map.of("enterprise_memberships",
                List.of(Map.of("enterpriseId", "ent-acme", "status", "SUSPENDED"))));
        assertThat(service.decision(inactiveMembership, "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void explicitLegacyCompatibilityNeverAppliesToLocalOrManagedUsers() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "legacyScopeCompatibility", true);
        assertThat(service.decision(token(Map.of()), "ent-acme", "GET:/api/customers", "review"))
                .isEqualTo(new AuthorizationService.Decision(true, "LEGACY_SCOPE"));
        assertThat(service.decision(token(Map.of("summer_identity_source", "local-user")),
                "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
        assertThat(service.decision(token(Map.of("summer_managed_grants", true)),
                "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    private static JwtAuthenticationToken token(Map<String, Object> claims) {
        Instant now = Instant.now();
        var jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject("unassigned")
                .issuedAt(now).expiresAt(now.plusSeconds(60))
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claims(values -> values.putAll(claims)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_customer.read"),
                new SimpleGrantedAuthority("SCOPE_customer.write")));
    }
}
