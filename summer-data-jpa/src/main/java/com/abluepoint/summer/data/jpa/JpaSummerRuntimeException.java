package com.abluepoint.summer.data.jpa;

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.SummerException;
import com.abluepoint.summer.common.exception.SummerRuntimeException;

public class JpaSummerRuntimeException extends SummerRuntimeException {

    public JpaSummerRuntimeException() {
    }

    public JpaSummerRuntimeException(CodeMessage codeMessage) {
        super(codeMessage);
    }

    public JpaSummerRuntimeException(CodeMessage codeMessage, Object... args) {
        super(codeMessage, args);
    }

    public JpaSummerRuntimeException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage, cause);
    }

    public JpaSummerRuntimeException(SummerException e) {
        super(e);
    }

    public JpaSummerRuntimeException(Throwable cause) {
        super(cause);
    }
}
