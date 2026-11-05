package com.abluepoint.summer.sample.menu;

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
    @Autowired org.springframework.jdbc.core.JdbcTemplate menuDb;
    @org.junit.jupiter.api.BeforeEach void publicationState() { menuDb.update("insert into summer_menu_publication(publication_id,published,manifest_version) values ('global',false,0) on duplicate key update publication_id=publication_id"); }
    @Autowired MenuManifestRepository manifests;
    @Autowired MenuManifestOverrideRepository overrides;
    @Autowired AuthorizationService authorization;
    @Autowired AuthorizationChangeEventService events;
    @Autowired AuthorizationAuditStore audit;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.abluepoint.summer.sample.authorization.application.permission.MenuPublicationState publication;

    private MenuManifestService node(String requestId) {
        return new MenuManifestService(manifests, overrides, authorization, events, () -> requestId, audit, publication);
    }
    private JwtAuthenticationToken admin() {
        return new JwtAuthenticationToken(Jwt.withTokenValue("test-only").header("alg", "none").subject("menu-admin")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.menu.write"))).build(),
                List.of(new SimpleGrantedAuthority("SCOPE_enterprise.menu.write")));
    }
    private String menu() {
        String id = "race-" + UUID.randomUUID();
        manifests.saveAndFlush(new MenuManifestEntity(new MenuManifestItemRequest(id, null, "/" + id, "menu.race", "race", 0, null), 1));
        menuDb.update("update summer_menu_publication set published=true,manifest_version=1 where publication_id='global'");
        return id;
    }
    private MenuManifestItemView current(String id) {
        return node("read").management("ent-acme", admin()).items().stream().filter(i -> i.menuId().equals(id)).findFirst().orElseThrow();
    }
    @Test void concurrentInitialOverridesHaveOneWinner() throws Exception { race(false); }
    @Test void concurrentExistingOverridesHaveOneWinner() throws Exception { race(true); }
    private void race(boolean existing) throws Exception {
        String id = menu(); var tx = new TransactionTemplate(transactions);
        if (existing) tx.execute(s -> node("setup").override("ent-acme", id, new MenuManifestOverrideRequest(true, null, 9, null), "\"1\"", "setup", admin()));
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
        try { tx.execute(s -> service.override("ent-acme", id, new MenuManifestOverrideRequest(null, null, order, null), "\"" + version + "\"", "writer-" + order, admin())); return 200; }
        catch (com.abluepoint.summer.common.error.SummerException expected) {
            if (expected.errorCode() != com.abluepoint.summer.sample.error.SampleTransportErrorCode.PRECONDITION_FAILED) throw expected; return 412; }
    }
    @Test void rollbackDoesNotPublishIdempotencyReceiptAndSameKeyRetryPersists() {
        String id = menu(); var tx = new TransactionTemplate(transactions); var service = node("retry");
        var patch = new MenuManifestOverrideRequest(false, null, 99, null);
        tx.executeWithoutResult(s -> { service.override("ent-acme", id, patch, "\"1\"", "same-key", admin()); s.setRollbackOnly(); });
        assertThat(current(id).version()).isEqualTo(1);
        tx.execute(s -> service.override("ent-acme", id, patch, "\"1\"", "same-key", admin()));
        assertThat(current(id).version()).isEqualTo(2);
        assertThat(current(id).enabled()).isFalse();
        assertThat(audit.read("ent-acme", id).stream().filter(r -> "MENU_OVERRIDE_UPDATED".equals(r.get("action")))).hasSize(1);
    }
}
