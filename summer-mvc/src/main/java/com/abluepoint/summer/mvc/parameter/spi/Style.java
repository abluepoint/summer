package com.abluepoint.summer.mvc.parameter.spi;

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
