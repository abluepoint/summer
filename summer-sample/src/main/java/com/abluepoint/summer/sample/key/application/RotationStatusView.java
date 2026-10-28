package com.abluepoint.summer.sample.key.application;
import java.time.Instant;

public record RotationStatusView(String status, int periodDays, int retentionDays, Instant nextRotationAt,
                          Instant lastAttemptAt, Instant lastSuccessAt, int failureCount, String lastError) {}
