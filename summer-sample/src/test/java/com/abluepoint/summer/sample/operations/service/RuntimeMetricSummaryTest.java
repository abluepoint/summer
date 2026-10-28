package com.abluepoint.summer.sample.operations.service;

import com.abluepoint.summer.sample.observability.service.AuditObservation;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.IndicatedHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RuntimeMetricSummaryTest {
    @Test
    void computesWeightedMeanAndBothErrorClassesFromCompletedTimerCounts() {
        var registry = new SimpleMeterRegistry();
        try {
            Timer success = registry.timer("http.server.requests", "status", "200");
            success.record(100, TimeUnit.MILLISECONDS); success.record(300, TimeUnit.MILLISECONDS);
            registry.timer("http.server.requests", "status", "403").record(100, TimeUnit.MILLISECONDS);
            registry.timer("http.server.requests", "status", "500").record(700, TimeUnit.MILLISECONDS);
            var metrics = service(registry).snapshot().metrics().http();
            assertThat(metrics.requests()).isEqualTo(4);
            assertThat(metrics.clientErrors()).isEqualTo(1);
            assertThat(metrics.serverErrors()).isEqualTo(1);
            assertThat(metrics.meanLatencyMs()).isEqualTo(300);
            assertThat(metrics.errorRate()).isEqualTo(0.5);
            assertThat(metrics.window()).isEqualTo("PROCESS_LIFETIME");
        } finally { registry.close(); }
    }

    @Test
    void anEmptyProcessHasNoInventedLatencyOrErrorRate() {
        var registry = new SimpleMeterRegistry();
        try {
            var data = service(registry).snapshot();
            assertThat(data.metrics().http().status()).isEqualTo("NO_DATA");
            assertThat(data.metrics().http().meanLatencyMs()).isNull();
            assertThat(data.metrics().http().errorRate()).isNull();
            assertThat(data.metrics().cache().status()).isEqualTo("NOT_CONNECTED");
            assertThat(data.metrics().realtime().values()).isEmpty();
        } finally { registry.close(); }
    }

    @SuppressWarnings("unchecked")
    private RuntimeDiagnosticsService service(SimpleMeterRegistry registry) {
        var health = mock(HealthEndpoint.class);
        var descriptor = mock(IndicatedHealthDescriptor.class);
        when(descriptor.getStatus()).thenReturn(Status.UP);
        when(health.health()).thenReturn(descriptor);
        var audit = mock(AuditObservation.class);
        when(audit.snapshot()).thenReturn(new AuditObservation.Snapshot(true, 0, 0, 0, Instant.now()));
        return new RuntimeDiagnosticsService(health, registry, () -> "metric-test",
                new MockEnvironment().withProperty("spring.flyway.enabled", "false"),
                mock(org.springframework.beans.factory.ObjectProvider.class), audit);
    }
}
