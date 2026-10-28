package com.abluepoint.summer.sample.task.application;


import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TaskCreateCommand(String name, String taskType, String cronExpression,
                         Map<String, Object> parameters, Integer maxRetries, Long timeoutMs) {
    public TaskCreateCommand(String name, String taskType, String cronExpression, Map<String, Object> parameters, Integer maxRetries) {
        this(name, taskType, cronExpression, parameters, maxRetries, 30_000L);
    }
}
