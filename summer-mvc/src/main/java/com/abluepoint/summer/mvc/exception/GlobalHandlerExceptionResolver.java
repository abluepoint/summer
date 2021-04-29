package com.abluepoint.summer.mvc.exception;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:GlobalHandlerExceptionResolver.java
 * Date:2020-03-16 15:48:16
 */

import com.abluepoint.summer.mvc.interceptor.RequestLoggingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GlobalHandlerExceptionResolver extends SummerHandlerExceptionResolver {

    private RequestLoggingSupport requestLoggingSupport;
    private static final Logger logger = LoggerFactory.getLogger(GlobalHandlerExceptionResolver.class);

    public GlobalHandlerExceptionResolver(CodeMapping codeMapping, RequestLoggingSupport requestLoggingSupport) {
        super(codeMapping);
        this.requestLoggingSupport = requestLoggingSupport;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String afterMsg = requestLoggingSupport.getAfterMessage(request);
        if (logger.isDebugEnabled()) {
            logger.debug(afterMsg + " failed", ex);
        }
        return super.resolveException(request, response, handler, ex);
    }
}