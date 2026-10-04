package com.abluepoint.summer.sample.task;

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
}
