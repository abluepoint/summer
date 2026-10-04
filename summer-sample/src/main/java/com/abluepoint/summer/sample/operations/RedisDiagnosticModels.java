package com.abluepoint.summer.sample.operations;

import java.time.Instant;

record RedisDiagnosticView(
        String component,
        RedisDiagnosticStatus status,
        String endpoint,
        String reason,
        String message,
        long latencyMs,
        RedisRuntimeMetrics metrics,
        Instant checkedAt
) {
}

record RedisRuntimeMetrics(
        Long uptimeSeconds,
        Long connectedClients,
        Long usedMemoryBytes,
        Long totalCommandsProcessed
) {
}
