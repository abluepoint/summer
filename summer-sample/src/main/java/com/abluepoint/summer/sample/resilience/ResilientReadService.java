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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

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
    private final ConcurrentMap<String, Object> fallbackValues = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> fallbackTimes = new ConcurrentHashMap<>();
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
    }

    public <T> T read(String operation, String cacheKey, Supplier<T> loader) {
        if (!properties.isEnabled()) return remember(operation, cacheKey, loader.get());
        Counters stats = counters.computeIfAbsent(operation, ignored -> new Counters());
        stats.calls++;
        try {
            CircuitBreaker breaker = breakers.circuitBreaker(operation);
            Retry retry = retries.retry(operation);
            RateLimiter limiter = limiters.rateLimiter(operation);
            Bulkhead bulkhead = bulkheads.bulkhead(operation);
            TimeLimiter timer = timers.timeLimiter(operation);
            Supplier<T> decorated = loader;
            decorated = CircuitBreaker.decorateSupplier(breaker, decorated);
            decorated = Retry.decorateSupplier(retry, decorated);
            decorated = Bulkhead.decorateSupplier(bulkhead, decorated);
            decorated = RateLimiter.decorateSupplier(limiter, decorated);
            Supplier<T> finalSupplier = decorated;
            CompletableFuture<T> stage = CompletableFuture.supplyAsync(finalSupplier, executor);
            T value;
            try {
                value = timer.executeFutureSupplier(() -> stage);
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
            stats.successes++;
            return remember(operation, cacheKey, value);
        } catch (RuntimeException failure) {
            stats.failures++;
            if (isRejected(failure)) stats.rejected++;
            if (isTimeout(failure)) stats.timedOut++;
            stats.lastFailureAt = Instant.now();
            T fallback = fallback(operation, cacheKey);
            if (fallback != null) {
                stats.fallbacks++;
                return fallback;
            }
            throw new DependencyDegradedException("Dependency read is unavailable", failure);
        }
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
                    stats.calls, stats.successes, stats.failures, stats.fallbacks, stats.rejected, stats.timedOut,
                    stats.lastFailureAt));
        });
        return result.stream().sorted(Comparator.comparing(ResilienceOperationView::name)).toList();
    }

    private <T> T remember(String operation, String cacheKey, T value) {
        if (value != null) {
            String key = operation + ":" + cacheKey;
            fallbackValues.put(key, value);
            fallbackTimes.put(key, Instant.now());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T fallback(String operation, String cacheKey) {
        String key = operation + ":" + cacheKey;
        Instant stored = fallbackTimes.get(key);
        if (stored == null || stored.plus(properties.getFallbackTtl()).isBefore(Instant.now())) {
            fallbackValues.remove(key);
            fallbackTimes.remove(key);
            return null;
        }
        return (T) fallbackValues.get(key);
    }

    private static boolean isRejected(Throwable failure) {
        return failure.getClass().getName().contains("RequestNotPermitted")
                || failure.getClass().getName().contains("BulkheadFull")
                || failure.getClass().getName().contains("CallNotPermitted");
    }

    private static boolean isTimeout(Throwable failure) {
        return failure instanceof java.util.concurrent.TimeoutException
                || failure.getCause() instanceof java.util.concurrent.TimeoutException
                || failure.getClass().getName().contains("Timeout");
    }

    private static final class Counters {
        private long calls;
        private long successes;
        private long failures;
        private long fallbacks;
        private long rejected;
        private long timedOut;
        private Instant lastFailureAt;
    }
}
