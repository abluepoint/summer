package com.abluepoint.summer.sample.observability.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Read-only, bounded-frequency observation of the durable append-only ledger. */
@Component("auditHealthIndicator")
@org.springframework.modulith.NamedInterface("audit")
public final class AuditObservation implements HealthIndicator, MeterBinder {
    private static final long REFRESH_NANOS = 5_000_000_000L;
    private final ObjectProvider<JdbcTemplate> jdbc;
    private Snapshot current;
    private long lastRead;

    public AuditObservation(ObjectProvider<JdbcTemplate> jdbc) { this.jdbc = jdbc; }

    public synchronized Snapshot snapshot() {
        long now = System.nanoTime();
        if (current != null && now - lastRead < REFRESH_NANOS) return current;
        try {
            current = jdbc.getObject().queryForObject("""
                    SELECT COALESCE(SUM(CASE WHEN result='SUCCESS' THEN 1 ELSE 0 END),0),
                           COALESCE(SUM(CASE WHEN result='FAILURE' THEN 1 ELSE 0 END),0), COUNT(*)
                    FROM summer_audit_event
                    """, (rs, row) -> new Snapshot(true, rs.getLong(1), rs.getLong(2), rs.getLong(3), Instant.now()));
        } catch (RuntimeException unavailable) {
            // Neither driver details nor stale healthy data cross the monitoring boundary.
            current = new Snapshot(false, 0, 0, 0, Instant.now());
        }
        lastRead = System.nanoTime();
        return current;
    }

    @Override public Health health() { return snapshot().available() ? Health.up().build() : Health.down().build(); }

    @Override public void bindTo(MeterRegistry registry) {
        Gauge.builder("summer.audit.available", this, a -> a.snapshot().available() ? 1 : 0).register(registry);
        Gauge.builder("summer.audit.events", this, a -> a.value("SUCCESS")).tag("result", "SUCCESS").register(registry);
        Gauge.builder("summer.audit.events", this, a -> a.value("FAILURE")).tag("result", "FAILURE").register(registry);
        Gauge.builder("summer.audit.events", this, a -> a.value("OTHER")).tag("result", "OTHER").register(registry);
    }

    private double value(String result) {
        Snapshot data = snapshot();
        if (!data.available()) return Double.NaN;
        return switch (result) {
            case "SUCCESS" -> data.success();
            case "FAILURE" -> data.failure();
            default -> data.total() - data.success() - data.failure();
        };
    }

    public record Snapshot(boolean available, long success, long failure, long total, Instant checkedAt) { }
}
