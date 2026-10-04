package com.abluepoint.summer.sample.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientReadServiceTest {
    @Test
    void retries_and_uses_a_bounded_fallback_for_read_failures() {
        ResilienceProperties p = new ResilienceProperties();
        p.setRetryWait(Duration.ZERO);
        p.setTimeout(Duration.ofSeconds(5));
        p.setFallbackTtl(Duration.ofMinutes(1));
        ResilientReadService service = service(p);
        AtomicInteger attempts = new AtomicInteger();
        assertThat(service.read(ResilientReadService.PARAMETERS, "key", () -> {
            if (attempts.incrementAndGet() < 2) throw new IllegalStateException("temporary");
            return "v1";
        })).isEqualTo("v1");
        String fallback = service.read(ResilientReadService.PARAMETERS, "key", () -> { throw new IllegalStateException("down"); });
        assertThat(fallback).isEqualTo("v1");
        assertThat(service.snapshot().getFirst().fallbacks()).isEqualTo(1);
    }

    @Test
    void fails_closed_after_fallback_expiry() {
        ResilienceProperties p = new ResilienceProperties();
        p.setRetryWait(Duration.ZERO);
        p.setFallbackTtl(Duration.ZERO);
        ResilientReadService service = service(p);
        assertThat(service.read(ResilientReadService.DICTIONARIES, "code", () -> "v1")).isEqualTo("v1");
        assertThatThrownBy(() -> service.read(ResilientReadService.DICTIONARIES, "code", () -> { throw new IllegalStateException("down"); }))
                .isInstanceOf(DependencyDegradedException.class);
    }

    private static ResilientReadService service(ResilienceProperties p) {
        return new ResilientReadService(p, CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(),
                TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(p.getTimeout()).build()));
    }
}
