package com.abluepoint.summer.sample.task.infrastructure;
import com.abluepoint.summer.sample.task.application.TrustedTaskTypeView;
import com.abluepoint.summer.sample.task.domain.RegisteredTaskHandler;
import com.abluepoint.summer.sample.task.domain.TaskUntrustedTypeException;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@Component
public final class TrustedTaskRegistry {
    private final Map<String, TrustedTaskHandler> handlers;

    public TrustedTaskRegistry() { this(List.of()); }

    @Autowired
    public TrustedTaskRegistry(List<RegisteredTaskHandler> registered) {
        Map<String, TrustedTaskHandler> values = new java.util.LinkedHashMap<>(Map.of(
            "system.noop", (parameters, progress) -> { },
            "system.parameter-events", (parameters, progress) -> { },
            // Deterministic handlers used to exercise retry and timeout state transitions.
            "system.fail", (parameters, progress) -> { throw new IllegalStateException("task handler failed"); },
            "system.sleep", (parameters, progress) -> {
                Object millis = parameters.get("millis");
                long duration = millis instanceof Number n ? n.longValue() : 60_000L;
                if (duration < 0) throw new IllegalArgumentException("Task duration must be non-negative");
                long remaining = duration;
                while (remaining > 0) {
                    long interval = Math.min(1000, remaining);
                    try { Thread.sleep(interval); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                    remaining -= interval;
                    progress.accept((int) (100.0 * (duration - remaining) / duration));
                }
            }
        ));
        for (RegisteredTaskHandler handler : registered) {
            String type = handler.taskType();
            if (type == null || !type.matches("[A-Za-z][A-Za-z0-9_.-]{0,79}"))
                throw new IllegalArgumentException("Invalid registered task type");
            if (values.putIfAbsent(type, handler::execute) != null)
                throw new IllegalArgumentException("Duplicate registered task type");
        }
        handlers = Map.copyOf(values);
    }

    public boolean supports(String taskType) { return taskType != null && handlers.containsKey(taskType); }
    public void execute(String taskType, Map<String, Object> parameters) {
        execute(taskType, parameters, ignored -> { });
    }
    public void execute(String taskType, Map<String, Object> parameters, java.util.function.IntConsumer progress) {
        TrustedTaskHandler handler = handlers.get(taskType);
        if (handler == null) throw new TaskUntrustedTypeException(taskType);
        handler.execute(parameters == null ? Map.of() : Map.copyOf(parameters), progress);
    }
    public List<TrustedTaskTypeView> types() {
        return handlers.keySet().stream().sorted().map(type -> new TrustedTaskTypeView(type, "Registered Summer task handler")).toList();
    }
    @FunctionalInterface interface TrustedTaskHandler { void execute(Map<String, Object> parameters, java.util.function.IntConsumer progress); }
}
