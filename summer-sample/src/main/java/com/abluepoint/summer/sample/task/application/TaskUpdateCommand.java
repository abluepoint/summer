package com.abluepoint.summer.sample.task.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record TaskUpdateCommand(String name, String cronExpression, Map<String, Object> parameters,
                         Integer maxRetries, Long timeoutMs) {
    public TaskUpdateCommand(String name, String cronExpression, Map<String, Object> parameters, Integer maxRetries) {
        this(name, cronExpression, parameters, maxRetries, null);
    }
}
