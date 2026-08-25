package com.abluepoint.summer.mvc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(FieldLists.class)
public @interface FieldList {

    String param();

    FieldSource source() default FieldSource.PARAM;

    String name() default "";

    String countParam() default "";

    int maxSize() default -1;

    Item[] items() default {};
}
