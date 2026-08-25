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
@Repeatable(Fields.class)
public @interface Field {

    String param();

    FieldSource source() default FieldSource.PARAM;

    String name() default "";

    boolean required() default false;

    boolean nullable() default false;

    String style() default "emptyStyle";
}
