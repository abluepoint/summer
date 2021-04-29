/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidatorTypeCodeTest.java
 * Date:2020-12-31 17:20:31
 */

package com.abluepoint.summer.mvc.validate;

import org.junit.jupiter.api.Test;

import java.util.Map;

public class ValidatorTypeCodeTest {

    @Test
    public void test() {
        Map<String, ValidatorTypeCode> map = ValidatorTypeCode.getConstants();
        for (ValidatorTypeCode tc : map.values()) {
//            System.out.println(tc);
            System.out.print("validate.json.");
            System.out.print(tc.getValue());
            System.out.print(" = ");
            System.out.print(tc.getMessageKey());
            System.out.println();
        }

//        try {
//            String s = "a";
//            byte[] data = s.getBytes("UTF-8");
//            System.out.println(data.length);
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }

    }

}