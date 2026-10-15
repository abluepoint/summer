package com.abluepoint.summer.sample.operations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record RuntimeDiagnosticsView(
        String requestId,
        String traceId,
        Instant checkedAt,
        List<RuntimeComponentHealth> components,
        RuntimeMetricSnapshot metrics
) {
}

record RuntimeComponentHealth(String name, String status) {
}

record RuntimeMetricSnapshot(
        int registeredMeters,
        List<String> meterNames,
        Map<String, Double> samples,
        RuntimeHttpMetrics http,
        RuntimeModuleMetrics audit,
        RuntimeModuleMetrics authentication,
        RuntimeModuleMetrics cache,
        RuntimeModuleMetrics realtime
) {
}

record RuntimeHttpMetrics(String status, String window, Instant processStartedAt, long requests,
                          long clientErrors, long serverErrors, Double meanLatencyMs, Double errorRate) { }

record RuntimeModuleMetrics(String status, String window, Instant checkedAt, Map<String, Double> values) { }
