package com.abluepoint.summer.common.error;

import java.util.Arrays;
import java.util.List;

public class SummerException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] messageArguments;
    private final List<Violation> violations;

    public SummerException(ErrorCode errorCode, Object... messageArguments) {
        this(errorCode, null, messageArguments);
    }

    public SummerException(ErrorCode errorCode, Throwable cause, Object... messageArguments) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
        this.messageArguments = Arrays.copyOf(messageArguments, messageArguments.length);
        this.violations = List.of();
    }

    public SummerException(ErrorCode errorCode, List<Violation> violations) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.messageArguments = new Object[0];
        this.violations = List.copyOf(violations);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object[] messageArguments() {
        return Arrays.copyOf(messageArguments, messageArguments.length);
    }

    public List<Violation> violations() {
        return violations;
    }

    public record Violation(String field, String message) {
    }
}
