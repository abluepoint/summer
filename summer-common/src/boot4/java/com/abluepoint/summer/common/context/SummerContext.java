package com.abluepoint.summer.common.context;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SummerContext(String requestId, Locale locale, Map<String, Object> attributes) {

    public SummerContext {
        requestId = Objects.requireNonNull(requestId, "requestId");
        locale = locale == null ? Locale.getDefault() : locale;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public Optional<Object> attribute(String name) {
        return Optional.ofNullable(attributes.get(name));
    }
}
