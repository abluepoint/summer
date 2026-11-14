package com.abluepoint.summer.sample.resource;

import java.util.Locale;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value={"permissionCode"})
public record InterfaceResourceDescriptor(
        String httpMethod,
        String normalizedPath,
        String handler,
        String module,
        ResourceType resourceType,
        SensitiveLevel sensitiveLevel,
        boolean configurable,
        String contextId,
        String source,
        Map<String, List<String>> conditions,
        String configurationPolicy
) {

    public InterfaceResourceDescriptor {
        contextId = requireText(contextId, "contextId");
        source = requireText(source, "source");
        configurationPolicy = requireText(configurationPolicy, "configurationPolicy");
        Map<String, List<String>> ordered = new TreeMap<>();
        conditions.forEach((key, values) -> { if (!values.isEmpty()) ordered.put(key, values.stream().distinct().sorted().toList()); });
        conditions = Collections.unmodifiableMap(ordered);
        httpMethod = requireText(httpMethod, "httpMethod").toUpperCase(Locale.ROOT);
        normalizedPath = normalizePath(normalizedPath);
        handler = requireText(handler, "handler");
        module = requireText(module, "module");
        resourceType = Objects.requireNonNull(resourceType, "resourceType");
        sensitiveLevel = Objects.requireNonNull(sensitiveLevel, "sensitiveLevel");
    }

    public static InterfaceResourceDescriptor mvc(
            String httpMethod,
            String path,
            String handler,
            String module,
            ResourceType resourceType,
            SensitiveLevel sensitiveLevel,
            boolean configurable
    ) {
        return new InterfaceResourceDescriptor(
                httpMethod, path, handler, module, resourceType, sensitiveLevel, configurable,
                "application", "MVC", Map.of(), resourceType == ResourceType.BUSINESS ? "MANAGED_ROLE" : "COMPONENT_OWNED"
        );
    }

    public String key() {
        return contextId + " " + httpMethod + " " + normalizedPath + " " + conditions;
    }

    private static String normalizePath(String value) {
        String path = requireText(value, "normalizedPath").trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        path = path.replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
