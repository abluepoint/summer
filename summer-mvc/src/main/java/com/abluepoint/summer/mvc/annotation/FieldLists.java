package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:FieldLists.java
 * Date:2020-03-10 11:08:10
 */

import java.lang.annotation.*;

@Target({ ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FieldLists {
    FieldList [] value() default {};
}
