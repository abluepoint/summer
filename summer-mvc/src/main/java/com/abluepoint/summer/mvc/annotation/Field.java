package com.abluepoint.summer.mvc.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Fields.class)
public @interface Field {

    String param();

    String name() default "";

    boolean required() default false;

    String style() default "emptyStyle";


}
