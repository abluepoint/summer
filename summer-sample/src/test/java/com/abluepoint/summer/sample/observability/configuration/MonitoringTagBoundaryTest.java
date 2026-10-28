package com.abluepoint.summer.sample.observability.configuration;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringTagBoundaryTest {
    @Test
    void keyFilteringDoesNotClaimToBoundFutureCustomNameValues() {
        var registry = new SimpleMeterRegistry();
        try {
            registry.config().meterFilter(new ObservabilityConfiguration().monitoringTagBoundary());
            registry.counter("custom.example", "name", "first");
            registry.counter("custom.example", "name", "second");
            assertThat(registry.getMeters()).hasSize(2);
        } finally { registry.close(); }
    }

    @Test
    void perRequestIdentifiersAndPathsDoNotCreateMetricSeries() {
        var registry = new SimpleMeterRegistry();
        try {
            registry.config().meterFilter(new ObservabilityConfiguration().monitoringTagBoundary());
            var first = registry.counter("http.test", "status", "200", "uri", "/users/first",
                    "principal", "first", "traceId", "first", "requestId", "first", "token", "first");
            var second = registry.counter("http.test", "status", "200", "uri", "/users/second",
                    "principal", "second", "traceId", "second", "requestId", "second", "token", "second");
            assertThat(first).isSameAs(second);
            assertThat(first.getId().getTags()).extracting(io.micrometer.core.instrument.Tag::getKey)
                    .containsExactly("status");
        } finally { registry.close(); }
    }
}
