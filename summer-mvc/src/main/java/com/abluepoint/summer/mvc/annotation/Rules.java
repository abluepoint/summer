package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:Rules.java
 * Date:2020-01-11 16:47:11
 */

public @interface Rules {
    Rule [] value() default {};
}
