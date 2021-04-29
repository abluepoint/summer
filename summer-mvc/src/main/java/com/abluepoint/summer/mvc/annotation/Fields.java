package com.abluepoint.summer.mvc.annotation;

import java.lang.annotation.*;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:Fields.java
 * Date:2020-03-10 11:07:10
 */

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Fields {
    Field [] value() default {};
}
