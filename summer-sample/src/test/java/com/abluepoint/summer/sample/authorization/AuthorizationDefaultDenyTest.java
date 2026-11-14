package com.abluepoint.summer.sample.authorization;

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

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationDefaultDenyTest {
    private final AuthorizationUserBindingRepository bindings = mock(AuthorizationUserBindingRepository.class);
    private final org.springframework.jdbc.core.JdbcTemplate membershipJdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    private final AuthorizationService service = new AuthorizationService(
            bindings, mock(AuthorizationCapabilityRepository.class),
            mock(AuthorizationRoleRepository.class), mock(AuthorizationChangeEventService.class), mock(AuthorizationAuditStore.class), membershipJdbc);

    @org.springframework.beans.factory.annotation.Autowired CurrentManagedAuthorizationTestFixture managed;
    @org.springframework.beans.factory.annotation.Autowired com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess current;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @org.springframework.beans.factory.annotation.Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap bootstrap;

    private static final com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain DOMAIN =
            com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
    private String grantRead() {
        return managed.grantHandlers(DOMAIN, "unassigned", (handler, path, method) ->
                method.equals("GET") && path.equals("/api/v1/enterprises/{enterpriseId}/agents"));
    }
    private String resource(String permission) {
        return db.queryForObject("select resource_id from summer_permission_resource where domain_key=? and permission_id=?", String.class, DOMAIN.key(), permission);
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
        String role = grantRead();
        String resource = resource(role);
        assertThat(current.allows(DOMAIN, token(Map.of()), "INTERFACE", resource)).isTrue();
        managed.revoke(DOMAIN, "unassigned", role);
        assertThat(current.allows(DOMAIN, token(Map.of()), "INTERFACE", resource)).isFalse();
        assertThat(service.decision(token(Map.of("summer_managed_grants", true)),
                "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void bootstrapAdministratorCanConfigureRolesButCannotReadBusinessData() {
        var admin = token(Map.of("summer_identity_source", "local-user",
                "enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.write")),
                "platform_capabilities", List.of("platform.capabilities.write")));
        String role = managed.grantHandlers(DOMAIN, "unassigned", (handler,path,method) -> path.equals("/api/v1/enterprises/{enterpriseId}/roles") && method.equals("POST"));
        var resources = db.queryForList("select resource_id from summer_permission_resource where domain_key=? and permission_id=?", String.class, DOMAIN.key(), role);
        assertThat(resources).isNotEmpty().allSatisfy(id -> assertThat(current.roleAllows(DOMAIN, admin, id)).isTrue());
        // The retired facade never turns bootstrap or claim data into authority.
        assertThat(service.decision(admin, "ent-acme", "POST:/api/v1/enterprises/{enterpriseId}/roles", "review").allowed()).isFalse();
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
        String resource = resource(grantRead());
        assertThat(current.allows(DOMAIN, token(Map.of()), "INTERFACE", resource)).isTrue();
        db.update("update summer_enterprise_member set status='DISABLED' where enterprise_id=? and subject=?", DOMAIN.id(), "unassigned");
        assertThat(current.allows(DOMAIN, token(Map.of()), "INTERFACE", resource)).isFalse();
        var missingMembership = token(Map.of("enterprise_memberships", List.of()));
        assertThat(service.decision(missingMembership, "ent-acme", "GET:/api/customers", "review"))
                .isEqualTo(new AuthorizationService.Decision(false, "CONTROLLED_MANAGED_UPGRADE_REQUIRED"));
        var inactiveMembership = token(Map.of("enterprise_memberships",
                List.of(Map.of("enterpriseId", "ent-acme", "status", "SUSPENDED"))));
        assertThat(service.decision(inactiveMembership, "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void explicitLegacyCompatibilityNeverAppliesToLocalOrManagedUsers() {
        assertThat(service.decision(token(Map.of()), "ent-acme", "GET:/api/customers", "review"))
                .isEqualTo(new AuthorizationService.Decision(false, "CONTROLLED_MANAGED_UPGRADE_REQUIRED"));
        assertThat(service.decision(token(Map.of("summer_identity_source", "local-user")),
                "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
        assertThat(service.decision(token(Map.of("summer_managed_grants", true)),
                "ent-acme", "GET:/api/customers", "review").allowed()).isFalse();
    }

    @Test
    void ordinaryStartupNeverRepairsMissingModeAndExplicitFixtureRejectsLegacyMode() {
        db.update("update summer_authorization_domain set engine_mode='LEGACY' where domain_key=?", DOMAIN.key());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> managed.install(DOMAIN))
                .isInstanceOf(IllegalStateException.class).hasMessage("Fixture cannot convert an existing legacy domain");
        assertThat(db.queryForObject("select engine_mode from summer_authorization_domain where domain_key=?",String.class,DOMAIN.key())).isEqualTo("LEGACY");
        db.update("delete from summer_authorization_domain where domain_key=?",DOMAIN.key());
        bootstrap.initialize();
        assertThat(db.queryForObject("select count(*) from summer_authorization_domain where domain_key=?",Long.class,DOMAIN.key())).isZero();
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
