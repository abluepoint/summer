package com.abluepoint.summer.mvc.annotation;

import java.lang.annotation.*;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:FieldList.java
 * Date:2020-03-10 11:08:10
 */

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(FieldLists.class)
public @interface FieldList {
    String param();
    String name() default "";
    String countParam() default "";
    Item [] items() default {};
}
