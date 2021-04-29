package com.abluepoint.summer.mvc.validate;

import java.util.Map;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:DefaultStyle.java
 * Date:2020-03-12 15:08:12
 */

public abstract class AbstractStyle implements Style {

    private Class<?> paramType;
    private String desc;
    private Map<String, String> settings;

    AbstractStyle(Class<?> paramType, String desc, Map<String, String> settings) {
        this.paramType = paramType;
        this.desc = desc;
        this.settings = settings;
    }

    @Override
    public Class<?> getParamType() {
        return paramType;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    @Override
    public Map<String, String> getSettings() {
        return settings;
    }

    @Override
    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    @Override
    public String getSetting(Object key) {
        return settings.get(key);
    }


}
