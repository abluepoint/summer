/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:AbstractRequestLoggingInterceptor.java
 * Date:2020-03-27 17:41:27
 */

package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.filter.CachingSupportServletRequestWrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class AbstractRequestLoggingInterceptor implements HandlerInterceptor {

    private RequestLoggingSupport requestLoggingSupport;

    public AbstractRequestLoggingInterceptor(RequestLoggingSupport requestLoggingSupport) {
        this.requestLoggingSupport = requestLoggingSupport;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        CachingSupportServletRequestWrap wrapper = WebUtils.getNativeRequest(request, CachingSupportServletRequestWrap.class);
        if (wrapper == null) {
            throw new RequestLoggingException("system.error.request_not_support");
        }
        wrapper.cacheRequest();
        beforeRequest(request, requestLoggingSupport.getBeforeMessage(request));

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        afterRequest(request, requestLoggingSupport.getAfterMessage(request));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {

    }

    protected abstract void beforeRequest(HttpServletRequest request, String message);

    protected abstract void afterRequest(HttpServletRequest request, String message);

}
