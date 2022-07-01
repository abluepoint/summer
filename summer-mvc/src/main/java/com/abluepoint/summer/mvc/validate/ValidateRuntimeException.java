/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateRuntimeException.java
 * Date:2020-11-12 17:44:12
 */

package com.abluepoint.summer.mvc.validate;

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.SummerRuntimeException;

public class ValidateRuntimeException extends SummerRuntimeException {

    private static final long serialVersionUID = 2285211535401698199L;

    public ValidateRuntimeException() {
        super();
    }

    public ValidateRuntimeException(CodeMessage codeMessage) {
        super(codeMessage);
    }

    public ValidateRuntimeException(CodeMessage codeMessage, Object... args) {
        super(codeMessage, args);
    }

    public ValidateRuntimeException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage, cause);
    }

    public ValidateRuntimeException(CodeMessage codeMessage, Throwable cause, Object... args) {
        super(codeMessage, cause, args);
    }


}