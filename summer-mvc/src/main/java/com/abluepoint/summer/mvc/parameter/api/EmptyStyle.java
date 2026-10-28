package com.abluepoint.summer.mvc.parameter.api;

import com.abluepoint.summer.mvc.parameter.spi.AbstractStyle;

import java.util.Map;

public final class EmptyStyle extends AbstractStyle {

    public EmptyStyle() {
        super(String.class, "text", Map.of());
    }

    @Override
    public boolean validate(String value) {
        return value != null;
    }

    @Override
    public Object getTypedObject(String value) {
        return value;
    }
}
