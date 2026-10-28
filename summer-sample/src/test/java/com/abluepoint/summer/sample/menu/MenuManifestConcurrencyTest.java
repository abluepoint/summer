package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.sample.menu.application.command.MenuManifestOverrideCommand;
import com.abluepoint.summer.sample.menu.application.MenuManifestService;
import com.abluepoint.summer.sample.menu.application.view.MenuManifestItemView;
import com.abluepoint.summer.sample.menu.domain.MenuManifestItem;
import com.abluepoint.summer.sample.menu.domain.exception.MenuManifestPreconditionException;
import com.abluepoint.summer.sample.menu.infrastructure.persistence.MenuManifestEntity;
import com.abluepoint.summer.sample.menu.infrastructure.persistence.MenuManifestOverrideRepository;
import com.abluepoint.summer.sample.menu.infrastructure.persistence.MenuManifestRepository;

import com.abluepoint.summer.sample.authorization.application.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.application.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.AuthorizationService;

import com.abluepoint.summer.sample.authorization.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
class MenuManifestConcurrencyTest {
    @Autowired MenuManifestRepository manifests;
    @Autowired MenuManifestOverrideRepository overrides;
    @Autowired AuthorizationService authorization;
    @Autowired AuthorizationChangeEventService events;
    @Autowired AuthorizationAuditStore audit;
    @Autowired PlatformTransactionManager transactions;

    private MenuManifestService node(String requestId) {
        return new MenuManifestService(manifests, overrides, authorization, events, () -> requestId, audit);
    }
    private JwtAuthenticationToken admin() {
        return new JwtAuthenticationToken(Jwt.withTokenValue("test-only").header("alg", "none").subject("menu-admin")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.menu.write"))).build(),
                List.of(new SimpleGrantedAuthority("SCOPE_enterprise.menu.write")));
    }
    private String menu() {
        String id = "race-" + UUID.randomUUID();
        manifests.saveAndFlush(new MenuManifestEntity(new MenuManifestItem(id, null, "/" + id, "menu.race", "race", 0, null), 1));
        return id;
    }
    private MenuManifestItemView current(String id) {
        return node("read").management("ent-acme", admin()).items().stream().filter(i -> i.menuId().equals(id)).findFirst().orElseThrow();
    }
    @Test void concurrentInitialOverridesHaveOneWinner() throws Exception { race(false); }
    @Test void concurrentExistingOverridesHaveOneWinner() throws Exception { race(true); }
    private void race(boolean existing) throws Exception {
        String id = menu(); var tx = new TransactionTemplate(transactions);
        if (existing) tx.execute(s -> node("setup").override("ent-acme", id, new MenuManifestOverrideCommand(true, null, 9, null), "\"1\"", "setup", admin()));
        long version = existing ? 2 : 1; var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> write(node("writer-a"), tx, id, version, 101, barrier));
            var b = workers.submit(() -> write(node("writer-b"), tx, id, version, 102, barrier));
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 412);
        }
        assertThat(current(id).version()).isEqualTo(version + 1);
        assertThat(audit.read("ent-acme", id).stream().filter(r -> "MENU_OVERRIDE_UPDATED".equals(r.get("action")) && !"setup".equals(r.get("requestId")))).hasSize(1);
    }
    private int write(MenuManifestService service, TransactionTemplate tx, String id, long version, int order, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try { tx.execute(s -> service.override("ent-acme", id, new MenuManifestOverrideCommand(null, null, order, null), "\"" + version + "\"", "writer-" + order, admin())); return 200; }
        catch (MenuManifestPreconditionException expected) { return 412; }
    }
    @Test void rollbackDoesNotPublishIdempotencyReceiptAndSameKeyRetryPersists() {
        String id = menu(); var tx = new TransactionTemplate(transactions); var service = node("retry");
        var patch = new MenuManifestOverrideCommand(false, null, 99, null);
        tx.executeWithoutResult(s -> { service.override("ent-acme", id, patch, "\"1\"", "same-key", admin()); s.setRollbackOnly(); });
        assertThat(current(id).version()).isEqualTo(1);
        tx.execute(s -> service.override("ent-acme", id, patch, "\"1\"", "same-key", admin()));
        assertThat(current(id).version()).isEqualTo(2);
        assertThat(current(id).enabled()).isFalse();
        assertThat(audit.read("ent-acme", id).stream().filter(r -> "MENU_OVERRIDE_UPDATED".equals(r.get("action")))).hasSize(1);
    }
}
