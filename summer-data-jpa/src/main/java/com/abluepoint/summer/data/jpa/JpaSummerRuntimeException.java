package com.abluepoint.summer.data.jpa;

import com.abluepoint.summer.common.exception.SummerException;
import com.abluepoint.summer.common.exception.SummerRuntimeException;

public class JpaSummerRuntimeException extends SummerRuntimeException {

    public JpaSummerRuntimeException() {
    }

    public JpaSummerRuntimeException(String message) {
        super(message);
    }

    public JpaSummerRuntimeException(String message, Object... args) {
        super(message, args);
    }

    public JpaSummerRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public JpaSummerRuntimeException(String message, Throwable cause, Object... args) {
        super(message, cause, args);
    }

    public JpaSummerRuntimeException(SummerException e) {
        super(e);
    }

    public JpaSummerRuntimeException(Throwable cause) {
        super(cause);
    }
}
