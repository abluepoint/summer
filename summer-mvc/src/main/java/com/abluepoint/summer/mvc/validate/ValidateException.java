/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateException.java
 * Date:2020-11-17 16:13:17
 */

package com.abluepoint.summer.mvc.validate;


import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.SummerException;

public class ValidateException extends SummerException {

    private static final long serialVersionUID = -1985885199858336033L;

    private String name;

    public ValidateException() {
        super();
    }

    public ValidateException(CodeMessage codeMessage) {
        super(codeMessage);
    }


    public ValidateException(CodeMessage codeMessage, Object... args) {
        super(codeMessage, args);
        initName(args);
    }

    public ValidateException(CodeMessage codeMessage, Throwable cause) {
        super(codeMessage, cause);
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