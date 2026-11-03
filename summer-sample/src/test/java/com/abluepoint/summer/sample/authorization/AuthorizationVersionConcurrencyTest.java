package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Independent service instances share the real persistence boundary, like two JVMs. */
@SpringBootTest
@DirtiesContext
class AuthorizationVersionConcurrencyTest {
    @Autowired AuthorizationUserBindingRepository bindings;
    @Autowired AuthorizationCapabilityRepository capabilities;
    @Autowired AuthorizationRoleRepository roles;
    @Autowired AuthorizationChangeEventService events;
    @Autowired AuthorizationAuditStore audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private AuthorizationService node() {
        var node = new AuthorizationService(bindings, capabilities, roles, events, audit, jdbc);
        ReflectionTestUtils.setField(node, "legacyScopeCompatibility", true);
        node.loadUserAssignments();
        return node;
    }

    private JwtAuthenticationToken admin() {
        var grants = List.of("enterprise.roles.read", "enterprise.roles.write");
        return new JwtAuthenticationToken(Jwt.withTokenValue("test-only").header("alg", "none")
                .subject("version-test-admin")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", grants)).build(),
                grants.stream().map(code -> new SimpleGrantedAuthority("SCOPE_" + code)).toList());
    }

    @Test
    void twoNodesUpdatingTheSameVersionHaveOneWinnerAndOneMutationRecord() throws Exception {
        var tx = new TransactionTemplate(transactions);
        var setup = node();
        var role = tx.execute(s -> setup.createRole("ent-acme", new RoleCreateRequest("Original", "race-" + UUID.randomUUID(), List.of(), null), "create-race", "created", admin()));
        var left = node();
        var right = node();
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> update(tx, left, role.roleId(), "writer-a", barrier));
            var second = workers.submit(() -> update(tx, right, role.roleId(), "writer-b", barrier));
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter("PRECONDITION_FAILED"::equals)).hasSize(1);
            String winner = results.stream().filter(value -> !value.equals("PRECONDITION_FAILED")).findFirst().orElseThrow();
            var fresh = node().role("ent-acme", role.roleId(), admin());
            assertThat(fresh.version()).isEqualTo(2);
            assertThat(fresh.name()).isEqualTo(winner);
            assertThat(audit.read("ent-acme", role.roleId()).stream().filter(row -> row.get("action").equals("ROLE_UPDATED")).map(row -> row.get("requestId"))).containsExactly(winner);
            assertThat(events.list("ent-acme", "ROLE").items().stream().filter(event -> event.resourceId().equals(role.roleId()) && event.eventType().equals("ROLE_UPDATED")).map(AuthorizationChangeEventView::requestId)).containsExactly(winner);
            left.reloadPersistentState(); right.reloadPersistentState();
            assertThat(left.role("ent-acme", role.roleId(), admin())).isEqualTo(right.role("ent-acme", role.roleId(), admin()));
        }
    }

    private String update(TransactionTemplate tx, AuthorizationService node, String id, String name, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            return tx.execute(s -> node.updateRole("ent-acme", id, new RoleUpdateRequest(name, null, null, null, null), "\"1\"", name, name, admin()).name());
        } catch (com.abluepoint.summer.common.error.SummerException expected) {
            if (expected.errorCode() != com.abluepoint.summer.sample.error.SampleTransportErrorCode.PRECONDITION_FAILED) throw expected;
            return "PRECONDITION_FAILED";
        }
    }

    @Test
    void rolledBackRoleChangePublishesNeitherProjectionReceiptAuditNorOutbox() {
        var tx = new TransactionTemplate(transactions);
        var node = node();
        var role = tx.execute(s -> node.createRole("ent-acme", new RoleCreateRequest("Original", "rollback-" + UUID.randomUUID(), List.of(), null), "create-rollback", "created", admin()));
        var change = new RoleUpdateRequest("Changed", null, null, List.of("GET:/rollback-only"), null);
        tx.executeWithoutResult(s -> {
            node.updateRole("ent-acme", role.roleId(), change, "\"1\"", "rollback-update", "rolled-back", admin());
            s.setRollbackOnly();
        });
        assertThat(node.role("ent-acme", role.roleId(), admin())).isEqualTo(role);
        assertThat(node().role("ent-acme", role.roleId(), admin())).isEqualTo(role);
        assertThat(audit.read("ent-acme", role.roleId()).stream().filter(row -> row.get("action").equals("ROLE_UPDATED"))).isEmpty();
        assertThat(events.list("ent-acme", "ROLE").items().stream().filter(event -> event.resourceId().equals(role.roleId()) && event.eventType().equals("ROLE_UPDATED"))).isEmpty();
        var retried = tx.execute(s -> node.updateRole("ent-acme", role.roleId(), change, "\"1\"", "rollback-update", "committed-retry", admin()));
        assertThat(node().role("ent-acme", role.roleId(), admin())).isEqualTo(retried);
        assertThat(audit.read("ent-acme", role.roleId()).stream().filter(row -> row.get("action").equals("ROLE_UPDATED")).map(row -> row.get("requestId"))).containsExactly("committed-retry");
    }

    @Test
    void twoNodesChangingTheSameUserBindingHaveOneWinner() throws Exception {
        var tx = new TransactionTemplate(transactions);
        var setup = node();
        String username = "binding-" + UUID.randomUUID();
        tx.execute(s -> setup.assignUserRoles("ent-acme", username, new UserRoleAssignmentRequest(List.of(), List.of()), "\"1\"", "binding-create", "binding-created", admin()));
        var left = node(); var right = node(); var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> updateBinding(tx, left, username, "binding-a", barrier));
            var second = workers.submit(() -> updateBinding(tx, right, username, "binding-b", barrier));
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter("PRECONDITION_FAILED"::equals)).hasSize(1);
            String winner = results.stream().filter(value -> !value.equals("PRECONDITION_FAILED")).findFirst().orElseThrow();
            var fresh = node().userAssignment("ent-acme", username, admin());
            assertThat(fresh.version()).isEqualTo(2);
            assertThat(fresh.deniedCapabilityCodes()).containsExactly(winner);
            assertThat(audit.read("ent-acme", username).stream().filter(row -> row.get("action").equals("USER_ROLES_UPDATED")).map(row -> row.get("requestId"))).containsExactly("binding-created", winner);
            assertThat(events.list("ent-acme", "USER_BINDING").items().stream().filter(event -> event.resourceId().equals(username) && event.version() == 2).map(AuthorizationChangeEventView::requestId)).containsExactly(winner);
        }
    }

    private String updateBinding(TransactionTemplate tx, AuthorizationService node, String username, String name, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            tx.execute(s -> node.assignUserRoles("ent-acme", username, new UserRoleAssignmentRequest(List.of(), List.of(name)), "\"1\"", name, name, admin()));
            return name;
        } catch (com.abluepoint.summer.common.error.SummerException expected) {
            if (expected.errorCode() != com.abluepoint.summer.sample.error.SampleTransportErrorCode.PRECONDITION_FAILED) throw expected;
            return "PRECONDITION_FAILED";
        }
    }
}
