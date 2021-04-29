package com.abluepoint.summer.mvc.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:CommonRequestLoggingInterceptor.java
 * Date:2020-03-09 23:52:09
 */

public class CommonRequestLoggingInterceptor extends AbstractRequestLoggingInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CommonRequestLoggingInterceptor.class);

    public CommonRequestLoggingInterceptor(RequestLoggingSupport requestLoggingSupport) {
        super(requestLoggingSupport);
    }

    @Override
    protected void beforeRequest(HttpServletRequest request, String message) {
        logger.debug(message);
    }

    @Override
    protected void afterRequest(HttpServletRequest request, String message) {
        logger.debug(message);
    }
}
