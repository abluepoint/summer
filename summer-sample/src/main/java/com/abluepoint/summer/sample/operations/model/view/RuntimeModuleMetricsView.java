package com.abluepoint.summer.sample.operations.model.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RuntimeModuleMetricsView(String status, String window, Instant checkedAt, Map<String, Double> values) { }
