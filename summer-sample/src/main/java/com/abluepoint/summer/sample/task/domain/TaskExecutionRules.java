package com.abluepoint.summer.sample.task.domain;
import java.time.Instant;
public final class TaskExecutionRules {
 private TaskExecutionRules() {}
    public static int retries(Integer value) { int n = value == null ? 0 : value; if (n < 0 || n > 10) throw new TaskValidationException("maxRetries must be between 0 and 10"); return n; }
    public static long timeout(Long value) { long n = value == null ? 30_000L : value; if (n < 1_000L || n > 300_000L) throw new TaskValidationException("timeoutMs must be between 1000 and 300000"); return n; }
    public static String requireKey(String value) { if (value == null || value.isBlank() || value.length() > 255) throw new TaskIdempotencyRequiredException(); return value.trim(); }

 public static void requireTriggerable(TaskStatus status) { if (status != TaskStatus.ACTIVE) throw new TaskConflictException("Paused task cannot be triggered"); }
 public static boolean claimable(TaskExecutionStatus status, Instant leaseUntil, boolean recovering, Instant now) { return status == TaskExecutionStatus.RUNNING && (recovering || leaseUntil == null || !leaseUntil.isAfter(now)); }
 public static int completedPercent(int number, int completed, int maxAttempts) { return Math.min(95, Math.max(1, ((number - 1) * 90 + Math.max(0, Math.min(100, completed)) * 90 / 100) / maxAttempts)); }
 public static String boundedKey(String key, String fallback) { String value = key == null || key.isBlank() ? fallback : key.trim(); return value.length() <= 255 ? value : value.substring(0,255); }
}
