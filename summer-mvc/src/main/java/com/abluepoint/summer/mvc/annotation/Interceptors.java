package com.abluepoint.summer.mvc.annotation;

import java.lang.annotation.*;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:Interceptors.java
 * Date:2020-02-27 22:45:27
 */

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Interceptors {
    String value() default "_default";
}