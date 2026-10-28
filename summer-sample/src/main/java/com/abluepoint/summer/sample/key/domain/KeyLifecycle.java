package com.abluepoint.summer.sample.key.domain;

import java.time.Instant;

/** Signing uses the active key; unexpired grace keys remain available for historical verification. */
public final class KeyLifecycle {
    private KeyLifecycle() {}
    public static boolean availableAt(String status, Instant graceUntil, Instant now) {
        return "ACTIVE".equals(status) || ("GRACE".equals(status) && (graceUntil == null || graceUntil.isAfter(now)));
    }
    public static int checkedPeriod(int value) {
        if (value < 1 || value > 3650) throw new IllegalArgumentException("key rotation period must be between 1 and 3650 days");
        return value;
    }
    public static int checkedRetention(int value) {
        if (value < 1 || value > 365) throw new IllegalArgumentException("key rotation retention must be between 1 and 365 days");
        return value;
    }
    public static void requireRetention(int value) {
        if (value < 1 || value > 365) throw new KeyRotationValidationException("retentionDays must be between 1 and 365");
    }
    public static void requireVersion(int current, int expected) {
        if (current != expected) throw new KeyRotationPreconditionException("The signing key version has changed");
    }
}
