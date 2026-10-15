package com.abluepoint.summer.sample.resilience;

import java.time.Instant;
import java.util.List;

record ResilienceDiagnosticsView(String requestId, Instant checkedAt, List<ResilienceOperationView> operations) {}

record ResilienceOperationView(String name, String circuitBreaker, int retryMaxAttempts,
                               int rateLimitForPeriod, int bulkheadMaxConcurrent,
                               long calls, long successes, long failures, long fallbacks,
                               long rejected, long timedOut, long retryAttempts, Instant lastFailureAt) {}

final class DependencyDegradedException extends RuntimeException {
    DependencyDegradedException(String message, Throwable cause) { super(message, cause); }
}
