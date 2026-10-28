package com.abluepoint.summer.mvc.parameter.internal;

import com.abluepoint.summer.mvc.parameter.spi.Style;

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

    public boolean contains(String name) {
        return name != null && styles.containsKey(name);
    }
}
