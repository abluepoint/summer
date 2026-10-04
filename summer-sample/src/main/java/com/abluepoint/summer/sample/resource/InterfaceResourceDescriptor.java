package com.abluepoint.summer.sample.resource;

import java.util.Locale;
import java.util.Objects;

public record InterfaceResourceDescriptor(
        String httpMethod,
        String normalizedPath,
        String handler,
        String module,
        ResourceType resourceType,
        SensitiveLevel sensitiveLevel,
        String permissionCode,
        boolean configurable
) {

    public InterfaceResourceDescriptor {
        httpMethod = requireText(httpMethod, "httpMethod").toUpperCase(Locale.ROOT);
        normalizedPath = normalizePath(normalizedPath);
        handler = requireText(handler, "handler");
        module = requireText(module, "module");
        resourceType = Objects.requireNonNull(resourceType, "resourceType");
        sensitiveLevel = Objects.requireNonNull(sensitiveLevel, "sensitiveLevel");
        permissionCode = permissionCode == null || permissionCode.isBlank()
                ? httpMethod + ":" + normalizedPath
                : permissionCode.trim();
    }

    public static InterfaceResourceDescriptor mvc(
            String httpMethod,
            String path,
            String handler,
            String module,
            ResourceType resourceType,
            SensitiveLevel sensitiveLevel,
            String permissionCode,
            boolean configurable
    ) {
        return new InterfaceResourceDescriptor(
                httpMethod, path, handler, module, resourceType, sensitiveLevel, permissionCode, configurable
        );
    }

    public String key() {
        return httpMethod + " " + normalizedPath;
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
