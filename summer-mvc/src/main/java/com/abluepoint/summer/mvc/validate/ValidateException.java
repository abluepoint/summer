/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateException.java
 * Date:2020-11-17 16:13:17
 */

package com.abluepoint.summer.mvc.validate;


import com.abluepoint.summer.common.exception.SummerException;

public class ValidateException extends SummerException {

    private static final long serialVersionUID = -1985885199858336033L;

    private String name;

    public ValidateException() {
        super();
    }

    public ValidateException(String message) {
        super(message);
    }

    public ValidateException(String message, Object[] args) {
        super(message, args);
        initName(args);
    }

    public ValidateException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValidateException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
        initName(args);
    }

    private void initName(Object[] args) {
        if (args.length > 1) {
            name = (String) args[0];
        }
    }

    public String getName() {
        return name;
    }
}