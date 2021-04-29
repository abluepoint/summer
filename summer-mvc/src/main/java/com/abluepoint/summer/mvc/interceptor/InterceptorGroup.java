package com.abluepoint.summer.mvc.interceptor;

import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

public class InterceptorGroup {

    private HandlerInterceptor[] interceptors;

    public HandlerInterceptor[] getInterceptors(){
        return interceptors;
    }

    public void setInterceptors(HandlerInterceptor[] interceptors) {
        this.interceptors = interceptors;
    }
}
