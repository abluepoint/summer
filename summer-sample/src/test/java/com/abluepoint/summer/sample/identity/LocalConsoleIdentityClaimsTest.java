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
class LocalConsoleIdentityClaimsTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired com.abluepoint.summer.sample.member.infrastructure.MemberDirectory members;
    @Autowired AuthUserService users;
    @Autowired LocalConsoleIdentityClaims identities;
    @Autowired jakarta.persistence.EntityManager entityManager;
    String username, role, capability;
    private static final String CODE = "enterprise.data-scope.read";
    private static final Map<String, Object> EMPTY = Map.of("enterprise_memberships", List.of(),
            "enterprise_capabilities", Map.of(), "platform_capabilities", List.of());

    @BeforeEach void seed() {
        username="claims-"+UUID.randomUUID();role="role-"+UUID.randomUUID();capability="cap-"+UUID.randomUUID();
        users.create(username,UUID.randomUUID().toString(),"USER");
        members.provision("ent-acme", "member-" + username, username, username, "claims@example.test");
        jdbc.update("insert into summer_organization_membership (enterprise_id,member_id,organization_id,primary_organization,created_at,updated_at) values ('ent-acme',?,'org-engineering',true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", "member-" + username);
        jdbc.update("insert into summer_authorization_capability (capability_id,enterprise_id,scope,code,resource,action,risk_level,status,version,created_at,updated_at) values (?, 'ent-acme','ENTERPRISE',?,'data-scopes','read','LOW','ACTIVE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",capability,CODE);
        jdbc.update("insert into summer_authorization_role (role_id,enterprise_id,name,code,built_in,status,capability_ids,capability_codes,version,created_at,updated_at) values (?,'ent-acme','fixture',?,false,'ACTIVE',?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",role,role,capability,CODE);
        jdbc.update("insert into summer_authorization_user_binding (binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) values (?,'ent-acme',?,?,'',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",UUID.randomUUID().toString(),username,role);
    }

    @Test void persistedRoleAndSelectedOrganizationBecomeClaims() {
        jdbc.update("insert into summer_organization_context (subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,'ent-acme','org-engineering','1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",username);
        var claims=identities.resolve(username,EMPTY);
        assertThat(LocalConsoleIdentityClaims.capabilities(claims)).containsExactly(CODE);
        assertThat(claims.get("organization_id")).isEqualTo("org-engineering");
        assertThat(claims.get("release_operator")).isEqualTo(false);
        assertThat(claims).doesNotContainKey("scope");
    }
    @Test void explicitDenyOverridesTheRoleGrant() {
        jdbc.update("update summer_authorization_user_binding set denied_capability_codes = ? where username = ?",CODE,username);
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username,EMPTY))).isEmpty();
    }
    @Test void disabledRoleCannotGrantAnAction() {
        jdbc.update("update summer_authorization_role set status='DISABLED' where role_id=?",role);
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username,EMPTY))).isEmpty();
    }
    @Test void disabledCapabilityCannotGrantAnAction() {
        jdbc.update("update summer_authorization_capability set status='DISABLED' where capability_id=?",capability);
        assertThat(LocalConsoleIdentityClaims.capabilities(identities.resolve(username,EMPTY))).isEmpty();
    }
    @Test void removedManagedBindingCannotReuseThePriorTokenClaims() {
        var prior=identities.resolve(username,EMPTY);
        jdbc.update("delete from summer_authorization_user_binding where username=?",username);
        var next=identities.resolve(username,prior);
        assertThat(LocalConsoleIdentityClaims.capabilities(next)).isEmpty();
        assertThat((Collection<?>)next.get("enterprise_memberships")).isEmpty();
    }
    @Test void disabledMemberLosesTenantAndOrganizationClaimsEvenWithRoleBindings() {
        jdbc.update("insert into summer_organization_context (subject,enterprise_id,organization_id,revision,created_at,updated_at) values (?,'ent-acme','org-engineering','1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",username);
        var prior = identities.resolve(username, EMPTY);
        jdbc.update("update summer_enterprise_member set status='DISABLED' where subject=?", username);
        var next = identities.resolve(username, prior);
        assertThat((Collection<?>) next.get("enterprise_memberships")).isEmpty();
        assertThat(LocalConsoleIdentityClaims.capabilities(next)).isEmpty();
        assertThat(next).doesNotContainKeys("organization_id", "organization_context", "active_enterprise_id");
    }
    @Test void roleBindingsWithoutMembershipFailClosed() {
        jdbc.update("delete from summer_enterprise_member where subject=?", username);
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
}
