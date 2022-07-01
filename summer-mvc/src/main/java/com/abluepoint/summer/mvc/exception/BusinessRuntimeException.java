/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateRuntimeException.java
 * Date:2020-11-12 17:44:12
 */

package com.abluepoint.summer.mvc.exception;

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.SummerRuntimeException;

public class BusinessRuntimeException extends SummerRuntimeException {

    private static final long serialVersionUID = 2285211535401698199L;

    public BusinessRuntimeException() {
        super();
    }

    public BusinessRuntimeException(CodeMessage codeMessage) {
        super(codeMessage);
    }

    public BusinessRuntimeException(CodeMessage codeMessage, Object... args) {
        super(codeMessage, args);
    }

    public BusinessRuntimeException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage, cause);
    }

    public BusinessRuntimeException(CodeMessage codeMessage, Throwable cause, Object... args) {
        super(codeMessage, cause, args);
    }


}