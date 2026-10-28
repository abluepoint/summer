package com.abluepoint.summer.sample.task;
import com.abluepoint.summer.sample.task.infrastructure.TaskProgressEventEntity;
import com.abluepoint.summer.sample.task.application.TaskExecutionService;
import com.abluepoint.summer.sample.task.infrastructure.TaskProgressEventRepository;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.quartz.job-store-type=memory",
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskProgressSseHttpTest {
    @Autowired MockMvc mockMvc;
    @Autowired TaskProgressEventRepository events;
    @Autowired TaskExecutionService executionService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void revokedReadPermissionClosesTheStreamBeforeNewTaskEventsAreSent() throws Exception {
        var created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Revocation\",\"taskType\":\"system.noop\",\"cronExpression\":\"0 0 0 1 1 ? 2099\"}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated()).andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.taskId");
        var stream = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/events", taskId)
                        .accept(MediaType.TEXT_EVENT_STREAM).with(admin("enterprise.tasks.read")))
                .andExpect(request().asyncStarted()).andReturn();
        jdbc.update("insert into summer_authorization_user_binding (binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) values (?,?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                java.util.UUID.randomUUID().toString(), "ent-acme", "task-admin", "[]",
                "[\"GET:/api/v1/enterprises/{enterpriseId}/tasks/{taskId}/events\"]");
        executionService.execute(taskId, "revocation-fire");
        stream.getAsyncResult(3000);
        org.assertj.core.api.Assertions.assertThat(stream.getResponse().getContentAsString())
                .doesNotContain("execution.started", "execution.succeeded");
    }

    @Test
    void invalidCursorReturnsJsonFailureForAnEventStreamClient() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/task-1/events")
                        .header("Last-Event-ID", "-1").accept(MediaType.TEXT_EVENT_STREAM)
                        .with(admin("enterprise.tasks.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("SUMMER_TASK_SSE_LAST_EVENT_ID"));
    }

    @Test
    void taskExecutionPublishesProgressWithoutSensitiveParameters() throws Exception {
        var created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Progress\",\"taskType\":\"system.fail\",\"cronExpression\":\"0/5 * * * * ?\",\"parameters\":{\"secret\":\"do-not-stream\"},\"maxRetries\":1}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated()).andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.taskId");

        mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks/{id}:trigger", taskId)
                        .header("Idempotency-Key", "progress-trigger")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isAccepted());

        for (int attempt = 0; attempt < 40; attempt++) {
            if (events.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc("ent-acme", taskId).stream()
                    .anyMatch(event -> "execution.failed".equals(event.eventType()))) break;
            Thread.sleep(25);
        }
        var published = events.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc("ent-acme", taskId);
        org.assertj.core.api.Assertions.assertThat(published).extracting(TaskProgressEventEntity::eventType)
                .contains("execution.started", "execution.attempt", "execution.log", "execution.failed");
        org.assertj.core.api.Assertions.assertThat(published).allMatch(event -> !event.payloadJson().contains("do-not-stream"));
        org.assertj.core.api.Assertions.assertThat(published).isSortedAccordingTo(java.util.Comparator.comparing(TaskProgressEventEntity::eventId));
    }

    @Test
    void progressStreamRequiresCapabilityAndTenantMembership() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/task-1/events").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        mockMvc.perform(get("/api/v1/enterprises/ent-other/tasks/task-1/events").with(admin("enterprise.tasks.read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void progressStreamUsesSseAndAcceptsLastEventIdForReplay() throws Exception {
        var created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stream\",\"taskType\":\"system.noop\",\"cronExpression\":\"0/5 * * * * ?\"}")
                        .with(admin("enterprise.tasks.write")))
                .andExpect(status().isCreated()).andReturn();
        String taskId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.taskId");
        var result = mockMvc.perform(get("/api/v1/enterprises/ent-acme/tasks/{id}/events", taskId)
                        .header("Last-Event-ID", "17")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(admin("enterprise.tasks.read")))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getRequest().getAsyncContext()).isNotNull();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String capability) {
        return TaskAuthenticationFixture.cookie(capability);
    }
}
