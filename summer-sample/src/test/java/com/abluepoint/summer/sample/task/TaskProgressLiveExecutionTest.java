package com.abluepoint.summer.sample.task;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-live-progress;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.quartz.job-store-type=memory",
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskProgressLiveExecutionTest {
    @Autowired TaskDefinitionRepository definitions;
    @Autowired TaskExecutionRepository executions;
    @Autowired TaskProgressEventRepository events;
    @Autowired TaskExecutionService service;
    @Autowired MockMvc mvc;

    @Test
    void runningProgressIsCommittedAndStreamedBeforeHandlerFinishes() throws Exception {
        String taskId = UUID.randomUUID().toString();
        definitions.saveAndFlush(new TaskDefinitionEntity(taskId, "ent-acme", "Live " + taskId,
                "system.sleep", "0 0 0 1 1 ? 2099", "{\"millis\":5000}", 0, 15000));
        var stream = mvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/events", taskId)
                        .with(TaskAuthenticationFixture.cookie("enterprise.tasks.read")))
                .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var running = executor.submit(() -> service.execute(taskId, "live-fire", "live-key", Instant.now(), false));
            try {
                await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                    assertThat(running).isNotDone();
                    assertThat(events.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc("ent-acme", taskId))
                            .extracting(TaskProgressEventEntity::eventType)
                            .contains("execution.started", "execution.attempt", "execution.progress").doesNotContain("execution.succeeded");
                    assertThat(executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc("ent-acme", taskId))
                            .extracting(TaskExecutionEntity::status).containsExactly(TaskExecutionStatus.RUNNING);
                    assertThat(stream.getResponse().getContentAsString()).contains("event:execution.attempt", "event:execution.progress");
                });
            } finally {
                running.get(15, TimeUnit.SECONDS);
            }
        } finally {
            stream.getRequest().getAsyncContext().complete();
        }
        assertThat(executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc("ent-acme", taskId))
                .extracting(TaskExecutionEntity::status).containsExactly(TaskExecutionStatus.SUCCESS);
    }
}
