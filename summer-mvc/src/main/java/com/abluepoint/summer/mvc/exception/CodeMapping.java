package com.abluepoint.summer.mvc.exception;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ExceptionCodeMapping.java
 * Date:2020-03-16 09:28:16
 */

public interface CodeMapping {

    Integer getCode(Exception ex);

    Integer register(Class exceptionClass, Integer code);
}
