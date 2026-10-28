package com.abluepoint.summer.data.jpa.query.internal;

import com.abluepoint.summer.data.jpa.query.spi.SqlTemplateSource;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClasspathSqlTemplateSource implements SqlTemplateSource {

    private static final Pattern TEMPLATE_MARKER = Pattern.compile("^--\\s*([A-Za-z][A-Za-z0-9_.-]*)\\s*$");

    private final ResourceLoader resourceLoader;

    public ClasspathSqlTemplateSource(ResourceLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    @Override
    public String load(String location, String templateName) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("SQL template resource does not exist: " + location);
        }

        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            Map<String, String> templates = splitTemplates(content);
            if (templates.isEmpty()) {
                if (templateName == null || templateName.isBlank()) {
                    return content.strip();
                }
                throw new IllegalArgumentException("No named SQL templates found in " + location);
            }
            String selected = templates.get(templateName);
            if (selected == null) {
                throw new IllegalArgumentException("SQL template '" + templateName + "' not found in " + location);
            }
            return selected;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read SQL template resource: " + location, exception);
        }
    }

    static Map<String, String> splitTemplates(String content) {
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String current = null;

        for (String line : content.split("\\R", -1)) {
            if (line.startsWith("---")) {
                continue;
            }
            Matcher marker = TEMPLATE_MARKER.matcher(line.strip());
            if (marker.matches()) {
                current = marker.group(1);
                if (sections.containsKey(current)) {
                    throw new IllegalArgumentException("Duplicate SQL template '" + current + "'");
                }
                sections.put(current, new StringBuilder());
            } else if (current != null) {
                sections.get(current).append(line).append('\n');
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        sections.forEach((name, sql) -> result.put(name, sql.toString().strip()));
        return result;
    }
}
