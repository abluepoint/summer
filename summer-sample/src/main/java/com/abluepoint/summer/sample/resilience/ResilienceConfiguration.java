package com.abluepoint.summer.sample.resilience;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResilienceProperties.class)
class ResilienceConfiguration {
    @Bean CircuitBreakerRegistry resilienceCircuitBreakers(ResilienceProperties p) {
        CircuitBreakerConfig c = CircuitBreakerConfig.custom()
                .ignoreException(ResilientReadService::isBusinessFailure)
                .failureRateThreshold(p.getCircuitFailureRate())
                .slidingWindowSize(Math.max(2, p.getCircuitWindow()))
                .minimumNumberOfCalls(Math.max(2, p.getCircuitWindow()))
                .waitDurationInOpenState(p.getCircuitOpenWait())
                .build();
        return CircuitBreakerRegistry.of(c);
    }

    @Bean RetryRegistry resilienceRetries(ResilienceProperties p) {
        RetryConfig c = RetryConfig.custom().maxAttempts(Math.max(1, p.getMaxAttempts()))
                .retryOnException(failure -> !ResilientReadService.isBusinessFailure(failure))
                .waitDuration(p.getRetryWait()).build();
        return RetryRegistry.of(c);
    }

    @Bean RateLimiterRegistry resilienceRateLimiters(ResilienceProperties p) {
        RateLimiterConfig c = RateLimiterConfig.custom().limitRefreshPeriod(p.getRateLimitPeriod())
                .limitForPeriod(Math.max(1, p.getRateLimit())).timeoutDuration(Duration.ZERO).build();
        return RateLimiterRegistry.of(c);
    }

    @Bean BulkheadRegistry resilienceBulkheads(ResilienceProperties p) {
        BulkheadConfig c = BulkheadConfig.custom().maxConcurrentCalls(Math.max(1, p.getBulkhead()))
                .maxWaitDuration(Duration.ZERO).build();
        return BulkheadRegistry.of(c);
    }

    @Bean TimeLimiterRegistry resilienceTimeLimiters(ResilienceProperties p) {
        return TimeLimiterRegistry.of(TimeLimiterConfig.custom().timeoutDuration(p.getTimeout()).cancelRunningFuture(true).build());
    }
}
