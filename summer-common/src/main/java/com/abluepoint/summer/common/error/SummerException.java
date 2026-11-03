package com.abluepoint.summer.common.error;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SummerException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String explicitMessage;
    private final Object[] messageArguments;
    private final List<Violation> violations;

    public SummerException(ErrorCode errorCode, Object... messageArguments) {
        this(errorCode, null, messageArguments);
    }

    public SummerException(ErrorCode errorCode, Throwable cause, Object... messageArguments) {
        this(errorCode, null, cause, messageArguments, List.of());
    }

    public SummerException(ErrorCode errorCode, List<Violation> violations) {
        this(errorCode, null, null, new Object[0], violations);
    }

    /** Creates an error with a literal message; null retains error-code message resolution. */
    public static SummerException withMessage(ErrorCode errorCode, String message, Object... messageArguments) {
        return withMessage(errorCode, message, null, messageArguments);
    }

    public static SummerException withMessage(
            ErrorCode errorCode, String message, Throwable cause, Object... messageArguments) {
        return new SummerException(errorCode, message, cause, messageArguments, List.of());
    }

    /** Shared initialization for field-validation subclasses, including application error codes. */
    protected SummerException(ErrorCode errorCode, String message, Throwable cause,
                              Object[] messageArguments, List<Violation> violations) {
        super(message == null ? Objects.requireNonNull(errorCode, "errorCode").defaultMessage() : message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.explicitMessage = message;
        this.messageArguments = Arrays.copyOf(messageArguments, messageArguments.length);
        this.violations = List.copyOf(violations);
    }

    /** Null means absent; empty and blank strings are explicitly supplied messages. */
    public String explicitMessage() {
        return explicitMessage;
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
