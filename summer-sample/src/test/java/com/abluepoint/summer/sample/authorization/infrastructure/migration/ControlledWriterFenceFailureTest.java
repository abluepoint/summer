package com.abluepoint.summer.sample.authorization.infrastructure.migration;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.migration.AuthorizationMigrationService;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ControlledWriterFenceFailureTest {
    private final JdbcTemplate operator = mock(JdbcTemplate.class);
    private final ControlledWriterFence fence = new ControlledWriterFence(mock(JdbcTemplate.class),
            new ObjectMapper(), mock(Environment.class), mock(AuthorizationMigrationService.class));

    @Test
    void unavailableSessionInventoryRetainsTheOriginalCause() {
        var failure = new DataAccessResourceFailureException("inventory unavailable");
        when(operator.queryForObject(anyString(), eq(Integer.class))).thenThrow(failure);

        var error = assertThrows(SummerException.class, () -> fence.requireSessionInventory(operator));

        assertThat(error.errorCode()).isEqualTo(PermissionErrorCode.CONFLICT);
        assertThat(error).hasMessage("Stable authenticated connection inventory unavailable; no privileges changed")
                .hasCause(failure);
        verify(operator, never()).execute(anyString());
    }

    @Test
    void failedDrainRetainsCauseWhenTheOwnedSessionStillExists() {
        var failure = new DataAccessResourceFailureException("drain unavailable");
        when(operator.queryForList(anyString(), eq(Long.class), eq("retired-runtime"))).thenReturn(List.of(42L));
        when(operator.queryForObject(anyString(), eq(Integer.class), eq(42L), eq("retired-runtime"))).thenReturn(1);
        doThrow(failure).when(operator).execute("KILL CONNECTION 42");

        var error = assertThrows(SummerException.class,
                () -> fence.drainRuntimeSessions(operator, "retired-runtime"));

        assertThat(error.errorCode()).isEqualTo(PermissionErrorCode.CONFLICT);
        assertThat(error).hasMessage("Owned authenticated session drain incomplete; resume the approved operation")
                .hasCause(failure);
    }

    @Test
    void failedKillIsSafeToResumeWhenTheOwnedSessionHasAlreadyGone() {
        when(operator.queryForList(anyString(), eq(Long.class), eq("retired-runtime"))).thenReturn(List.of(42L));
        when(operator.queryForObject(anyString(), eq(Integer.class), eq(42L), eq("retired-runtime")))
                .thenReturn(1, 0, 0);
        doThrow(new DataAccessResourceFailureException("already gone")).when(operator).execute("KILL CONNECTION 42");

        assertThat(fence.drainRuntimeSessions(operator, "retired-runtime")).containsExactly(42L);
    }
}
