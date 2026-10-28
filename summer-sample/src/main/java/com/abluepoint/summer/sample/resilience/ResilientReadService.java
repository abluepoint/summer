package com.abluepoint.summer.sample.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

@Service
public class ResilientReadService {
    public static final String PARAMETERS = "system-parameters.read";
    public static final String DICTIONARIES = "dictionaries.read";

    private final ResilienceProperties properties;
    private final CircuitBreakerRegistry breakers;
    private final RetryRegistry retries;
    private final RateLimiterRegistry limiters;
    private final BulkheadRegistry bulkheads;
    private final TimeLimiterRegistry timers;
    private final Executor executor = ForkJoinPool.commonPool();
    private record Entry(Object value, Instant storedAt) {}
    private final ConcurrentMap<String, Entry> fallbackValues = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counters> counters = new ConcurrentHashMap<>();

    public ResilientReadService(ResilienceProperties properties, CircuitBreakerRegistry breakers,
                                RetryRegistry retries, RateLimiterRegistry limiters,
                                BulkheadRegistry bulkheads, TimeLimiterRegistry timers) {
        this.properties = properties;
        this.breakers = breakers;
        this.retries = retries;
        this.limiters = limiters;
        this.bulkheads = bulkheads;
        this.timers = timers;
        for (String operation : List.of(PARAMETERS, DICTIONARIES)) {
            Counters stats = new Counters();
            counters.put(operation, stats);
            retries.retry(operation).getEventPublisher().onRetry(event -> stats.retryAttempts.incrementAndGet());
        }
    }

    public <T> T read(String operation, String cacheKey, Supplier<T> loader) {
        return read(operation, cacheKey, loader, null);
    }

