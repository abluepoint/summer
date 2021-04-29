/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JpaFreemarkerTemplateSourceTest.java
 * Date:2020-12-31 17:17:31
 */

package com.abluepoint.summer.data.jpa;


import org.junit.jupiter.api.Test;

public class JpaFreemarkerTemplateSourceTest {


    @Test
    public void getTemplateLocation() {
        String templateName = "test/adfadf/1323";
        int pos = templateName.indexOf("/");
        pos = pos+1;
        String location = templateName.substring(0,pos);
        String tName = templateName.substring(pos);
        System.out.println(location);
        System.out.println(tName);
        System.out.println("and".length());
    }
}
