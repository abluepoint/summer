/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:EmptyStype.java
 * Date:2020-04-16 11:31:16
 */

package com.abluepoint.summer.mvc.validate;

import java.util.Map;

public class EmptyStyle implements Style{

    @Override
    public Class<?> getParamType() {
        return String.class;
    }

    @Override
    public String getDesc() {
        return null;
    }

    @Override
    public Map<String, String> getSettings() {
        return null;
    }

    @Override
    public void setSettings(Map<String, String> settings) {

    }

    @Override
    public String getSetting(Object key) {
        return null;
    }

    @Override
    public boolean validate(String value) {
        return true;
    }

    @Override
    public Object getTypedObject(String value) {
        return value;
    }
}
