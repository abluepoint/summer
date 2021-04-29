package com.abluepoint.summer.mvc.exception;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:DefaultExceptionCodeMapping.java
 * Date:2020-03-16 09:29:16
 */

import com.abluepoint.summer.common.exception.SummerException;
import com.abluepoint.summer.common.exception.SummerRuntimeException;
import com.abluepoint.summer.mvc.interceptor.RequestLoggingException;
import com.abluepoint.summer.mvc.validate.ValidateException;
import com.abluepoint.summer.mvc.validate.ValidateExceptions;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class NamedCodeMapping implements CodeMapping {

    private Map<String, Integer> mapping;

    public NamedCodeMapping() {
        this.mapping = new HashMap<>();
        register(SummerException.class,1001);
        register(SummerRuntimeException.class,1001);
        register(ValidateExceptions.class,1002);
        register(ValidateException.class,1003);
        register(RequestLoggingException.class,1004);

    }

    @Override
    public Integer getCode(Exception ex) {
        Integer code = mapping.get(ex.getClass().getName());
        if (code != null) {
            return code;
        } else {
            return -1;
        }
    }

    @Override
    public Integer register(Class exceptionClass, Integer code) {
        return mapping.put(exceptionClass.getName(), code);
    }
}
