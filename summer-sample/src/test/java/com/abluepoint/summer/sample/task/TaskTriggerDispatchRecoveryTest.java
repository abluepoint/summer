package com.abluepoint.summer.sample.task;
import com.abluepoint.summer.sample.task.infrastructure.QuartzTaskSchedulingAdapter;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionEntity;
import com.abluepoint.summer.sample.task.infrastructure.TaskTriggerReceiptRepository;
import com.abluepoint.summer.sample.task.domain.TaskConflictException;
import com.abluepoint.summer.sample.task.infrastructure.TaskAttemptRepository;
import com.abluepoint.summer.sample.task.infrastructure.TaskTriggerReceiptEntity;
import com.abluepoint.summer.sample.task.application.TaskSchedulerService;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionRepository;
import com.abluepoint.summer.sample.task.infrastructure.TrustedTaskRegistry;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionRepository;

import com.abluepoint.summer.sample.customer.service.CustomerAuditService;
import org.junit.jupiter.api.Test;
import org.quartz.*;
import tools.jackson.databind.ObjectMapper;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TaskTriggerDispatchRecoveryTest {
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
                new TrustedTaskRegistry(), new QuartzTaskSchedulingAdapter(scheduler), new ObjectMapper(), mock(CustomerAuditService.class));
        assertThatThrownBy(() -> service.trigger("ent-acme", "task", "retry-key", "request", null))
                .isInstanceOf(TaskConflictException.class);
        service.trigger("ent-acme", "task", "retry-key", "request", null);
        verify(scheduler, times(2)).scheduleJob(any(Trigger.class));
        verify(receipts, times(1)).saveAndFlush(any());
    }
}
