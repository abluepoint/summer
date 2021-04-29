package com.abluepoint.summer.mvc.validate;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:Style.java
 * Date:2020-03-10 16:38:10
 */

import java.util.Map;

public interface Style {

    Class<?> getParamType();

    String getDesc();

    Map<String, String> getSettings();

    void setSettings(Map<String, String> settings);

    String getSetting(Object key);

    boolean validate(String value);

    Object getTypedObject(String value);
}
