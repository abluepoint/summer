package com.abluepoint.summer.sample.operations.service;

import com.abluepoint.summer.sample.operations.model.view.RuntimeComponentHealthView;
import com.abluepoint.summer.sample.operations.model.view.RuntimeDiagnosticsView;
import com.abluepoint.summer.sample.operations.model.view.RuntimeHttpMetricsView;
import com.abluepoint.summer.sample.operations.model.view.RuntimeMetricSnapshotView;
import com.abluepoint.summer.sample.operations.model.view.RuntimeModuleMetricsView;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.observability.service.AuditObservation;
import com.abluepoint.summer.sample.observability.service.TraceCorrelation;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Statistic;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.flywaydb.core.Flyway;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.lang.management.ManagementFactory;

@Service
public final class RuntimeDiagnosticsService {

    private final HealthEndpoint healthEndpoint;
    private final MeterRegistry meterRegistry;
    private final RequestIdAccessor requestIdAccessor;
    private final Clock clock;
    private final Environment environment;
    private final ObjectProvider<Flyway> migrations;
    private final AuditObservation audit;

    RuntimeDiagnosticsService(HealthEndpoint healthEndpoint, MeterRegistry meterRegistry,
                              RequestIdAccessor requestIdAccessor, Environment environment, ObjectProvider<Flyway> migrations,
                              AuditObservation audit) {
        this.healthEndpoint = healthEndpoint;
        this.meterRegistry = meterRegistry;
        this.requestIdAccessor = requestIdAccessor;
        this.clock = Clock.systemUTC();
        this.environment = environment;
        this.migrations = migrations;
        this.audit = audit;
    }

    public RuntimeDiagnosticsView snapshot() {
        return new RuntimeDiagnosticsView(
                requestIdAccessor.currentRequestId(),
                TraceCorrelation.currentTraceId(),
                clock.instant(),
                components(healthEndpoint.health()),
                metrics());
    }

    private List<RuntimeComponentHealthView> components(HealthDescriptor root) {
        List<RuntimeComponentHealthView> result = new ArrayList<>();
        if (root instanceof CompositeHealthDescriptor composite) {
            composite.getComponents().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.add(new RuntimeComponentHealthView(
                            entry.getKey(), entry.getValue().getStatus().getCode())));
        } else {
            result.add(new RuntimeComponentHealthView("application", root.getStatus().getCode()));
        }
        if (result.stream().noneMatch(value -> "db".equals(value.name()))) {
            result.add(new RuntimeComponentHealthView("db", "NOT_CONNECTED"));
        }
        if (result.stream().noneMatch(value -> "redis".equals(value.name()))) {
            result.add(new RuntimeComponentHealthView("redis", "NOT_CONNECTED"));
        }
        if (result.stream().noneMatch(value -> "audit".equals(value.name()))) {
            result.add(new RuntimeComponentHealthView("audit", "NOT_CONNECTED"));
        }
        result.add(new RuntimeComponentHealthView("migrations", migrationStatus()));
        return List.copyOf(result);
    }

    private String migrationStatus() {
        try {
            if (!environment.getProperty("spring.flyway.enabled", Boolean.class, true)) {
                return "NOT_ENABLED";
            }
            Flyway flyway = migrations.getIfAvailable();
            if (flyway == null) return "UNAVAILABLE";
            if (!flyway.validateWithResult().validationSuccessful) return "VALIDATION_FAILED";
            var info = flyway.info();
            if (info.pending().length > 0) return "PENDING";
            return info.applied().length > 0 ? "UP" : "NOT_APPLIED";
        } catch (RuntimeException unavailable) {
            // Driver, configuration and migration details never cross the diagnostics boundary.
            return "UNAVAILABLE";
        }
    }

    private RuntimeMetricSnapshotView metrics() {
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
        // Exclude non-finite values (e.g. a failed DB scrape) from JSON numeric samples.
        samples.entrySet().removeIf(entry -> !Double.isFinite(entry.getValue()));
        var auditSnapshot = audit.snapshot();
        RuntimeModuleMetricsView auditMetrics = new RuntimeModuleMetricsView(
                auditSnapshot.available() ? "AVAILABLE" : "UNAVAILABLE", "PERSISTED_LEDGER_MAX_5S_OLD",
                auditSnapshot.checkedAt(), auditSnapshot.available() ? Map.of(
                        "success", (double) auditSnapshot.success(), "failure", (double) auditSnapshot.failure(),
                        "total", (double) auditSnapshot.total()) : Map.of());
        return new RuntimeMetricSnapshotView(meters.size(), names, Map.copyOf(samples), httpMetrics(), auditMetrics,
                moduleMetrics("PROCESS_LIFETIME", Map.of("attempts", "spring.security.authentications")),
                moduleMetrics("PROCESS_LIFETIME", Map.of("requests", "summer.cache.requests", "hits", "summer.cache.hits",
                        "loads", "summer.cache.loads", "redisFailures", "summer.cache.redis.failures", "localEntries", "summer.cache.entries")),
                moduleMetrics("CURRENT", Map.of("connections", "summer.realtime.connections")));
    }

    private RuntimeHttpMetricsView httpMetrics() {
        long count = 0, clientErrors = 0, serverErrors = 0;
        double totalMillis = 0;
        var timers = meterRegistry.find("http.server.requests").timers();
        for (Timer timer : timers) {
            long requests = timer.count();
            count += requests;
            totalMillis += timer.totalTime(TimeUnit.MILLISECONDS);
            String status = timer.getId().getTag("status");
            if (status != null && status.matches("4[0-9]{2}")) clientErrors += requests;
            if (status != null && status.matches("5[0-9]{2}")) serverErrors += requests;
        }
        return new RuntimeHttpMetricsView(timers.isEmpty() ? "NO_DATA" : "AVAILABLE", "PROCESS_LIFETIME",
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()), count, clientErrors, serverErrors,
                count == 0 ? null : totalMillis / count, count == 0 ? null : (double) (clientErrors + serverErrors) / count);
    }

    private RuntimeModuleMetricsView moduleMetrics(String window, Map<String, String> requested) {
        Map<String, Double> values = new LinkedHashMap<>();
        requested.forEach((field, name) -> {
            var meters = meterRegistry.find(name).meters();
            if (!meters.isEmpty()) {
                double sum = 0;
                for (Meter meter : meters) {
                    for (var measurement : meter.measure()) {
                        if (measurement.getStatistic() == Statistic.COUNT || measurement.getStatistic() == Statistic.VALUE) {
                            double value = measurement.getValue();
                            if (Double.isFinite(value)) sum += value;
                        }
                    }
                }
                values.put(field, sum);
            }
        });
        return new RuntimeModuleMetricsView(values.isEmpty() ? "NOT_CONNECTED" : "AVAILABLE", window, clock.instant(), Map.copyOf(values));
    }
}
