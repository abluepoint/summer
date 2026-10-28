package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.application.command.RoleCreateCommand;
import com.abluepoint.summer.sample.authorization.application.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.application.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import com.abluepoint.summer.sample.authorization.domain.exception.AuthorizationValidationException;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationCapabilityEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationCapabilityRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationRoleRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationUserBindingRepository;

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

class AuthorizationCapabilityUpgradeTest {
    private static final String CODE = "enterprise.roles.read";
    private static final String EXISTING_ID = "cap-existing-role-reader";
    private final AuthorizationCapabilityRepository capabilities = mock(AuthorizationCapabilityRepository.class);
    private final AuthorizationRoleRepository roles = mock(AuthorizationRoleRepository.class);
    private final org.springframework.jdbc.core.JdbcTemplate membershipJdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    private final AuthorizationService service = new AuthorizationService(
            mock(AuthorizationUserBindingRepository.class), capabilities, roles,
            mock(AuthorizationChangeEventService.class), mock(AuthorizationAuditStore.class), membershipJdbc);

    @org.junit.jupiter.api.BeforeEach void persistedMembershipFixture() {
        org.mockito.Mockito.when(membershipJdbc.queryForObject(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "bootstrapUsername", "unassigned");
    }

    @Test
    void assigningACodeReusesItsPersistedIdentityInsteadOfInsertingANewSeed() {
        loadExistingCapability("ACTIVE");
        var role = service.createRole("ent-acme", new RoleCreateCommand("Reader", "reader", List.of(CODE), null),
                "create-reader", "upgrade", admin());

        assertThat(role.capabilityIds()).containsExactly(EXISTING_ID);
        assertThat(service.capabilities("ent-acme", admin()).capabilities())
                .filteredOn(capability -> CODE.equals(capability.code()))
                .singleElement().satisfies(capability -> {
                    assertThat(capability.capabilityId()).isEqualTo(EXISTING_ID);
                    assertThat(capability.version()).isEqualTo(7);
                });
        verify(capabilities, never()).save(any());
    }

    @Test
    void disabledPersistedCapabilityCannotBeRevivedByTheDefaultSeed() {
        loadExistingCapability("DISABLED");
        service.reloadPersistentState();

        assertThatThrownBy(() -> service.createRole("ent-acme",
                new RoleCreateCommand("Reader", "reader", List.of(CODE), null), "create-disabled", "upgrade", admin()))
                .isInstanceOf(AuthorizationValidationException.class).hasMessage("Capability is disabled");
        assertThat(service.capabilities("ent-acme", admin()).capabilities())
                .filteredOn(capability -> CODE.equals(capability.code()))
                .singleElement().satisfies(capability -> {
                    assertThat(capability.capabilityId()).isEqualTo(EXISTING_ID);
                    assertThat(capability.status()).isEqualTo("DISABLED");
                    assertThat(capability.version()).isEqualTo(7);
                });
        verify(capabilities, never()).save(any());
        verify(roles, never()).save(any());
    }

    private void loadExistingCapability(String status) {
        when(capabilities.findAllByOrderByCapabilityIdAsc()).thenReturn(List.of(
                new AuthorizationCapabilityEntity(EXISTING_ID, "ent-acme", "ENTERPRISE", CODE,
                        "roles", "read", "LOW", status, 7)));
        when(capabilities.existsById(EXISTING_ID)).thenReturn(true);
        service.loadUserAssignments();
    }

    private static JwtAuthenticationToken admin() {
        List<String> grants = List.of("enterprise.roles.write", "enterprise.capabilities.read");
        var jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject("administrator")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", grants)).build();
        return new JwtAuthenticationToken(jwt, grants.stream().map(code -> new SimpleGrantedAuthority("SCOPE_" + code)).toList());
    }
}
