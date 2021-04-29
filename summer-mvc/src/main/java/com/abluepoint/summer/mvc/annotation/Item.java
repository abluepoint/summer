package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:Item.java
 * Date:2020-03-10 11:21:10
 */

public @interface Item {

    String param();

    String name();

    boolean required() default false;

    String style();

}
