package com.abluepoint.summer.sample.resilience.model.view;

import java.time.Instant;
import java.util.List;

public record ResilienceOperationView(String name, String circuitBreaker, int retryMaxAttempts,
                               int rateLimitForPeriod, int bulkheadMaxConcurrent,
                               long calls, long successes, long failures, long fallbacks,
                               long rejected, long timedOut, long retryAttempts, Instant lastFailureAt) {}
