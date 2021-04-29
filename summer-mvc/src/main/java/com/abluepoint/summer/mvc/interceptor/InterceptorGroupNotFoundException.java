package com.abluepoint.summer.mvc.interceptor;

public class InterceptorGroupNotFoundException extends RuntimeException {

    public InterceptorGroupNotFoundException() {
        super();
    }

    public InterceptorGroupNotFoundException(String msg) {
        super(msg);
    }

    public InterceptorGroupNotFoundException(String msg, Throwable t) {
        super(msg, t);
    }

}
