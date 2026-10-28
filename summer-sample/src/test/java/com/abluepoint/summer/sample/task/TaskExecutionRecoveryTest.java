package com.abluepoint.summer.sample.task;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionEntity;
import com.abluepoint.summer.sample.task.infrastructure.TaskAttemptRepository;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionEntity;
import com.abluepoint.summer.sample.task.application.TaskExecutionService;
import com.abluepoint.summer.sample.task.domain.TaskExecutionStatus;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionRepository;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionRepository;
import com.abluepoint.summer.sample.task.infrastructure.TaskAttemptEntity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.quartz.job-store-type=memory",
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskExecutionRecoveryTest {
    @Autowired TaskDefinitionRepository definitions;
    @Autowired TaskExecutionRepository executions;
    @Autowired TaskAttemptRepository attempts;
    @Autowired TaskExecutionService service;

    @Test
    void expiredLeaseIsTakenOverAndRecoveryKeepsBusinessKey() {
        String taskId = UUID.randomUUID().toString();
        definitions.saveAndFlush(new TaskDefinitionEntity(taskId, "ent-acme", "Recovery", "system.noop",
                "0/5 * * * * ?", "{}", 0, 30_000));
        String key = "scheduled:" + taskId + ":1";
        executions.saveAndFlush(new TaskExecutionEntity(UUID.randomUUID().toString(), taskId, "ent-acme", "dead-fire",
                key, "dead-node", Instant.now().minusSeconds(10)));

        service.execute(taskId, "recovery-fire", key, Instant.now(), true);

        var rows = executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc("ent-acme", taskId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().status()).isEqualTo(TaskExecutionStatus.SUCCESS);
        assertThat(rows.getFirst().idempotencyKey()).isEqualTo(key);
        assertThat(rows.getFirst().recoveryCount()).isEqualTo(1);
    }

    @Test
    void committedInterruptedAttemptCanRecoverEvenAfterLastConfiguredAttempt() {
        String taskId = UUID.randomUUID().toString();
        definitions.saveAndFlush(new TaskDefinitionEntity(taskId, "ent-acme", "Interrupted", "system.noop",
                "0 0 0 1 1 ? 2099", "{}", 0, 30_000));
        var execution = new TaskExecutionEntity(UUID.randomUUID().toString(), taskId, "ent-acme", "dead-fire",
                "interrupted-key", "dead-node", Instant.now().minusSeconds(10));
        execution.attempt(1);
        executions.saveAndFlush(execution);
        attempts.saveAndFlush(new TaskAttemptEntity(UUID.randomUUID().toString(), execution.executionId(), 1, "dead-node"));

        service.execute(taskId, "recovery-fire", "interrupted-key", Instant.now(), true);

        var recovered = executions.findById(execution.executionId()).orElseThrow();
        assertThat(recovered.status()).isEqualTo(TaskExecutionStatus.SUCCESS);
        assertThat(recovered.recoveryCount()).isEqualTo(1);
        assertThat(attempts.findAllByExecutionIdOrderByAttemptAsc(execution.executionId()))
                .extracting(TaskAttemptEntity::status)
                .containsExactly(TaskExecutionStatus.FAILED, TaskExecutionStatus.SUCCESS);
        assertThat(attempts.findAllByExecutionIdOrderByAttemptAsc(execution.executionId()).getFirst().failureCode())
                .isEqualTo("TASK_NODE_LOST");
    }

    @Test
    void concurrentDuplicateExecutionsDoNotCreateAnotherAttempt() throws Exception {
        String taskId = UUID.randomUUID().toString();
        definitions.saveAndFlush(new TaskDefinitionEntity(taskId, "ent-acme", "Concurrent", "system.sleep",
                "0 0 0 1 1 ? 2099", "{\"millis\":1000}", 0, 30_000));
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.execute(taskId, "fire-a", "shared-key", Instant.now(), false));
            var second = executor.submit(() -> service.execute(taskId, "fire-b", "shared-key", Instant.now(), false));
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        var values = executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc("ent-acme", taskId);
        assertThat(values).hasSize(1);
        assertThat(values.getFirst().status()).isEqualTo(TaskExecutionStatus.SUCCESS);
        assertThat(attempts.findAllByExecutionIdOrderByAttemptAsc(values.getFirst().executionId())).hasSize(1);
    }
}
