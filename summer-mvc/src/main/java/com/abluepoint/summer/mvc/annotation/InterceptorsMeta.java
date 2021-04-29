package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:InterceptorsMeta.java
 * Date:2020-03-11 09:51:11
 */

import com.abluepoint.summer.mvc.interceptor.InterceptorGroup;

public class InterceptorsMeta {
    private final Interceptors interceptors;
    private final InterceptorGroup interceptorGroup;

    public InterceptorsMeta(Interceptors interceptors, InterceptorGroup interceptorGroup) {
        this.interceptors = interceptors;
        this.interceptorGroup = interceptorGroup;
    }

    public Interceptors getInterceptors() {
        return interceptors;
    }

    public InterceptorGroup getInterceptorGroup() {
        return interceptorGroup;
    }
}
