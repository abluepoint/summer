package com.abluepoint.summer.sample.operations.model.view;

import java.time.Instant;

public record RedisDiagnosticView(
        String component,
        RedisDiagnosticStatus status,
        String endpoint,
        String reason,
        String message,
        long latencyMs,
        RedisRuntimeMetricsView metrics,
        Instant checkedAt
) {
}
