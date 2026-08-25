package com.abluepoint.summer.mvc.validate;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FieldValues {

    public static final String REQUEST_ATTRIBUTE = FieldValues.class.getName();

    private static final FieldValues EMPTY = new FieldValues(Map.of());

    private final Map<String, Object> values;

    public FieldValues(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static FieldValues from(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        return value instanceof FieldValues fieldValues ? fieldValues : EMPTY;
    }

    public <T> T get(String name, Class<T> type) {
        Object value = values.get(name);
        return value == null ? null : type.cast(value);
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    public Map<String, Object> asMap() {
        return values;
    }
}
