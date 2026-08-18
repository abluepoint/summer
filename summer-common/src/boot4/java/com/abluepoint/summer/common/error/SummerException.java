package com.abluepoint.summer.common.error;

import java.util.Arrays;

public class SummerException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] messageArguments;

    public SummerException(ErrorCode errorCode, Object... messageArguments) {
        this(errorCode, null, messageArguments);
    }

    public SummerException(ErrorCode errorCode, Throwable cause, Object... messageArguments) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
        this.messageArguments = Arrays.copyOf(messageArguments, messageArguments.length);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object[] messageArguments() {
        return Arrays.copyOf(messageArguments, messageArguments.length);
    }
}
