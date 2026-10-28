package com.abluepoint.summer.sample.task.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.task.application.TaskCreateCommand;
public record TaskCreateRequest(String name, String taskType, String cronExpression,
                         Map<String, Object> parameters, Integer maxRetries, Long timeoutMs) { public TaskCreateCommand toCommand() { return new TaskCreateCommand(name,taskType,cronExpression,parameters,maxRetries,timeoutMs); } }
