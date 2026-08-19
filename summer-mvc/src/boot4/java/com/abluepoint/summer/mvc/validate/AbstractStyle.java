package com.abluepoint.summer.mvc.validate;

import java.util.Map;

public abstract class AbstractStyle implements Style {

    private final Class<?> paramType;
    private final String desc;
    private volatile Map<String, String> settings;

    protected AbstractStyle(Class<?> paramType, String desc, Map<String, String> settings) {
        this.paramType = paramType;
        this.desc = desc;
        setSettings(settings);
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
        this.settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    @Override
    public String getSetting(Object key) {
        return settings.get(key);
    }
}
