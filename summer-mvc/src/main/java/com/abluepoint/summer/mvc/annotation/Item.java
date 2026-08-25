package com.abluepoint.summer.mvc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Item {

    String param();

    String name();

    boolean required() default false;

    boolean nullable() default false;

    String style();
}
