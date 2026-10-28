package com.abluepoint.summer.sample.operations.model.view;

import java.time.Instant;

public record RedisRuntimeMetricsView(
        Long uptimeSeconds,
        Long connectedClients,
        Long usedMemoryBytes,
        Long totalCommandsProcessed
) {
}
