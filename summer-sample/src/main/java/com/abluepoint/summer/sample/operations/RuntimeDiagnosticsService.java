package com.abluepoint.summer.sample.operations;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
final class RuntimeDiagnosticsService {

    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;
    private final RequestIdAccessor requestIdAccessor;
    private final Clock clock;

    RuntimeDiagnosticsService(HealthEndpoint healthEndpoint, MeterRegistry meterRegistry,
                              RequestIdAccessor requestIdAccessor) {
        this.healthEndpoint = healthEndpoint;
        this.meterRegistry = meterRegistry;
        this.requestIdAccessor = requestIdAccessor;
        this.clock = Clock.systemUTC();
    }

    RuntimeDiagnosticsView snapshot() {
        return new RuntimeDiagnosticsView(
                requestIdAccessor.currentRequestId(),
                clock.instant(),
                components(healthEndpoint.health()),
                metrics());
    }

    private List<RuntimeComponentHealth> components(HealthDescriptor root) {
        if (!(root instanceof CompositeHealthDescriptor composite)) {
            return List.of(new RuntimeComponentHealth("application", root.getStatus().getCode()));
        }
        List<RuntimeComponentHealth> result = new ArrayList<>();
        composite.getComponents().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.add(new RuntimeComponentHealth(
                        entry.getKey(), entry.getValue().getStatus().getCode())));
        if (result.stream().noneMatch(value -> "db".equals(value.name()))) {
            result.add(new RuntimeComponentHealth("db", "NOT_CONNECTED"));
        }
        if (result.stream().noneMatch(value -> "redis".equals(value.name()))) {
            result.add(new RuntimeComponentHealth("redis", "NOT_CONNECTED"));
        }
        if (result.stream().noneMatch(value -> "audit".equals(value.name()))) {
            result.add(new RuntimeComponentHealth("audit", "NOT_CONNECTED"));
        }
        return List.copyOf(result);
    }

    private RuntimeMetricSnapshot metrics() {
        List<Meter> meters = meterRegistry.getMeters().stream()
                .sorted(Comparator.comparing(meter -> meter.getId().getName()))
                .toList();
        List<String> names = meters.stream().map(meter -> meter.getId().getName()).distinct().limit(100).toList();
        Map<String, Double> samples = new LinkedHashMap<>();
        for (Meter meter : meters) {
            var measurements = meter.measure().iterator();
            if (measurements.hasNext()) {
                samples.putIfAbsent(meter.getId().getName(), measurements.next().getValue());
            }
            if (samples.size() >= 100) break;
        }
        return new RuntimeMetricSnapshot(meters.size(), names, Map.copyOf(samples));
    }
}
