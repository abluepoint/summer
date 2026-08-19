package com.abluepoint.summer.mvc.validate;

import java.util.List;

public final class FieldValidationException extends RuntimeException {

    private final List<Violation> violations;

    public FieldValidationException(List<Violation> violations) {
        super("Field validation failed");
        this.violations = List.copyOf(violations);
    }

    public List<Violation> violations() {
        return violations;
    }

    public record Violation(String field, String message) {
    }
}
