/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateException.java
 * Date:2020-11-17 16:13:17
 */

package com.abluepoint.summer.mvc.exception;


import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.SummerException;

public class BusinessException extends SummerException {

    private static final long serialVersionUID = -1985885199858336033L;

    private String name;

    public BusinessException() {
        super();
    }

    public BusinessException(CodeMessage codeMessage) {
        super(codeMessage);
    }


    public BusinessException(CodeMessage codeMessage, Object... args) {
        super(codeMessage, args);
        initName(args);
    }

    public BusinessException(CodeMessage codeMessage, Throwable cause) {
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