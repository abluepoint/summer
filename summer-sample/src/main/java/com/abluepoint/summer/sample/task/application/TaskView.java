package com.abluepoint.summer.sample.task.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record TaskView(String taskId, String enterpriseId, String name, String taskType,
                String cronExpression, Map<String, Object> parameters, int maxRetries,
                long timeoutMs, String status, long version, Instant createdAt, Instant updatedAt) {}
