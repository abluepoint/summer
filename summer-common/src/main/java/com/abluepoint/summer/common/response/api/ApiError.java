package com.abluepoint.summer.common.response.api;

import java.util.List;

public record ApiError(String code, String message, List<FieldViolation> violations) {

    public ApiError {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public record FieldViolation(String field, String message) {
    }
}
