package com.abluepoint.summer.sample.resource.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@org.springframework.modulith.NamedInterface("permissions")
public @interface ResourcePermission {

    String value();
}
