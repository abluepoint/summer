package com.abluepoint.summer.mvc.logging;

import com.abluepoint.summer.mvc.filter.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Creates a bounded, redacted copy of the body already managed by MVC. */
public final class RequestBodySnapshotter {

    public static final String REDACTED_VALUE = "[REDACTED]";
    public static final String UNAVAILABLE_VALUE = "[BODY_UNAVAILABLE]";
    private static final Set<String> DEFAULT_REDACTED_FIELDS = Set.of(
            "password", "passwd", "secret", "token", "access_token", "refresh_token",
            "client_secret", "authorization", "cookie"
    );

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxPreviewLength;
    private final Set<String> redactedFields;

    public RequestBodySnapshotter(
            ObjectMapper objectMapper,
            boolean enabled,
            int maxPreviewLength,
            Set<String> redactedFields
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (maxPreviewLength < 1) {
            throw new IllegalArgumentException("maxPreviewLength must be positive");
        }
        this.enabled = enabled;
        this.maxPreviewLength = maxPreviewLength;
        this.redactedFields = normalizeRedactedFields(redactedFields);
    }

    public static RequestBodySnapshotter disabled(ObjectMapper objectMapper) {
        return new RequestBodySnapshotter(objectMapper, false, 2048, DEFAULT_REDACTED_FIELDS);
    }

    public String snapshot(HttpServletRequest request) {
        if (!enabled || !(request instanceof CachedBodyHttpServletRequest cachedRequest)) {
            return null;
        }
        byte[] body = cachedRequest.body();
        if (body.length == 0) {
            return null;
        }

        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return snapshotJson(body);
        }
        return truncate(UNAVAILABLE_VALUE);
    }

    private String snapshotJson(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null || (!node.isObject() && !isObjectArray(node))) {
                return truncate(UNAVAILABLE_VALUE);
            }
            redact(node);
            return truncate(objectMapper.writeValueAsString(node));
        } catch (RuntimeException exception) {
            // A malformed or unsupported body must never be copied to logs.
            return truncate(UNAVAILABLE_VALUE);
        }
    }

    private static boolean isObjectArray(JsonNode node) {
        if (!node.isArray()) {
            return false;
        }
        for (JsonNode element : node) {
            if (!element.isObject()) {
                return false;
            }
        }
        return true;
    }

    private void redact(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = node.asObject();
            for (String name : new LinkedHashSet<>(object.propertyNames())) {
                if (redactedFields.contains(name.toLowerCase(Locale.ROOT))) {
                    object.put(name, REDACTED_VALUE);
                } else {
                    redact(object.get(name));
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = node.asArray();
            for (JsonNode child : array) {
                redact(child);
            }
        }
    }

    private String truncate(String value) {
        if (value.length() <= maxPreviewLength) {
            return value;
        }
        if (maxPreviewLength == 1) {
            return "…";
        }
        return value.substring(0, maxPreviewLength - 1) + "…";
    }

    private static Set<String> normalizeRedactedFields(Set<String> fields) {
        Set<String> normalized = new LinkedHashSet<>();
        Set<String> source = fields == null || fields.isEmpty() ? DEFAULT_REDACTED_FIELDS : fields;
        for (String field : source) {
            if (field == null || field.isBlank()) {
                throw new IllegalArgumentException("redactedFields must contain non-blank names");
            }
            normalized.add(field.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }
}
