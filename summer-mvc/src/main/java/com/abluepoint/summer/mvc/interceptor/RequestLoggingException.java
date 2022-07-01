package com.abluepoint.summer.mvc.interceptor;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:RequestLoggingException.java
 * Date:2020-03-10 17:45:10
 */

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.SummerException;

public class RequestLoggingException extends SummerException {

    public RequestLoggingException() {
        super();
    }

    public RequestLoggingException(CodeMessage codeMessage) {
        super(codeMessage);
    }

    public RequestLoggingException(CodeMessage codeMessage, Object[] args) {
        super(codeMessage, args);
    }

    public RequestLoggingException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage, cause);
    }

}
