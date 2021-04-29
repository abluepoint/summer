/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ContextUtilsTest.java
 * Date:2020-12-31 17:19:31
 */

package com.abluepoint.summer.common.util;


import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;


public class ContextUtilsTest {

    public static class User{
        private String name;
        private String desc;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return "User{" + "name='" + name + '\'' + ", desc='" + desc + '\'' + '}';
        }
    }

    @Test
    public void mapProperties() {
        Map<String,Object> data = new HashMap<>();
        data.put("name","peter");
        data.put("desc","desc");
        User user = new User();
        ContextUtils.mapProperties(data,user,null,null);
        System.out.println(user);
    }
}