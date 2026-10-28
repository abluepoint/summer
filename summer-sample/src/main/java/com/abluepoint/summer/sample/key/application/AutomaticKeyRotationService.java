package com.abluepoint.summer.sample.key.application;

import com.abluepoint.summer.sample.key.domain.KeyManagementStore;
import com.abluepoint.summer.sample.key.domain.KeyRotationState;
import com.abluepoint.summer.sample.key.domain.KeyRotationStateStore;

import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;
import com.abluepoint.summer.sample.customer.service.CustomerAuditService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class AutomaticKeyRotationService {
    private final KeyManagementStore store;
    private final KeyRotationStateStore states;
    private final SecurityAuditLogger audit;
    private final TransactionTemplate transactions;
    private final CustomerAuditService ledger;

    public AutomaticKeyRotationService(KeyManagementStore store, KeyRotationStateStore states,
                                 SecurityAuditLogger audit, PlatformTransactionManager transactionManager,
                                 CustomerAuditService ledger) {
        this.store = store;
        this.states = states;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ledger = ledger;
    }

    public synchronized RotationStatusView rotateIfDue() {
        // Match manual rotation's monitor-before-database-lock order, so an
        // overlapping HTTP rotation cannot deadlock this node's Quartz thread.
        synchronized (store) {
            String operationId = "automatic-key-rotation-" + java.util.UUID.randomUUID();
            try {
                return transactions.execute(ignored -> rotateInTransaction(operationId));
            } catch (RuntimeException failure) {
                // A failed key flush invalidates its transaction/persistence context.
                // Record the retry only after that transaction has fully rolled back.
                return transactions.execute(ignored -> recordFailure(failure, operationId));
            }
        }
    }

    private RotationStatusView rotateInTransaction(String operationId) {
        KeyRotationState state = states.lockForRotation().orElseThrow(
                () -> new IllegalStateException("SUMMER_KEY_ROTATION_STATE_MISSING"));
        Instant now = Instant.now();
        if (!state.dueAt(now)) return view(state);
        if (state.runningAt(now)) return view(state);
        int expectedVersion = store.currentVersion();
        // A node may have committed the key transaction and crashed before it
        // could mark the state successful. The version fence makes the retry
        // converge without generating a second key.
        if (state.alreadyRotated(expectedVersion)) {
            state.success(now, now.plus(state.periodDays(), java.time.temporal.ChronoUnit.DAYS));
            states.saveAndFlush(state);
            return view(state);
        }
        state.attempt(now, expectedVersion);
        states.saveAndFlush(state);
        String idempotencyKey = "automatic-key-rotation-v" + expectedVersion;
        store.rotate(state.retentionDays(), expectedVersion, idempotencyKey);
        state.success(now, now.plus(state.periodDays(), java.time.temporal.ChronoUnit.DAYS));
        states.saveAndFlush(state);
        ledger.recordResourceSuccess("security:automatic-key-rotation", "KEY_AUTOMATIC_ROTATION_SUCCEEDED", operationId, null);
        audit.record("security_key_automatic_rotation_succeeded", "system",
                Map.of("version", store.currentVersion(), "periodDays", state.periodDays(), "retentionDays", state.retentionDays()));
        return view(state);
    }

    private RotationStatusView recordFailure(RuntimeException failure, String operationId) {
        KeyRotationState state = states.lockForRotation().orElseThrow(
                () -> new IllegalStateException("SUMMER_KEY_ROTATION_STATE_MISSING"));
        Instant now = Instant.now();
        // Another node/manual rotation may have succeeded after our rollback.
        // Its future schedule must not be replaced by this older failure.
        if (!state.dueAt(now)) return view(state);
        Instant retryAt = state.retryAt(now);
        state.attempt(now, store.currentVersion());
        state.failure(now, failureCode(failure), retryAt);
        states.saveAndFlush(state);
        ledger.recordResourceFailure("security:automatic-key-rotation", "KEY_AUTOMATIC_ROTATION_FAILED", operationId, null,
                "SUMMER_KEY_ROTATION_FAILED");
        audit.record("security_key_automatic_rotation_failed", "system",
                Map.of("failureCount", state.failureCount(), "retryAt", retryAt.toString(), "errorCode", state.lastError()));
        return view(state);
    }

    public RotationStatusView status() { return view(requiredState()); }

    private KeyRotationState requiredState() {
        return states.current().orElseThrow(() -> new IllegalStateException("SUMMER_KEY_ROTATION_STATE_MISSING"));
    }

    private static String failureCode(RuntimeException failure) {
        String name = failure.getClass().getSimpleName();
        if (name == null || name.isBlank()) return "SUMMER_KEY_ROTATION_FAILED";
        return name.length() > 120 ? name.substring(0, 120) : name;
    }

    private static RotationStatusView view(KeyRotationState state) {
        return new RotationStatusView(state.status(), state.periodDays(), state.retentionDays(),
                state.nextRotationAt(), state.lastAttemptAt(), state.lastSuccessAt(),
                state.failureCount(), state.lastError());
    }
}

