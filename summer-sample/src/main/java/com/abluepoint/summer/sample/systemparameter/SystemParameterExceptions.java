package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.error.api.SummerException;

import java.util.List;

final class SystemParameterValidationException extends RuntimeException {
    private final List<SummerException.Violation> violations;

    SystemParameterValidationException(List<SummerException.Violation> violations) {
        super("System parameter validation failed");
        this.violations = List.copyOf(violations);
    }

    List<SummerException.Violation> violations() {
        return violations;
    }
}

final class SystemParameterConflictException extends RuntimeException {
    SystemParameterConflictException(String message) {
        super(message);
    }
}

final class SystemParameterVersionConflictException extends RuntimeException {
    SystemParameterVersionConflictException() {
        super("The system parameter was changed by another request");
    }
}

final class SystemParameterNotFoundException extends RuntimeException {
    SystemParameterNotFoundException(String key) {
        super("System parameter not found: " + key);
    }
}
