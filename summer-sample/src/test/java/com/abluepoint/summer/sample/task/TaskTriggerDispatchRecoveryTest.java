package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.sample.customer.CustomerAuditService;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TaskTriggerDispatchRecoveryTest {
    @Test
    void pendingRecoveryKeepsASchedulerConflictForTheNextAttempt() throws Exception {
        var fixture = pending();
        doThrow(new SchedulerException("scheduler outage")).doReturn(new java.util.Date())
                .when(fixture.scheduler()).scheduleJob(any(Trigger.class));
        assertThatCode(fixture.service()::dispatchPendingTriggers).doesNotThrowAnyException();
        fixture.service().dispatchPendingTriggers();
        verify(fixture.scheduler(), times(2)).scheduleJob(any(Trigger.class));
        verify(fixture.audit(), times(1)).recordTenantResourceSuccess(anyString(), anyString(), eq("TASK_TRIGGERED"), anyString(), isNull());
    }

    @Test
    void pendingRecoveryPropagatesAnUnrelatedBusinessRejection() {
        var fixture = pending();
        var denied = new SummerException(CommonErrorCode.FORBIDDEN);
        doThrow(denied).when(fixture.audit()).recordTenantResourceSuccess(anyString(), anyString(), eq("TASK_TRIGGERED"), anyString(), isNull());
        assertThatThrownBy(fixture.service()::dispatchPendingTriggers).isSameAs(denied);
    }

    private Pending pending() {
        var definitions = mock(TaskDefinitionRepository.class);
        var executions = mock(TaskExecutionRepository.class);
        var receipts = mock(TaskTriggerReceiptRepository.class);
        var scheduler = mock(Scheduler.class);
        var audit = mock(CustomerAuditService.class);
        var task = new TaskDefinitionEntity("task", "ent-acme", "dispatch", "system.noop", "0 0 0 1 1 ? 2099", "{}", 0);
        when(definitions.findById("task")).thenReturn(Optional.of(task));
        when(receipts.findPending(eq(TaskStatus.ACTIVE), any()))
                .thenReturn(List.of(new TaskTriggerReceiptEntity("receipt", "ent-acme", "task", "retry-key", "fingerprint")));
        var service = new TaskSchedulerService(definitions, executions, mock(TaskAttemptRepository.class), receipts,
                new TrustedTaskRegistry(), scheduler, new ObjectMapper(), audit);
        return new Pending(service, scheduler, audit);
    }

    private record Pending(TaskSchedulerService service, Scheduler scheduler, CustomerAuditService audit) {}

    @Test
    void retryingAReservedTriggerAfterSchedulerFailureDispatchesItAgain() throws Exception {
        var definitions = mock(TaskDefinitionRepository.class);
        var executions = mock(TaskExecutionRepository.class);
        var receipts = mock(TaskTriggerReceiptRepository.class);
        var scheduler = mock(Scheduler.class);
        var task = new TaskDefinitionEntity("task", "ent-acme", "dispatch", "system.noop", "0 0 0 1 1 ? 2099", "{}", 0);
        when(definitions.findById("task")).thenReturn(Optional.of(task));
        String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("task".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(receipts.findByEnterpriseIdAndIdempotencyKey("ent-acme", "retry-key")).thenReturn(Optional.empty(),
                Optional.of(new TaskTriggerReceiptEntity("receipt", "ent-acme", "task", "retry-key", fingerprint)));
        doThrow(new SchedulerException("test scheduler outage")).doReturn(new java.util.Date())
                .when(scheduler).scheduleJob(any(Trigger.class));
        var service = new TaskSchedulerService(definitions, executions, mock(TaskAttemptRepository.class), receipts,
                new TrustedTaskRegistry(), scheduler, new ObjectMapper(), mock(CustomerAuditService.class));
        assertThatThrownBy(() -> service.trigger("ent-acme", "task", "retry-key", "request", null))
                .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(TaskErrorCode.CONFLICT));
        service.trigger("ent-acme", "task", "retry-key", "request", null);
        verify(scheduler, times(2)).scheduleJob(any(Trigger.class));
        verify(receipts, times(1)).saveAndFlush(any());
    }
}
