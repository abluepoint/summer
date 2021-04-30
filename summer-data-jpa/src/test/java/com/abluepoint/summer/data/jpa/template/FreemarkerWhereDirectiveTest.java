/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:FtlWhereDirectiveTest.java
 * Date:2020-12-31 17:16:31
 */

package com.abluepoint.summer.data.jpa.template;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class FreemarkerWhereDirectiveTest {

    @Test
    public void execute() {
        String bodyString = "AND u='1223' and t='1' and";

        int begin =0;
        int end = 0;
        if(StringUtils.startsWithIgnoreCase(bodyString,"and")){
            begin = 3;
        }

        if(StringUtils.endsWithIgnoreCase(bodyString,"and")){
            end = bodyString.length()-3;
        }

        bodyString = bodyString.substring(begin,end);

        System.out.println(bodyString);
    }
}