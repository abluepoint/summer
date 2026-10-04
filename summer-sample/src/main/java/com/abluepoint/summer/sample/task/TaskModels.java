package com.abluepoint.summer.sample.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record TaskCreateRequest(String name, String taskType, String cronExpression,
                         Map<String, Object> parameters, Integer maxRetries) {}
record TaskUpdateRequest(String name, String cronExpression, Map<String, Object> parameters,
                         Integer maxRetries) {}
record TaskView(String taskId, String enterpriseId, String name, String taskType,
                String cronExpression, Map<String, Object> parameters, int maxRetries,
                String status, long version, Instant createdAt, Instant updatedAt) {}
record TaskExecutionView(String executionId, String taskId, String enterpriseId,
                         String fireInstanceId, String status, int attempt,
                         Instant startedAt, Instant finishedAt, String failureCode) {}
record TaskExecutionPage(List<TaskExecutionView> items, long totalElements) {}
record TrustedTaskTypeView(String taskType, String description) {}
