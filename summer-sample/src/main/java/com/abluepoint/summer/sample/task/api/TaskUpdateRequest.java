package com.abluepoint.summer.sample.task.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.task.application.TaskUpdateCommand;
public record TaskUpdateRequest(String name, String cronExpression, Map<String, Object> parameters,
                         Integer maxRetries, Long timeoutMs) { public TaskUpdateCommand toCommand() { return new TaskUpdateCommand(name,cronExpression,parameters,maxRetries,timeoutMs); } }
