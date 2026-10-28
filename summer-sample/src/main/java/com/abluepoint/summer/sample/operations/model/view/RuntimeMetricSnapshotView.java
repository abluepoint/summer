package com.abluepoint.summer.sample.operations.model.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RuntimeMetricSnapshotView(
        int registeredMeters,
        List<String> meterNames,
        Map<String, Double> samples,
        RuntimeHttpMetricsView http,
        RuntimeModuleMetricsView audit,
        RuntimeModuleMetricsView authentication,
        RuntimeModuleMetricsView cache,
        RuntimeModuleMetricsView realtime
) {
}
