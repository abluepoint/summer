package com.abluepoint.summer.sample.resilience.model.view;

import java.time.Instant;
import java.util.List;

public record ResilienceDiagnosticsView(String requestId, Instant checkedAt, List<ResilienceOperationView> operations) {}
