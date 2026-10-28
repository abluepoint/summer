package com.abluepoint.summer.sample.task.domain;


import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Application-owned task implementation, registered as a Spring bean.
 * Reserved parameters are supplied by the scheduler: _summerTaskId,
 * _summerTaskEnterpriseId and _summerTaskIdempotencyKey. The business handler
 * must atomically deduplicate its side effect using that key and tenant.
 */
@org.springframework.modulith.NamedInterface("handlers")
public interface RegisteredTaskHandler {
    String taskType();
    void execute(Map<String, Object> parameters, IntConsumer progress);
}
