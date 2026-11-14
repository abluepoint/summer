package com.abluepoint.summer.sample.identity;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:local-console-claims;MODE=MySQL;DB_CLOSE_DELAY=-1")
@Transactional
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class LocalConsoleIdentityClaimsTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.member.MemberDirectory members;
    @Autowired AuthUserService users;
    @Autowired LocalConsoleIdentityClaims identities;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @Autowired com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess access;
    String username, role, resource;
    private final com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain =
            com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
    private static final String CODE = "enterprise.data-scope.read";
    private static final Map<String, Object> EMPTY = Map.of("enterprise_memberships", List.of(),
            "enterprise_capabilities", Map.of(), "platform_capabilities", List.of());

    @BeforeEach void seed() {
        username="claims-"+UUID.randomUUID();
        users.create(username,UUID.randomUUID().toString(),"USER");
        members.provision("ent-acme", "member-" + username, username, username, "claims@example.test");
        jdbc.update("insert into summer_organization_membership (enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at) values ('ent-acme',?,'org-engineering',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "member-" + username);
        entityManager.flush();
        role = current.grantHandlers(domain, username, (handler, path, method) -> handler.getBeanType().getName().equals(
                "com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController")
                && path.equals("/api/v1/enterprises/{enterpriseId}/roles") && method.equals("GET"));
        resource = jdbc.queryForObject("select resource_id from summer_permission_resource where domain_key=? and permission_id=?",
                String.class, domain.key(), role);
        assertThat(allowed()).isTrue();
    }

    @Test void currentIdentityAndSelectedOrganizationBecomeClaimsWithoutBusinessGrants() {
        jdbc.update("insert into summer_organization_context (subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,'ent-acme','org-engineering','1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",username);
        var claims=identities.resolve(username,EMPTY);
        assertThat(LocalConsoleIdentityClaims.capabilities(claims)).isEmpty();
        assertThat((Collection<?>) claims.get("enterprise_memberships")).hasSize(1);
        assertThat(allowed()).isTrue();
        assertThat(claims.get("organization_id")).isEqualTo("org-engineering");
        assertThat(claims.get("release_operator")).isEqualTo(false);
        assertThat(claims).doesNotContainKey("scope");
    }
    @Test void oldBusinessClaimsCannotReplaceCurrentRoleFacts() {
        var poisoned = Map.<String, Object>of("enterprise_capabilities", Map.of("ent-acme", List.of(CODE)),
                "platform_capabilities", List.of("platform.capabilities.write"), "denied_capability_codes", List.of(CODE));
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username, poisoned))).isEmpty();
        assertThat(allowed()).isTrue();
        current.revoke(domain, username, role);
        assertThat(allowed()).isFalse();
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username, poisoned))).isEmpty();
    }
    @Test void disabledRoleCannotGrantAnAction() {
        jdbc.update("update summer_authorization_role set status='DISABLED' where role_id=?",role);
        assertThat(allowed()).isFalse();
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username,EMPTY))).isEmpty();
    }
    @Test void disabledCurrentPermissionCannotGrantAnAction() {
        jdbc.update("update summer_permission set status='DISABLED' where domain_key=? and permission_id=?", domain.key(), role);
        assertThat(allowed()).isFalse();
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username,EMPTY))).isEmpty();
    }
    @Test void removedManagedBindingCannotReuseThePriorTokenClaims() {
        var prior=identities.resolve(username,EMPTY);
        current.revoke(domain, username, role);
        assertThat(allowed()).isFalse();
        var next=identities.resolve(username,prior);
        assertThat(LocalConsoleIdentityClaims.capabilities(next)).isEmpty();
        assertThat((Collection<?>)next.get("enterprise_memberships")).hasSize(1);
    }
    @Test void disabledMemberLosesTenantAndOrganizationClaimsEvenWithRoleBindings() {
        jdbc.update("insert into summer_organization_context (subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,'ent-acme','org-engineering','1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",username);
        var prior = identities.resolve(username, EMPTY);
        jdbc.update("update summer_enterprise_member set status='DISABLED' where subject=?", username);
        assertThat(allowed()).isFalse();
        var next = identities.resolve(username, prior);
        assertThat((Collection<?>) next.get("enterprise_memberships")).isEmpty();
        assertThat(LocalConsoleIdentityClaims.capabilities(next)).isEmpty();
        assertThat(next).doesNotContainKeys("organization_id", "organization_context", "active_enterprise_id");
    }
    @Test void roleBindingsWithoutMembershipFailClosed() {
        jdbc.update("delete from summer_enterprise_member where subject=?", username);
        assertThat(allowed()).isFalse();
        assertThat((Collection<?>) identities.resolve(username, EMPTY).get("enterprise_memberships")).isEmpty();
    }
    @Test void removedAssignmentCannotReissueThePriorOrganizationContext() {
        jdbc.update("insert into summer_organization_context (subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,'ent-acme','org-engineering','1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",username);
        var prior = identities.resolve(username, EMPTY);
        jdbc.update("delete from summer_organization_membership where member_id=?", "member-" + username);
        assertThat(identities.resolve(username, prior)).doesNotContainKeys("organization_id", "organization_context");
    }
    @Test void disabledLocalUserCannotReceiveNewClaims() {
        users.setActive(username,false);
        entityManager.flush();
        assertThatThrownBy(() -> identities.resolve(username,EMPTY)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    private boolean allowed() {
        return access.allows(domain, org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                username, "unused", List.of()), "INTERFACE", resource);
    }
}
