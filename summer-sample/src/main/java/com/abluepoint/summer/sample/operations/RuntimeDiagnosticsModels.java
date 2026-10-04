package com.abluepoint.summer.sample.operations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record RuntimeDiagnosticsView(
        String requestId,
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
        Map<String, Double> samples
) {
}
