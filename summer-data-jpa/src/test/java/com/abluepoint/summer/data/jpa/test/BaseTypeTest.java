/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:BaseTypeTest.java
 * Date:2020-12-31 17:17:31
 */

package com.abluepoint.summer.data.jpa.test;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

public class BaseTypeTest {

    @Test
    public void test(){
        System.out.println(ClassUtils.isPrimitiveOrWrapper(int.class));
    }

    private boolean isPrimitive(Class<?> aclass) {

        aclass.isAssignableFrom(Integer.class);
        aclass.isAssignableFrom(Byte.class);
        aclass.isAssignableFrom(Long.class);
        aclass.isAssignableFrom(Double.class);
        aclass.isAssignableFrom(Float.class);
        aclass.isAssignableFrom(Short.class);
        aclass.isAssignableFrom(Boolean.class);
        aclass.isAssignableFrom(Character.class);


        try {
            return ((Class<?>)aclass.getField("TYPE").get(null)).isPrimitive();
        } catch (Exception e) {
            return false;
        }
    }

}
