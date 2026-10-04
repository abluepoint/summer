package com.abluepoint.summer.sample.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
class TaskExecutionService {
    private final TaskDefinitionRepository definitions;
    private final TaskExecutionRepository executions;
    private final TrustedTaskRegistry registry;

    TaskExecutionService(TaskDefinitionRepository definitions, TaskExecutionRepository executions, TrustedTaskRegistry registry) {
        this.definitions = definitions; this.executions = executions; this.registry = registry;
    }

    @Transactional
    void execute(String taskId, String fireInstanceId) {
        TaskDefinitionEntity task = definitions.findById(taskId).orElseThrow(TaskNotFoundException::new);
        TaskExecutionEntity execution = executions.findByTaskIdAndFireInstanceId(taskId, fireInstanceId)
                .orElseGet(() -> executions.save(new TaskExecutionEntity(UUID.randomUUID().toString(), task.taskId(), task.enterpriseId(), fireInstanceId)));
        if (execution.status() != TaskExecutionStatus.RUNNING) return;
        Map<String, Object> parameters = readParameters(task.parametersJson());
        for (int attempt = 1; attempt <= task.maxRetries() + 1; attempt++) {
            execution.attempt(attempt);
            try {
                registry.execute(task.taskType(), parameters);
                execution.success();
                executions.saveAndFlush(execution);
                return;
            } catch (RuntimeException exception) {
                // Persist only a stable, bounded failure code; no exception text or parameters cross the API/log boundary.
                if (attempt == task.maxRetries() + 1) execution.failure("TASK_HANDLER_FAILED");
            }
        }
        executions.saveAndFlush(execution);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readParameters(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }
}
