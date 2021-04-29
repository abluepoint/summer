package com.abluepoint.summer.mvc.interceptor;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:RequestLoggingException.java
 * Date:2020-03-10 17:45:10
 */

import com.abluepoint.summer.common.exception.SummerException;

public class RequestLoggingException extends SummerException {

    public RequestLoggingException() {
        super();
    }

    public RequestLoggingException(String message) {
        super(message);
    }

    public RequestLoggingException(String message, Object[] args) {
        super(message, args);
    }

    public RequestLoggingException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestLoggingException(String message, Object[] args, Throwable cause) {
        super(message, args, cause);
    }

}
