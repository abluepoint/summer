/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerTupleConverterTest.java
 * Date:2020-12-31 17:16:31
 */

package com.abluepoint.summer.data.jpa.transform;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;


public class SummerTupleConverterTest {
    public static interface Demo {
        void hello();
    }

    @Test
    public void test() {
        boolean r = ClassUtils.isJavaLanguageInterface(Demo.class);
        System.out.println(r);
    }

}