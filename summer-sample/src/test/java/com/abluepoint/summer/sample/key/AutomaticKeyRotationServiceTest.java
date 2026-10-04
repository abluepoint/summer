package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AutomaticKeyRotationServiceTest {
    @Test
    void rotatesDueKeyOnceAndSchedulesTheNextWindow() {
        MysqlSummerKeyStore store = mock(MysqlSummerKeyStore.class);
        KeyRotationStateRepository states = mock(KeyRotationStateRepository.class);
        KeyRotationStateEntity state = new KeyRotationStateEntity(90, 30, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(states.findById(1)).thenReturn(Optional.of(state));
        when(store.currentVersion()).thenReturn(7);
        when(store.rotate(30, 7, "automatic-key-rotation-v7"))
                .thenReturn(new MysqlSummerKeyStore.Rotation("kid-8", 8, 30));

        AutomaticKeyRotationService service = new AutomaticKeyRotationService(store, states, noopAudit());
        RotationStatusView result = service.rotateIfDue();

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.nextRotationAt()).isAfter(Instant.now());
        assertThat(result.failureCount()).isZero();
        verify(store).rotate(30, 7, "automatic-key-rotation-v7");
    }

    @Test
    void recordsFailureAndLeavesRetryScheduleWithoutExposingFailureDetails() {
        MysqlSummerKeyStore store = mock(MysqlSummerKeyStore.class);
        KeyRotationStateRepository states = mock(KeyRotationStateRepository.class);
        KeyRotationStateEntity state = new KeyRotationStateEntity(90, 30, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(states.findById(1)).thenReturn(Optional.of(state));
        when(store.currentVersion()).thenReturn(2);
        when(store.rotate(anyInt(), anyInt(), anyString())).thenThrow(new IllegalStateException("private material must stay hidden"));

        RotationStatusView result = new AutomaticKeyRotationService(store, states, noopAudit()).rotateIfDue();

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.lastError()).isEqualTo("IllegalStateException");
        assertThat(result.nextRotationAt()).isAfter(Instant.now());
    }

    @Test
    void recoversACommittedRotationWithoutGeneratingAnotherKey() {
        MysqlSummerKeyStore store = mock(MysqlSummerKeyStore.class);
        KeyRotationStateRepository states = mock(KeyRotationStateRepository.class);
        KeyRotationStateEntity state = new KeyRotationStateEntity(90, 30, Instant.now().minus(1, ChronoUnit.MINUTES));
        state.attempt(Instant.now().minus(11, ChronoUnit.MINUTES), 4);
        when(states.findById(1)).thenReturn(Optional.of(state));
        when(store.currentVersion()).thenReturn(5);

        RotationStatusView result = new AutomaticKeyRotationService(store, states, noopAudit()).rotateIfDue();

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(store, never()).rotate(anyInt(), anyInt(), anyString());
    }

    private static SecurityAuditLogger noopAudit() {
        return (event, principal, attributes) -> { };
    }
}
