package com.abluepoint.summer.sample.resilience;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
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
        }, () -> true)).isEqualTo("v1");
        assertThat(attempts).hasValue(2);
        assertThat(service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS))
                .findFirst().orElseThrow().retryAttempts()).isEqualTo(1);
        String fallback = service.read(ResilientReadService.PARAMETERS, "key", () -> { throw new IllegalStateException("down"); }, () -> true);
        assertThat(fallback).isEqualTo("v1");
        assertThat(service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS))
                .findFirst().orElseThrow().retryAttempts()).isEqualTo(2);
        assertThat(service.snapshot().stream()
                .filter(view -> view.name().equals(ResilientReadService.PARAMETERS))
                .findFirst().orElseThrow().fallbacks()).isEqualTo(1);
    }

    @Test
    void fails_closed_after_fallback_expiry() {
        ResilienceProperties p = new ResilienceProperties();
        p.setRetryWait(Duration.ZERO);
        p.setFallbackTtl(Duration.ZERO);
        ResilientReadService service = service(p);
        assertThat(service.read(ResilientReadService.DICTIONARIES, "code", () -> "v1", () -> true)).isEqualTo("v1");
        assertThatThrownBy(() -> service.read(ResilientReadService.DICTIONARIES, "code", () -> { throw new IllegalStateException("down"); }, () -> true))
                .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void missingOrFailedProofAndUnknownOperationNeverUseFallback() {
        var service = service(new ResilienceProperties());
        service.read(ResilientReadService.PARAMETERS, "key", () -> "v1", () -> true);
        assertThatThrownBy(() -> service.read(ResilientReadService.PARAMETERS, "key", () -> { throw new IllegalStateException(); }))
                .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE));
        assertThatThrownBy(() -> service.read(ResilientReadService.PARAMETERS, "key", () -> { throw new IllegalStateException(); }, () -> { throw new IllegalStateException("policy offline"); }))
                .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE));
        AtomicInteger writes = new AtomicInteger();
        assertThatThrownBy(() -> service.read("write.operation", "key", writes::incrementAndGet, () -> true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(writes).hasValue(0);
    }

    @Test
    void authorizationFailureInvalidatesEvenVerifiedOldEntry() {
        var service = service(new ResilienceProperties());
        service.read(ResilientReadService.PARAMETERS, "key", () -> "v1", () -> true);
        assertThatThrownBy(() -> service.read(ResilientReadService.PARAMETERS, "key", () -> { throw new org.springframework.security.access.AccessDeniedException("denied"); }, () -> true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.read(ResilientReadService.PARAMETERS, "key", () -> { throw new IllegalStateException(); }, () -> true))
                .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE));
    }

    @Test
    void timeoutAndLateCompletionCannotLeakAcrossKeysOrReplaceFallback() throws Exception {
        var p = new ResilienceProperties();
        p.setTimeout(Duration.ofMillis(150));
        var service = service(p);
        service.read(ResilientReadService.PARAMETERS, "alice:v1", () -> "alice", () -> true);
        var release = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(1);
        try {
            assertThat(service.read(ResilientReadService.PARAMETERS, "alice:v1", () -> {
                try { release.await(); return "late"; }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                finally { finished.countDown(); }
            }, () -> true)).isEqualTo("alice");
            assertThatThrownBy(() -> service.read(ResilientReadService.PARAMETERS, "bob:v1", () -> { throw new IllegalStateException(); }, () -> true))
                    .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE));
        } finally { release.countDown(); }
        assertThat(finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(service.<String>read(ResilientReadService.PARAMETERS, "alice:v1", () -> { throw new IllegalStateException(); }, () -> true)).isEqualTo("alice");
        assertThat(service.read(ResilientReadService.PARAMETERS, "alice:v2", () -> "recovered", () -> true)).isEqualTo("recovered");
        assertThat(service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS)).findFirst().orElseThrow().timedOut()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void concurrentBulkheadRejectionIsCountedAndDoesNotBlockOtherOperations() throws Exception {
        var p = new ResilienceProperties();
        p.setBulkhead(1);
        p.setCircuitWindow(2);
        p.setMaxAttempts(1);
        p.setTimeout(Duration.ofSeconds(5));
        var service = service(p);
        service.read(ResilientReadService.PARAMETERS, "warm", () -> "safe", () -> true);
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var occupied = java.util.concurrent.CompletableFuture.supplyAsync(() -> service.read(ResilientReadService.PARAMETERS, "busy", () -> {
            started.countDown();
            try { release.await(); return "done"; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }, () -> true));
        try {
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 3; i++) {
                assertThat(service.read(ResilientReadService.PARAMETERS, "warm", () -> "unexpected", () -> true)).isEqualTo("safe");
            }
            assertThat(service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS))
                    .findFirst().orElseThrow().circuitBreaker()).isEqualTo("CLOSED");
            assertThat(service.read(ResilientReadService.DICTIONARIES, "independent", () -> "dictionary", () -> true)).isEqualTo("dictionary");
            assertThat(service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS)).findFirst().orElseThrow().rejected()).isEqualTo(3);
        } finally { release.countDown(); }
        assertThat(occupied.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("done");
    }

    @Test
    void callerTimeoutsOpenCircuitEvenWhenDependencyEventuallySucceeds() throws Exception {
        var p = new ResilienceProperties();
        p.setTimeout(Duration.ofMillis(100));
        p.setMaxAttempts(1);
        p.setCircuitWindow(2);
        p.setCircuitOpenWait(Duration.ofMinutes(1));
        p.setBulkhead(2);
        var service = service(p);
        var release = new java.util.concurrent.CountDownLatch(1);
        var finished = new java.util.concurrent.CountDownLatch(2);
        var calls = new AtomicInteger();
        java.util.function.Supplier<String> slow = () -> {
            calls.incrementAndGet();
            try { release.await(); return "late success"; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            finally { finished.countDown(); }
        };
        try {
            for (int i = 0; i < 2; i++) {
                assertThatThrownBy(() -> service.read(ResilientReadService.PARAMETERS, "cold", slow))
                        .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE));
            }
            var view = service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS)).findFirst().orElseThrow();
            assertThat(view.timedOut()).isEqualTo(2);
            assertThat(view.circuitBreaker()).isEqualTo("OPEN");
            assertThatThrownBy(() -> service.read(ResilientReadService.PARAMETERS, "cold", slow))
                    .isInstanceOfSatisfying(SummerException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE));
            assertThat(calls).hasValue(2);
        } finally { release.countDown(); }
        assertThat(finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS)).findFirst().orElseThrow().circuitBreaker()).isEqualTo("OPEN");
    }

    @Test
    void localRateRejectionsDoNotOpenDependencyCircuit() {
        var p = new ResilienceProperties();
        p.setCircuitWindow(2);
        p.setRateLimit(1);
        p.setRateLimitPeriod(Duration.ofMinutes(1));
        p.setTimeout(Duration.ofSeconds(5));
        var service = service(p);
        assertThat(service.read(ResilientReadService.PARAMETERS, "key", () -> "safe", () -> true)).isEqualTo("safe");
        for (int i = 0; i < 3; i++) {
            assertThat(service.read(ResilientReadService.PARAMETERS, "key", () -> "must not run", () -> true)).isEqualTo("safe");
        }
        var view = service.snapshot().stream().filter(v -> v.name().equals(ResilientReadService.PARAMETERS)).findFirst().orElseThrow();
        assertThat(view.rejected()).isEqualTo(3);
        assertThat(view.circuitBreaker()).isEqualTo("CLOSED");
    }

    private static ResilientReadService service(ResilienceProperties p) {
        var configuration = new ResilienceConfiguration();
        return new ResilientReadService(p, configuration.resilienceCircuitBreakers(p), configuration.resilienceRetries(p),
                configuration.resilienceRateLimiters(p), configuration.resilienceBulkheads(p), configuration.resilienceTimeLimiters(p));
    }
}
