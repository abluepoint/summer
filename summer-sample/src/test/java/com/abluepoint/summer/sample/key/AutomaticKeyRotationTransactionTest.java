package com.abluepoint.summer.sample.key;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(properties = "summer.security.storage.keys=mysql")
@DirtiesContext
class AutomaticKeyRotationTransactionTest {
    private static final String MASTER = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void keyProperties(DynamicPropertyRegistry properties) {
        properties.add("summer.sample.security.master-key", () -> MASTER);
    }

    @Autowired AutomaticKeyRotationService automatic;
    @Autowired MysqlSummerKeyStore store;
    @Autowired KeyRotationStateRepository states;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void databaseRotationFailureCommitsRetryStateAndLaterRetrySucceedsOnce() {
        int initialVersion = store.currentVersion();
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(ignored -> {
            KeyRotationStateEntity state = states.findById(1).orElseThrow();
            state.schedule(90, 30, Instant.now().minus(1, ChronoUnit.MINUTES));
            states.saveAndFlush(state);
        });
        // Real persistence failure when the new key is flushed, not a mocked exception.
        jdbc.execute("ALTER TABLE summer_security_keys ADD CONSTRAINT t28_fail_next_key CHECK (version <= " + initialVersion + ")");
        try {
            assertThatCode(() -> automatic.rotateIfDue()).doesNotThrowAnyException();
            RotationStatusView failed = automatic.status();
            assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(failed.failureCount()).isEqualTo(1);
            assertThat(failed.lastError()).isNotBlank();
            assertThat(failed.nextRotationAt()).isAfter(Instant.now());
            assertThat(store.currentVersion()).isEqualTo(initialVersion);
            assertThat(store.metadata().stream().filter(key -> "ACTIVE".equals(key.get("status"))).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM summer_audit_event WHERE resource='security:automatic-key-rotation' AND action='KEY_AUTOMATIC_ROTATION_FAILED' AND result='FAILURE'", Long.class)).isEqualTo(1L);
        } finally {
            jdbc.execute("ALTER TABLE summer_security_keys DROP CONSTRAINT t28_fail_next_key");
        }

        // Time fixture only: retry the same failed attempt with its persisted fence.
        jdbc.update("UPDATE summer_security_key_rotation SET next_rotation_at=?, version=version+1 WHERE rotation_id=1",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        RotationStatusView succeeded = automatic.rotateIfDue();
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.failureCount()).isZero();
        assertThat(store.currentVersion()).isEqualTo(initialVersion + 1);
        automatic.rotateIfDue();
        assertThat(store.currentVersion()).isEqualTo(initialVersion + 1);
        assertThat(store.metadata().stream().filter(key -> "ACTIVE".equals(key.get("status"))).count()).isEqualTo(1);
        assertThat(store.metadata().stream().filter(key -> "GRACE".equals(key.get("status"))).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM summer_audit_event WHERE resource='security:automatic-key-rotation' AND action='KEY_AUTOMATIC_ROTATION_SUCCEEDED' AND result='SUCCESS'", Long.class)).isEqualTo(1L);
    }
}
