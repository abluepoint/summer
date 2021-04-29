package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:AnnotationManager.java
 * Date:2020-03-10 17:09:10
 */

import org.springframework.web.method.HandlerMethod;

public interface AnnotationMetadataManager {

    Interceptors getInterceptors(HandlerMethod handler);

    FieldJsonSchema getFieldJsonSchemaMeta(HandlerMethod handler);

    FieldsMeta getFieldsMeta(HandlerMethod hm);

    public static AnnotationMetadataManager INSTANCE = DefaultAnnotationMetadataManager.getInstance();
}
