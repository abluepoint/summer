package com.abluepoint.summer.sample.operations.model.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RuntimeDiagnosticsView(
        String requestId,
        String traceId,
        Instant checkedAt,
        List<RuntimeComponentHealthView> components,
        RuntimeMetricSnapshotView metrics
) {
}
