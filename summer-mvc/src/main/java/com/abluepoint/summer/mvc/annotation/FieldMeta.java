package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:FieldMeta.java
 * Date:2020-03-11 09:57:11
 */

import com.abluepoint.summer.mvc.validate.Style;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class FieldMeta {

    private final String param;
    private final String name;
    private final boolean required;
    private final String styleName;

    private Style style;

    public FieldMeta(String param, String name, boolean required, String styleName) {
        Assert.hasText(param, "param must not be null");
        Assert.hasText(styleName, "style must not be null");
        this.param = param;
        if (StringUtils.hasText(name)) {
            this.name = name;
        } else {
            this.name = param;
        }
        this.required = required;
        this.styleName = styleName;
    }

    public FieldMeta(Field field) {
        this(field.param(), field.name(), field.required(), field.style());
    }

    public FieldMeta(Item field) {
        this(field.param(), field.name(), field.required(), field.style());
    }

    public String getParam() {
        return param;
    }

    public String getName() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public String getStyleName() {
        return styleName;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    @Override
    public String toString() {
        return "{" + "param='" + param + '\'' + ", name='" + name + '\'' + ", required=" + required + ", style='" + style + '\'' + '}';
    }
}
