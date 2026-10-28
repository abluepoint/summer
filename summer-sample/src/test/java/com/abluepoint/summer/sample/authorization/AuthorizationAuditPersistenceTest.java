package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.application.command.RoleCreateCommand;
import com.abluepoint.summer.sample.authorization.application.command.UserRoleAssignmentCommand;
import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationChangeEventOutboxRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"summer.security.storage.keys=memory", "summer.sample.authorization.legacy-scope-compatibility=true"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationAuditPersistenceTest {
    private static final String ENTERPRISE = "ent-acme";
    @Autowired AuthorizationService authorization;
    @Autowired AuthorizationChangeEventOutboxRepository outbox;
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;

    @Test
    void committedMutationAuditCanBeReadByAnIndependentServiceInstance() {
        var role = authorization.createRole(ENTERPRISE, new RoleCreateCommand("Reader", "audit-reader", List.of(), null),
                "audit-create", "audit-request", admin());

        var rows = freshService().audit(ENTERPRISE, role.roleId(), admin());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("action", "ROLE_CREATED")
                .containsEntry("requestId", "audit-request").containsEntry("principal", "audit-admin")
                .containsKey("at");
        assertThat(outbox.findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(ENTERPRISE, "ROLE"))
                .anyMatch(event -> role.roleId().equals(event.resourceId()));
    }

    @Test
    void rolledBackMutationLeavesNeitherAuditNorOutboxEvent() {
        AtomicReference<String> roleId = new AtomicReference<>();
        long originalEvents = outbox.count();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            var role = authorization.createRole(ENTERPRISE, new RoleCreateCommand("Rollback", "audit-rollback", List.of(), null),
                    "audit-rollback", "audit-rollback-request", admin());
            roleId.set(role.roleId());
            assertThat(authorization.audit(ENTERPRISE, role.roleId(), admin())).hasSize(1);
            assertThat(outbox.count()).isGreaterThan(originalEvents);
            transaction.setRollbackOnly();
        });

        assertThat(authorization.audit(ENTERPRISE, roleId.get(), admin())).isEmpty();
        assertThat(freshService().audit(ENTERPRISE, roleId.get(), admin())).isEmpty();
        assertThat(outbox.count()).isEqualTo(originalEvents);
    }

    @Test
    void authorizationDecisionsRemainDurableWhenAnOuterTransactionRollsBack() {
        var role = authorization.createRole(ENTERPRISE, new RoleCreateCommand("Customer reader", "audit-decision-reader",
                List.of("GET:/api/customers"), null), "audit-decision-role", "role-request", admin());
        authorization.assignUserRoles(ENTERPRISE, "alice", new UserRoleAssignmentCommand(List.of(role.roleId()),
                List.of("POST:/api/customers")), "\"1\"", "audit-decision-binding", "binding-request", admin());

        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            assertThat(authorization.decision(principal("alice"), ENTERPRISE, "GET:/api/customers", "allow-request").allowed()).isTrue();
            assertThat(authorization.decision(principal("alice"), ENTERPRISE, "POST:/api/customers", "deny-request").allowed()).isFalse();
            transaction.setRollbackOnly();
        });

        var rows = freshService().audit(ENTERPRISE, "alice", admin());
        assertThat(rows).anySatisfy(row -> assertThat(row).containsEntry("action", "AUTHORIZATION_ALLOWED")
                .containsEntry("requestId", "allow-request").containsEntry("principal", "alice")
                .containsEntry("permissionCode", "GET:/api/customers").containsEntry("decision", "ALLOW")
                .containsEntry("reasonCode", "ROLE_ALLOW").containsKey("at"));
        assertThat(rows).anySatisfy(row -> assertThat(row).containsEntry("action", "AUTHORIZATION_DENIED")
                .containsEntry("requestId", "deny-request").containsEntry("principal", "alice")
                .containsEntry("permissionCode", "POST:/api/customers").containsEntry("decision", "DENY")
                .containsEntry("reasonCode", "USER_EXPLICIT_DENY").containsKey("at"));
    }

    @Test
    void menuSyncAndOverrideHaveDurableAuditsAlongsideTheirOutboxEvents() throws Exception {
        syncMenu();
        mvc.perform(patch("/api/v1/enterprises/ent-acme/menu-manifest/audit-menu")
                        .with(authentication(admin())).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "audit-menu-override").header("If-Match", "\"1\"")
                        .header("X-Request-Id", "menu-override-request").content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        assertThat(freshService().audit(ENTERPRISE, "manifest", admin())).anySatisfy(row ->
                assertThat(row).containsEntry("action", "MENU_MANIFEST_SYNCED").containsEntry("principal", "audit-admin")
                        .containsEntry("requestId", "menu-sync-request"));
        assertThat(freshService().audit(ENTERPRISE, "audit-menu", admin())).anySatisfy(row ->
                assertThat(row).containsEntry("action", "MENU_OVERRIDE_UPDATED").containsEntry("principal", "audit-admin")
                        .containsEntry("requestId", "menu-override-request"));
        assertThat(outbox.findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(ENTERPRISE, "MENU_MANIFEST")).hasSize(2);
    }

    @Test
    void menuMutationAuditAndOutboxRollBackTogether() {
        long originalEvents = outbox.count();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            try { syncMenu(); } catch (Exception failure) { throw new IllegalStateException(failure); }
            assertThat(authorization.audit(ENTERPRISE, "manifest", admin())).hasSize(1);
            assertThat(outbox.count()).isGreaterThan(originalEvents);
            transaction.setRollbackOnly();
        });

        assertThat(freshService().audit(ENTERPRISE, "manifest", admin())).isEmpty();
        assertThat(outbox.count()).isEqualTo(originalEvents);
    }

    @Test
    void earlyDecisionBranchesAlsoKeepTheirPersistedReasons() {
        authorization.decision(principal("legacy"), ENTERPRISE, "GET:/api/customers", "legacy-request");
        authorization.decision(withClaims(principal("managed"), Map.of("summer_managed_grants", true)),
                ENTERPRISE, "GET:/api/customers", "managed-request");
        var bootstrap = withClaims(principal("summer-user", "enterprise.roles.write"), Map.of(
                "summer_identity_source", "local-user", "platform_capabilities", List.of("platform.capabilities.write")));
        authorization.decision(bootstrap, ENTERPRISE, "POST:/api/v1/enterprises/{enterpriseId}/roles", "bootstrap-request");
        authorization.assignUserRoles(ENTERPRISE, "inactive", new UserRoleAssignmentCommand(List.of(), List.of()),
                "\"1\"", "inactive-binding", "inactive-binding-request", admin());
        authorization.decision(withClaims(principal("inactive"), Map.of("enterprise_memberships",
                List.of(Map.of("enterpriseId", ENTERPRISE, "status", "DISABLED")))),
                ENTERPRISE, "GET:/api/customers", "inactive-request");

        var independent = freshService();
        assertThat(independent.audit(ENTERPRISE, "legacy", admin())).anySatisfy(row ->
                assertThat(row).containsEntry("decision", "ALLOW").containsEntry("reasonCode", "LEGACY_SCOPE"));
        assertThat(independent.audit(ENTERPRISE, "managed", admin())).anySatisfy(row ->
                assertThat(row).containsEntry("decision", "DENY").containsEntry("reasonCode", "DEFAULT_DENY"));
        assertThat(independent.audit(ENTERPRISE, "summer-user", admin())).anySatisfy(row ->
                assertThat(row).containsEntry("decision", "ALLOW").containsEntry("reasonCode", "BOOTSTRAP_ADMIN"));
        assertThat(independent.audit(ENTERPRISE, "inactive", admin())).anySatisfy(row ->
                assertThat(row).containsEntry("decision", "DENY").containsEntry("reasonCode", "INACTIVE_MEMBERSHIP"));
    }

    private void syncMenu() throws Exception {
        mvc.perform(put("/api/v1/platform/menu-manifest").with(authentication(admin()))
                        .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "audit-menu-sync")
                        .header("X-Request-Id", "menu-sync-request")
                        .content("""
                                {"manifestVersion":1,"items":[{"menuId":"audit-menu","path":"/audit-menu",
                                "titleKey":"menu.audit","iconKey":"icon.audit","sortOrder":1,
                                "capabilityCode":"enterprise.roles.read"}]}
                                """))
                .andExpect(status().isOk());
    }

    private AuthorizationService freshService() {
        return context.getAutowireCapableBeanFactory().createBean(AuthorizationService.class);
    }

    private static JwtAuthenticationToken admin() {
        return principal("audit-admin", "enterprise.roles.write", "enterprise.roles.read", "enterprise.menu.write", "platform.menu.write");
    }

    private static JwtAuthenticationToken principal(String subject, String... capabilities) {
        Jwt jwt = Jwt.withTokenValue("test-only").header("alg", "none").subject(subject)
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", ENTERPRISE, "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of(ENTERPRISE, List.of(capabilities)))
                .claim("platform_capabilities", List.of("platform.menu.write")).build();
        return new JwtAuthenticationToken(jwt, java.util.Arrays.stream(capabilities)
                .map(code -> new SimpleGrantedAuthority("SCOPE_" + code)).toList());
    }

    private static JwtAuthenticationToken withClaims(JwtAuthenticationToken authentication, Map<String, Object> extraClaims) {
        Jwt original = authentication.getToken();
        Jwt jwt = Jwt.withTokenValue("test-only").headers(values -> values.putAll(original.getHeaders()))
                .claims(values -> { values.putAll(original.getClaims()); values.putAll(extraClaims); }).build();
        return new JwtAuthenticationToken(jwt, authentication.getAuthorities(), authentication.getName());
    }
}
