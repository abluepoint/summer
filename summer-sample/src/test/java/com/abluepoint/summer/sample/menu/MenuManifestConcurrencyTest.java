package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.sample.authorization.*;
import com.abluepoint.summer.sample.authorization.application.permission.MenuCatalogService;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    @Autowired MenuManifestRepository manifests;
    @Autowired AuthorizationChangeEventService events;
    @Autowired AuthorizationAuditStore audit;
    @Autowired PlatformTransactionManager transactions;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources interfaces;
    private BusinessManagedAuthorizationTestFixture managed;
    private static final Domain DOMAIN = Domain.platform();

    @org.junit.jupiter.api.BeforeEach void managedDomain() {
        managed = new BusinessManagedAuthorizationTestFixture(menuDb, transactions, interfaces);
        managed.identity(DOMAIN.key(), "menu-admin");
        menuDb.update("insert into summer_menu_publication(publication_id,published,manifest_version) values ('global',false,0) on duplicate key update publication_id=publication_id");
    }
    @org.junit.jupiter.api.AfterEach void releaseManagedDomain() { managed.close(); }
    @Autowired com.abluepoint.summer.sample.authorization.application.permission.MenuPublicationState publication;

    // The HTTP adapter owns authorization. Race the current transactional catalog seam on two nodes.
    @Autowired com.abluepoint.summer.sample.dictionary.PlatformDictionaryOptions dictionaries;
    private MenuCatalogService node() {
        return new MenuCatalogService(menuDb, json, audit, events, publication, dictionaries);
    }
    private String menu() {
        String id = "race-" + UUID.randomUUID();
        manifests.saveAndFlush(new MenuManifestEntity(new MenuManifestItemRequest(id, null, "/" + id, "menu.race", "race", 0, null, "web"), 1));
        menuDb.update("update summer_menu_publication set published=true,manifest_version=1 where publication_id='global'");
        return id;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> current(String id) {
        return ((List<Map<String, Object>>) node().read(DOMAIN, false).get("items")).stream()
                .filter(i -> i.get("menuId").equals(id)).findFirst().orElseThrow();
    }
    @Test void concurrentInitialOverridesHaveOneWinner() throws Exception { race(false); }
    @Test void concurrentExistingOverridesHaveOneWinner() throws Exception { race(true); }
    private void race(boolean existing) throws Exception {
        String id = menu(); var tx = new TransactionTemplate(transactions);
        if (existing) tx.execute(s -> node().mutate(DOMAIN, "menu-admin", id, Map.of("enabled", true, "sortOrder", 9), "\"1\"", "setup", "setup"));
        long version = existing ? 2 : 1; var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> write(node(), tx, id, version, 101, barrier));
            var b = workers.submit(() -> write(node(), tx, id, version, 102, barrier));
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 412);
        }
        assertThat(((Number) current(id).get("version")).longValue()).isEqualTo(version + 1);
        assertThat(audit.read(DOMAIN.key(), id).stream().filter(r -> "MENU_LAYOUT_CHANGED".equals(r.get("action")) && !"setup".equals(r.get("requestId")))).hasSize(1);
    }
    private int write(MenuCatalogService service, TransactionTemplate tx, String id, long version, int order, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try { tx.execute(s -> service.mutate(DOMAIN, "menu-admin", id, Map.of("sortOrder", order), "\"" + version + "\"", "writer-" + order, "writer-" + order)); return 200; }
        catch (com.abluepoint.summer.common.error.SummerException expected) {
            if (expected.errorCode() != com.abluepoint.summer.sample.error.SampleTransportErrorCode.PERMISSION_STALE) throw expected; return 412; }
    }
    @Test void rollbackDoesNotPublishIdempotencyReceiptAndSameKeyRetryPersists() {
        String id = menu(); var tx = new TransactionTemplate(transactions); var service = node();
        var patch = Map.<String, Object>of("enabled", false, "sortOrder", 99);
        tx.executeWithoutResult(s -> { service.mutate(DOMAIN, "menu-admin", id, patch, "\"1\"", "same-key", "retry"); s.setRollbackOnly(); });
        assertThat(((Number) current(id).get("version")).longValue()).isEqualTo(1);
        assertThat(menuDb.queryForObject("select count(*) from summer_permission_mutation where domain_key=? and operation_key=? and idempotency_key='same-key'", Integer.class, DOMAIN.key(), "menu:layout:" + id)).isZero();
        assertThat(audit.read(DOMAIN.key(), id)).isEmpty();
        assertThat(menuDb.queryForObject("select count(*) from summer_authorization_change_outbox where enterprise_id=? and resource_id=?", Integer.class, DOMAIN.key(), id)).isZero();
        tx.execute(s -> service.mutate(DOMAIN, "menu-admin", id, patch, "\"1\"", "same-key", "retry"));
        assertThat(((Number) current(id).get("version")).longValue()).isEqualTo(2);
        assertThat(current(id).get("enabled")).isEqualTo(false);
        assertThat(audit.read(DOMAIN.key(), id).stream().filter(r -> "MENU_LAYOUT_CHANGED".equals(r.get("action")))).hasSize(1);
    }
}
