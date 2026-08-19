package com.abluepoint.summer.mvc.validate;

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
