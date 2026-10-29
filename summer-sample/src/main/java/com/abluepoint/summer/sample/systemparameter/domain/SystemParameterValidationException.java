package com.abluepoint.summer.sample.systemparameter.domain;

import com.abluepoint.summer.common.error.api.SummerException;
import java.util.List;

public final class SystemParameterValidationException extends RuntimeException {
    private final List<SummerException.Violation> violations;

    public SystemParameterValidationException(List<SummerException.Violation> violations) {
        super("System parameter validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<SummerException.Violation> violations() {
        return violations;
    }
}
