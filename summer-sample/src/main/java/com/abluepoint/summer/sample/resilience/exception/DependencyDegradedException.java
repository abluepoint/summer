package com.abluepoint.summer.sample.resilience.exception;

import java.time.Instant;
import java.util.List;

public final class DependencyDegradedException extends RuntimeException {
    public DependencyDegradedException(String message, Throwable cause) { super(message, cause); }
}
