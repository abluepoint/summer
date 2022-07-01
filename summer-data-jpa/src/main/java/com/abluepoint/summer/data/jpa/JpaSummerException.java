package com.abluepoint.summer.data.jpa;

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.SummerException;

public class JpaSummerException extends SummerException {
    public JpaSummerException() {
    }

    public JpaSummerException(CodeMessage codeMessage) {
        super(codeMessage);
    }

    public JpaSummerException(CodeMessage codeMessage, Object... args) {
        super(codeMessage, args);
    }

    public JpaSummerException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage, cause);
    }

}
