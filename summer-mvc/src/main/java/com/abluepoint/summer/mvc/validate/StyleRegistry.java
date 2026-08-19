package com.abluepoint.summer.mvc.validate;

import java.util.Map;
import java.util.Objects;

public final class StyleRegistry {

    private final Map<String, Style> styles;

    public StyleRegistry(Map<String, Style> styles) {
        this.styles = Map.copyOf(Objects.requireNonNull(styles, "styles"));
    }

    public Style require(String name) {
        Style style = styles.get(name);
        if (style == null) {
            throw new IllegalStateException("Unknown field style: " + name);
        }
        return style;
    }
}
