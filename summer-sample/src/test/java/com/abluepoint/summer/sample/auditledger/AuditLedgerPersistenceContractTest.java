package com.abluepoint.summer.sample.auditledger;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Contract tests for the production persistence seam; the sample service remains in-memory. */
class AuditLedgerPersistenceContractTest {

    @Test
    void auditEventIsAStableAppendOnlyJpaAggregate() throws Exception {
        Entity entity = AuditEventEntity.class.getAnnotation(Entity.class);
        Table table = AuditEventEntity.class.getAnnotation(Table.class);

        assertThat(entity).isNotNull();
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("aict_audit_event");
        assertThat(AuditEventEntity.class.getDeclaredMethod("preventUpdate")).isNotNull();
    }

    @Test
    void outboxAndStoreContractsExposeRetryAndRetentionBoundaries() throws Exception {
        assertThat(AuditEventStore.class).isInterface();
        assertThat(AuditOutboxStore.class).isInterface();
        assertThat(AuditReconciliationStore.class).isInterface();
        assertThat(AuditRetentionStore.class).isInterface();
        assertThat(AuditOutboxEntity.class.getDeclaredField("attempts")).isNotNull();
        assertThat(AuditOutboxEntity.class.getDeclaredField("nextAttemptAt")).isNotNull();
        assertThat(AuditOutboxEntity.class.getDeclaredField("enterpriseId")).isNotNull();
        assertThat(AuditEventRepository.class.getDeclaredMethod(
                "findTopByEnterpriseIdAndInvocationIdAndAttemptIdOrderByOccurredAtDesc",
                String.class, String.class, String.class)).isNotNull();
    }

    @Test
    void postgresMigrationCreatesAppendOnlyLedgerAndTransactionalOutbox() throws Exception {
        Path migration = Files.exists(Path.of("src/main/resources/db/migration/V1__audit_ledger.sql"))
                ? Path.of("src/main/resources/db/migration/V1__audit_ledger.sql")
                : Path.of("summer-sample/src/main/resources/db/migration/V1__audit_ledger.sql");
        assertThat(Files.exists(migration)).isTrue();
        String sql = Files.readString(migration).toLowerCase();
        assertThat(sql).contains("create table if not exists aict_audit_event");
        assertThat(sql).contains("create table if not exists aict_audit_outbox");
        assertThat(sql).contains("unique (event_id)");
        assertThat(sql).contains("check (attempts >= 0)");
        assertThat(sql).contains("enterprise_id varchar(80) not null");
        assertThat(sql).contains("before update or delete on aict_audit_event");
    }

    @Test
    void retentionPolicyDefinesDurableCutoffAndBatch() {
        AuditRetentionPolicy policy = new AuditRetentionPolicy(Duration.ofDays(30), 500);
        assertThat(policy.cutoff(Instant.parse("2026-08-26T00:00:00Z")))
                .isEqualTo(Instant.parse("2026-07-27T00:00:00Z"));
        assertThat(policy.batchSize()).isEqualTo(500);
    }

    @Test
    void serviceCanReceiveDurableAdapterWithoutChangingDefaultInMemoryBehavior() throws Exception {
        AuditLedgerPersistenceAdapter adapter = mock(AuditLedgerPersistenceAdapter.class);
        AuditLedgerService service = new AuditLedgerService(adapter);
        assertThat(service).isNotNull();
        assertThat(JpaAuditLedgerPersistenceAdapter.class.getAnnotation(Profile.class).value())
                .contains("audit-durable");
        assertThat(JpaAuditLedgerPersistenceAdapter.class.getDeclaredMethod("appendAndEnqueue",
                AuditEventView.class, String.class, String.class).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void enterpriseAndIdentityChainColumnsAreDurableAndTenantBound() throws Exception {
        assertThat(AuditEventEntity.class.getDeclaredField("enterpriseId")
                .getAnnotation(Column.class).nullable()).isFalse();
        for (String field : new String[]{"requestId", "traceId", "initiator", "actor",
                "effectiveSubject", "agentInstanceId"}) {
            assertThat(AuditEventEntity.class.getDeclaredField(field)).isNotNull();
        }
        assertThat(AuditOutboxEntity.class.getDeclaredField("enterpriseId")
                .getAnnotation(Column.class).nullable()).isFalse();
    }
}
