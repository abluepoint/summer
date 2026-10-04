package com.abluepoint.summer.sample.task;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
final class TrustedTaskRegistry {
    private final Map<String, TrustedTaskHandler> handlers = Map.of(
            "system.noop", (parameters) -> { },
            "system.parameter-events", (parameters) -> { },
            // Deterministic handlers used to exercise retry and timeout state transitions.
            "system.fail", (parameters) -> { throw new IllegalStateException("task handler failed"); },
            "system.sleep", (parameters) -> {
                Object millis = parameters.get("millis");
                try { Thread.sleep(millis instanceof Number n ? n.longValue() : 60_000L); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
    );

    boolean supports(String taskType) { return taskType != null && handlers.containsKey(taskType); }
    void execute(String taskType, Map<String, Object> parameters) {
        TrustedTaskHandler handler = handlers.get(taskType);
        if (handler == null) throw new TaskUntrustedTypeException(taskType);
        handler.execute(parameters == null ? Map.of() : Map.copyOf(parameters));
    }
    List<TrustedTaskTypeView> types() {
        return handlers.keySet().stream().sorted().map(type -> new TrustedTaskTypeView(type, "Registered Summer task handler")).toList();
    }
    @FunctionalInterface interface TrustedTaskHandler { void execute(Map<String, Object> parameters); }
}
