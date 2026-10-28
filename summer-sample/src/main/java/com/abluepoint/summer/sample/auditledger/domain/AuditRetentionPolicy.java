package com.abluepoint.summer.sample.auditledger.domain;


import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record AuditRetentionPolicy(Duration period, int batchSize) {
    public AuditRetentionPolicy {
        Objects.requireNonNull(period, "period");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("Retention period must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Retention batch size must be positive");
        }
    }

    public Instant cutoff(Instant now) {
        return Objects.requireNonNull(now, "now").minus(period);
    }
}
