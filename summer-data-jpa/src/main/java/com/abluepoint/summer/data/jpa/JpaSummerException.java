package com.abluepoint.summer.data.jpa;

import com.abluepoint.summer.common.exception.SummerException;

public class JpaSummerException extends SummerException {
    public JpaSummerException() {
    }

    public JpaSummerException(String message) {
        super(message);
    }

    public JpaSummerException(String message, Object... args) {
        super(message, args);
    }

    public JpaSummerException(String message, Throwable cause) {
        super(message, cause);
    }

    public JpaSummerException(String message, Throwable cause, Object... args) {
        super(message, cause, args);
    }
}