    /** Proof must re-read authoritative permissions and resource state outside any stale transaction. */
    @SuppressWarnings("unchecked")
    public <T> T read(String operation, String cacheKey, Supplier<T> loader, BooleanSupplier proof) {
        if (!PARAMETERS.equals(operation) && !DICTIONARIES.equals(operation))
            throw new IllegalArgumentException("Only parameter and dictionary reads support resilience");
        boolean eligible = proof != null;
        if (!properties.isEnabled()) return loader.get();
        Counters stats = counters.computeIfAbsent(operation, ignored -> new Counters());
        stats.calls.incrementAndGet();
        try {
            CircuitBreaker breaker = breakers.circuitBreaker(operation);
            Retry retry = retries.retry(operation);
            RateLimiter limiter = limiters.rateLimiter(operation);
            Bulkhead bulkhead = bulkheads.bulkhead(operation);
            TimeLimiter timer = timers.timeLimiter(operation);
            Supplier<T> decorated = loader;
            decorated = Retry.decorateSupplier(retry, decorated);
            decorated = Bulkhead.decorateSupplier(bulkhead, decorated);
            decorated = RateLimiter.decorateSupplier(limiter, decorated);
            Supplier<T> finalSupplier = decorated;
            // Record the caller-visible outcome, including timeout, once per logical read.
            // Admission remains inside the physical call so timeout cannot free its bulkhead early.
            Supplier<T> timedRead = () -> {
                CompletableFuture<T> stage = CompletableFuture.supplyAsync(finalSupplier, executor);
                try {
                    return timer.executeFutureSupplier(() -> stage);
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            };
            T value = CircuitBreaker.decorateSupplier(breaker, timedRead).get();
            stats.successes.incrementAndGet();
            return eligible ? remember(operation, cacheKey, value) : value;
        } catch (RuntimeException failure) {
            Throwable cause = unwrap(failure);
            if (isBusinessFailure(cause)) {
                // A withdrawal or authorization decision must invalidate old success data.
                fallbackValues.remove(operation + ":" + cacheKey);
                throw (RuntimeException) cause;
            }
            stats.failures.incrementAndGet();
            if (isRejected(cause)) stats.rejected.incrementAndGet();
            if (isTimeout(cause)) stats.timedOut.incrementAndGet();
            stats.lastFailureAt = Instant.now();
            Entry fallback = eligible ? fallback(operation, cacheKey) : null;
            if (fallback != null) {
                try {
                    if (!proof.getAsBoolean() || !fallback.storedAt().plus(properties.getFallbackTtl()).isAfter(Instant.now())) fallback = null;
                } catch (RuntimeException verificationFailure) {
                    fallbackValues.remove(operation + ":" + cacheKey);
                    if (isBusinessFailure(verificationFailure)) throw verificationFailure;
                    throw new DependencyDegradedException("Fallback cannot be verified", verificationFailure);
                }
                if (fallback == null) fallbackValues.remove(operation + ":" + cacheKey);
            }
            if (fallback != null) {
                stats.fallbacks.incrementAndGet();
                return (T) fallback.value();
            }
            throw new DependencyDegradedException("Dependency read is unavailable", failure);
        }
    }

    static boolean isBusinessFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof org.springframework.security.access.AccessDeniedException
                || (cause instanceof com.abluepoint.summer.common.error.api.SummerException business
                && business.errorCode().category() != com.abluepoint.summer.common.error.api.ErrorCategory.INTERNAL_ERROR);
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof java.util.concurrent.CompletionException
                || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    public List<ResilienceOperationView> snapshot() {
        List<ResilienceOperationView> result = new ArrayList<>();
        counters.forEach((name, stats) -> {
            CircuitBreaker breaker = breakers.circuitBreaker(name);
            Retry retry = retries.retry(name);
            RateLimiter limiter = limiters.rateLimiter(name);
            Bulkhead bulkhead = bulkheads.bulkhead(name);
            result.add(new ResilienceOperationView(name, breaker.getState().name(), retry.getRetryConfig().getMaxAttempts(),
                    limiter.getRateLimiterConfig().getLimitForPeriod(), bulkhead.getBulkheadConfig().getMaxConcurrentCalls(),
                    stats.calls.get(), stats.successes.get(), stats.failures.get(), stats.fallbacks.get(), stats.rejected.get(), stats.timedOut.get(), stats.retryAttempts.get(),
                    stats.lastFailureAt));
        });
        return result.stream().sorted(Comparator.comparing(ResilienceOperationView::name)).toList();
    }

    private <T> T remember(String operation, String cacheKey, T value) {
        if (value != null) {
            String key = operation + ":" + cacheKey;
            fallbackValues.put(key, new Entry(value, Instant.now()));
        }
        return value;
    }

    private Entry fallback(String operation, String cacheKey) {
        String key = operation + ":" + cacheKey;
        Entry entry = fallbackValues.get(key);
        if (entry == null) return null;
        if (!entry.storedAt().plus(properties.getFallbackTtl()).isAfter(Instant.now())) {
            fallbackValues.remove(key, entry);
            return null;
        }
        return entry;
    }

    static boolean isRejected(Throwable failure) {
        Throwable cause = unwrap(failure);
        return cause instanceof io.github.resilience4j.ratelimiter.RequestNotPermitted
                || cause instanceof io.github.resilience4j.bulkhead.BulkheadFullException
                || cause instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException;
    }

    private static boolean isTimeout(Throwable failure) {
        return failure instanceof java.util.concurrent.TimeoutException
                || failure.getCause() instanceof java.util.concurrent.TimeoutException
                || failure.getClass().getName().contains("Timeout");
    }

    private static final class Counters {
        private final java.util.concurrent.atomic.AtomicLong calls = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong successes = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong failures = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong fallbacks = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong rejected = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong timedOut = new java.util.concurrent.atomic.AtomicLong();
        private final java.util.concurrent.atomic.AtomicLong retryAttempts = new java.util.concurrent.atomic.AtomicLong();
        private volatile Instant lastFailureAt;
    }
}
