package com.abluepoint.summer.sample.operations.model.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RuntimeHttpMetricsView(String status, String window, Instant processStartedAt, long requests,
                          long clientErrors, long serverErrors, Double meanLatencyMs, Double errorRate) { }
