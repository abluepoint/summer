package com.abluepoint.summer.sample.systemparameter.domain;

import com.abluepoint.summer.common.error.api.SummerException;
import java.util.List;

public final class SystemParameterReleaseValidationException extends RuntimeException {
    private final List<SummerException.Violation> violations;
    public SystemParameterReleaseValidationException(String message) { super(message); this.violations = List.of(new SummerException.Violation("request", message)); }
    public SystemParameterReleaseValidationException(List<SummerException.Violation> violations) { super("System parameter release validation failed"); this.violations = List.copyOf(violations); }
    public List<SummerException.Violation> violations() { return violations; }
}
