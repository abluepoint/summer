package com.abluepoint.summer.sample.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record TaskCreateRequest(String name, String taskType, String cronExpression,
                         Map<String, Object> parameters, Integer maxRetries, Long timeoutMs) {
    TaskCreateRequest(String name, String taskType, String cronExpression, Map<String, Object> parameters, Integer maxRetries) {
        this(name, taskType, cronExpression, parameters, maxRetries, 30_000L);
    }
}
record TaskUpdateRequest(String name, String cronExpression, Map<String, Object> parameters,
                         Integer maxRetries, Long timeoutMs) {
    TaskUpdateRequest(String name, String cronExpression, Map<String, Object> parameters, Integer maxRetries) {
        this(name, cronExpression, parameters, maxRetries, null);
    }
}
record TaskView(String taskId, String enterpriseId, String name, String taskType,
                String cronExpression, Map<String, Object> parameters, int maxRetries,
                long timeoutMs, String status, long version, Instant createdAt, Instant updatedAt) {}
record TaskExecutionView(String executionId, String taskId, String enterpriseId,
                         String fireInstanceId, String status, int attempt,
                         Instant startedAt, Instant finishedAt, String failureCode,
                         String idempotencyKey, boolean recovered, List<TaskAttemptView> attempts) {}
record TaskExecutionPage(List<TaskExecutionView> items, long totalElements) {}
record TrustedTaskTypeView(String taskType, String description) {}
record TaskAttemptView(int attempt, String status, Instant startedAt, Instant finishedAt,
                       String failureCode, String nodeId) {}
