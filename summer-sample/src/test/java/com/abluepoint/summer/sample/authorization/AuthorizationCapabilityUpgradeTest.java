package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationCapabilityUpgradeTest {
    private static final String CODE = "enterprise.roles.read";
    private static final String EXISTING_ID = "cap-existing-role-reader";
    private final AuthorizationCapabilityRepository capabilities = mock(AuthorizationCapabilityRepository.class);
    private final AuthorizationRoleRepository roles = mock(AuthorizationRoleRepository.class);
    private final org.springframework.jdbc.core.JdbcTemplate membershipJdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    private final AuthorizationService service = new AuthorizationService(
            mock(AuthorizationUserBindingRepository.class), capabilities, roles,
            mock(AuthorizationChangeEventService.class), mock(AuthorizationAuditStore.class), membershipJdbc);

    @org.springframework.beans.factory.annotation.Autowired CurrentManagedAuthorizationTestFixture fixture;
    @org.springframework.beans.factory.annotation.Autowired com.abluepoint.summer.sample.authorization.application.permission.ManagedRoleService currentRoles;
    @org.springframework.beans.factory.annotation.Autowired com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess current;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate db;
    private static final com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain DOMAIN =
            com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");

    @Test
    void assigningACodeCannotReenterTheRetiredApiAndCurrentRolesKeepPermissionIdentity() {
        String permission = fixture.grantHandlers(DOMAIN, "administrator", (handler,path,method) -> path.equals("/api/v1/enterprises/{enterpriseId}/roles") && method.equals("GET"));
        db.update("update summer_permission set version=7 where domain_key=? and permission_id=?", DOMAIN.key(), permission);
        long count = db.queryForObject("select count(*) from summer_permission_identity where domain_key=?", Long.class, DOMAIN.key());
        retiredCreate();
        var role = currentRoles.mutate(DOMAIN, "administrator", "role", null, "POST",
                Map.of("name", "Reader", "code", "reader", "permissionIds", List.of(permission)), null, "current-reader", "upgrade");
        assertThat(((Map<?,?>)role.body()).get("permissionIds")).isEqualTo(List.of(permission));
        assertThat(db.queryForObject("select version from summer_permission where domain_key=? and permission_id=?", Long.class, DOMAIN.key(), permission)).isEqualTo(7L);
        assertThat(db.queryForObject("select count(*) from summer_permission_identity where domain_key=?", Long.class, DOMAIN.key())).isEqualTo(count);
        verifyNoInteractions(capabilities, roles);
    }

    @Test
    void disabledPermissionCannotBeRevivedByFixtureReinstallationOrRetiredSeed() throws Exception {
        String permission = fixture.grantHandlers(DOMAIN, "administrator", (handler,path,method) -> path.equals("/api/v1/enterprises/{enterpriseId}/roles") && method.equals("GET"));
        db.update("update summer_permission set status='DISABLED',version=7 where domain_key=? and permission_id=?", DOMAIN.key(), permission);
        service.reloadPersistentState();
        retiredCreate();
        fixture.install(DOMAIN);
        assertThat(db.queryForMap("select status,version from summer_permission where domain_key=? and permission_id=?", DOMAIN.key(), permission))
                .containsEntry("status", "DISABLED").containsEntry("version", 7L);
        var resources = db.queryForList("select resource_id from summer_permission_resource where domain_key=? and permission_id=?", String.class, DOMAIN.key(), permission);
        assertThat(resources).isNotEmpty().allSatisfy(id -> assertThat(current.allows(DOMAIN, admin(), "INTERFACE", id)).isFalse());
        verifyNoInteractions(capabilities, roles);
    }

    private void retiredCreate() {
        assertThatThrownBy(() -> service.createRole("ent-acme", new RoleCreateRequest("Reader", "reader", List.of(CODE), null),
                "retired-reader", "upgrade", admin()))
                .isInstanceOf(com.abluepoint.summer.common.error.SummerException.class)
                .hasMessage("Legacy code, deny and bundle grant APIs are retired; use permission and role management")
                .satisfies(error -> assertThat(((com.abluepoint.summer.common.error.SummerException) error).errorCode()).isSameAs(AuthorizationErrorCode.INVALID));
    }

    private static JwtAuthenticationToken admin() {
        List<String> grants = List.of("enterprise.roles.write", "enterprise.capabilities.read");
        var jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject("administrator")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", grants)).build();
        return new JwtAuthenticationToken(jwt, grants.stream().map(code -> new SimpleGrantedAuthority("SCOPE_" + code)).toList());
    }
}
