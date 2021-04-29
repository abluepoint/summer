package com.abluepoint.summer.mvc.annotation;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:FieldListMeta.java
 * Date:2020-03-11 11:03:11
 */

public class FieldListMeta {

    private final String param;
    private final String name;
    private final String countParam;
    private final FieldMeta[] fields;

    public FieldListMeta(String param, String name, String countParam, FieldMeta[] fields) {
        Assert.hasText(param, "param must not be null");
        Assert.isTrue(fields!=null&&fields.length>0, "fields must not be null");

        this.param = param;
        if (StringUtils.hasText(name)) {
            this.name = name;
        } else {
            this.name = param;
        }
        if(StringUtils.hasText(countParam)){
            this.countParam = countParam;
        }else{
            this.countParam = param+".count";
        }

        this.fields = fields;
    }

    public FieldListMeta(FieldList fieldList, FieldMeta[] fields) {
        this.param = fieldList.param();
        this.name = fieldList.name();

        this.countParam = fieldList.countParam();
        this.fields = fields;
    }

    public String getParam() {
        return param;
    }

    public String getName() {
        return name;
    }

    public String getCountParam() {
        return countParam;
    }

    public FieldMeta[] getFields() {
        return fields;
    }

    @Override
    public String toString() {
        return "FieldListMeta{" + "param='" + param + '\'' + ", name='" + name + '\'' + ", countParam='" + countParam + '\'' + ", fields=" + Arrays.toString(fields) + '}';
    }
}
