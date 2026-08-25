package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.annotation.FieldSource;
import com.abluepoint.summer.mvc.annotation.Item;
import com.abluepoint.summer.mvc.filter.CachedBodyHttpServletRequest;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FieldValidationInterceptor implements HandlerInterceptor {

    private final StyleRegistry styleRegistry;
    private final int maxListSize;
    private final ObjectMapper objectMapper;

    public FieldValidationInterceptor(StyleRegistry styleRegistry, int maxListSize) {
        this(styleRegistry, maxListSize, null);
    }

    public FieldValidationInterceptor(StyleRegistry styleRegistry, int maxListSize, ObjectMapper objectMapper) {
        this.styleRegistry = Objects.requireNonNull(styleRegistry, "styleRegistry");
        if (maxListSize < 1) {
            throw new IllegalArgumentException("maxListSize must be positive");
        }
        this.maxListSize = maxListSize;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Set<Field> fields = AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(),
                Field.class
        );
        Set<FieldList> fieldLists = AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(),
                FieldList.class
        );
        validateFieldSourceConflicts(fields, fieldLists);
        FieldContract fieldContract = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                FieldContract.class
        );
        boolean rejectUnknownJson = fieldContract != null && fieldContract.rejectUnknownJson();
        JsonNode jsonBody = readJsonBody(request, fields, fieldLists, rejectUnknownJson);

        Map<String, Object> values = new LinkedHashMap<>();
        List<FieldValidationException.Violation> violations = new ArrayList<>();
        if (rejectUnknownJson) {
            rejectUnknownJsonFields(jsonBody, fields, fieldLists, violations);
        }
        for (Field field : fields) {
            readField(
                    request,
                    field.source(),
                    field.param(),
                    field.name(),
                    field.required(),
                    field.nullable(),
                    field.style(),
                    values,
                    violations,
                    jsonBody
            );
        }
        for (FieldList fieldList : fieldLists) {
            if (fieldList.items().length == 0) {
                throw new IllegalStateException("FieldList items must not be empty: " + fieldList.param());
            }
            if (fieldList.source() == FieldSource.JSON) {
                readJsonFieldList(jsonBody, fieldList, values, violations);
                continue;
            }
            String countParam = StringUtils.hasText(fieldList.countParam())
                    ? fieldList.countParam()
                    : fieldList.param() + ".count";
            Integer count = readCount(
                    readRawValue(request, fieldList.source(), countParam),
                    countParam,
                    violations
            );
            if (count == null) {
                continue;
            }
            int configuredMax = fieldList.maxSize() > 0 ? fieldList.maxSize() : maxListSize;
            int effectiveMax = Math.min(configuredMax, maxListSize);
            if (count > effectiveMax) {
                violations.add(new FieldValidationException.Violation(
                        countParam,
                        "must be at most " + effectiveMax
                ));
                continue;
            }

            List<Map<String, Object>> itemValues = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Map<String, Object> itemValuesAtIndex = new LinkedHashMap<>();
                for (Item item : fieldList.items()) {
                    String param = fieldList.param() + "[" + index + "]." + item.param();
                    readField(
                            request,
                            fieldList.source(),
                            param,
                            item.name(),
                            item.required(),
                            item.nullable(),
                            item.style(),
                            itemValuesAtIndex,
                            violations,
                            jsonBody
                    );
                }
                itemValues.add(itemValuesAtIndex);
            }
            String name = StringUtils.hasText(fieldList.name()) ? fieldList.name() : fieldList.param();
            values.put(name, List.copyOf(itemValues));
        }
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
        request.setAttribute(FieldValues.REQUEST_ATTRIBUTE, new FieldValues(values));
        return true;
    }

    private void validateFieldSourceConflicts(
            Iterable<Field> fields,
            Iterable<FieldList> fieldLists
    ) {
        Map<String, EnumSet<FieldSource>> sourcesByExportName = new LinkedHashMap<>();
        for (Field field : fields) {
            String exportName = StringUtils.hasText(field.name()) ? field.name() : field.param();
            sourcesByExportName.computeIfAbsent(
                    exportName,
                    ignored -> EnumSet.noneOf(FieldSource.class)
            ).add(field.source());
        }
        for (FieldList fieldList : fieldLists) {
            String exportName = StringUtils.hasText(fieldList.name())
                    ? fieldList.name()
                    : fieldList.param();
            sourcesByExportName.computeIfAbsent(
                    exportName,
                    ignored -> EnumSet.noneOf(FieldSource.class)
            ).add(fieldList.source());
        }
        for (Map.Entry<String, EnumSet<FieldSource>> entry : sourcesByExportName.entrySet()) {
            if (entry.getValue().size() > 1) {
                throw new IllegalStateException(
                        "Parameter contract export '" + entry.getKey()
                                + "' uses multiple sources: " + entry.getValue()
                );
            }
        }
    }

    private void readJsonFieldList(
            JsonNode jsonBody,
            FieldList fieldList,
            Map<String, Object> values,
            List<FieldValidationException.Violation> violations
    ) {
        JsonNode arrayNode = resolveJsonNode(jsonBody, fieldList.param());
        if (arrayNode == null || arrayNode.isNull()) {
            violations.add(new FieldValidationException.Violation(fieldList.param(), "is required"));
            return;
        }
        if (!arrayNode.isArray()) {
            violations.add(new FieldValidationException.Violation(
                    fieldList.param(),
                    "must be a JSON object array"
            ));
            return;
        }

        int configuredMax = fieldList.maxSize() > 0 ? fieldList.maxSize() : maxListSize;
        int effectiveMax = Math.min(configuredMax, maxListSize);
        if (arrayNode.size() > effectiveMax) {
            violations.add(new FieldValidationException.Violation(
                    fieldList.param(),
                    "must contain at most " + effectiveMax + " items"
            ));
            return;
        }

        int initialViolationCount = violations.size();
        List<Map<String, Object>> itemValues = new ArrayList<>(arrayNode.size());
        for (int index = 0; index < arrayNode.size(); index++) {
            JsonNode itemNode = arrayNode.get(index);
            String itemPath = fieldList.param() + "[" + index + "]";
            if (!itemNode.isObject()) {
                violations.add(new FieldValidationException.Violation(
                        itemPath,
                        "must be a JSON object"
                ));
                continue;
            }
            Map<String, Object> valuesAtIndex = new LinkedHashMap<>();
            for (Item item : fieldList.items()) {
                readJsonListItem(itemNode, itemPath, item, valuesAtIndex, violations);
            }
            itemValues.add(Collections.unmodifiableMap(new LinkedHashMap<>(valuesAtIndex)));
        }
        if (violations.size() == initialViolationCount) {
            String name = StringUtils.hasText(fieldList.name()) ? fieldList.name() : fieldList.param();
            values.put(name, List.copyOf(itemValues));
        }
    }

    private void readJsonListItem(
            JsonNode itemNode,
            String itemPath,
            Item item,
            Map<String, Object> values,
            List<FieldValidationException.Violation> violations
    ) {
        String fieldPath = itemPath + "." + item.param();
        JsonNode node = resolveJsonNode(itemNode, item.param());
        if (node == null) {
            if (item.required()) {
                violations.add(new FieldValidationException.Violation(fieldPath, "is required"));
            }
            return;
        }
        readJsonScalar(
                node,
                fieldPath,
                item.name(),
                item.nullable(),
                item.style(),
                values,
                violations
        );
    }

    private void readField(
            HttpServletRequest request,
            FieldSource source,
            String param,
            String name,
            boolean required,
            boolean nullable,
            String styleName,
            Map<String, Object> values,
            List<FieldValidationException.Violation> violations,
            JsonNode jsonBody
    ) {
        if (source == FieldSource.JSON) {
            readJsonField(jsonBody, param, name, required, nullable, styleName, values, violations);
            return;
        }
        String rawValue = readRawValue(request, source, param);
        if (!StringUtils.hasText(rawValue)) {
            if (required) {
                violations.add(new FieldValidationException.Violation(param, "is required"));
            }
            return;
        }

        Style style = styleRegistry.require(styleName);
        if (!style.validate(rawValue)) {
            violations.add(new FieldValidationException.Violation(param, "must be " + style.getDesc()));
            return;
        }
        try {
            values.put(StringUtils.hasText(name) ? name : param, style.getTypedObject(rawValue));
        } catch (RuntimeException exception) {
            violations.add(new FieldValidationException.Violation(param, "must be " + style.getDesc()));
        }
    }

    private void readJsonField(
            JsonNode jsonBody,
            String param,
            String name,
            boolean required,
            boolean nullable,
            String styleName,
            Map<String, Object> values,
            List<FieldValidationException.Violation> violations
    ) {
        JsonNode node = resolveJsonNode(jsonBody, param);
        if (node == null) {
            if (required) {
                violations.add(new FieldValidationException.Violation(param, "is required"));
            }
            return;
        }
        readJsonScalar(
                node,
                param,
                StringUtils.hasText(name) ? name : param,
                nullable,
                styleName,
                values,
                violations
        );
    }

    private void readJsonScalar(
            JsonNode node,
            String fieldPath,
            String exportName,
            boolean nullable,
            String styleName,
            Map<String, Object> values,
            List<FieldValidationException.Violation> violations
    ) {
        if (node.isNull()) {
            if (nullable) {
                values.put(exportName, null);
            } else {
                violations.add(new FieldValidationException.Violation(fieldPath, "must not be null"));
            }
            return;
        }
        if (!node.isString() && !node.isNumber() && !node.isBoolean()) {
            violations.add(new FieldValidationException.Violation(fieldPath, "must be a JSON scalar"));
            return;
        }

        Style style = styleRegistry.require(styleName);
        JsonScalarType jsonType = requiredJsonType(style.getParamType());
        if (!acceptsJsonType(node, jsonType)) {
            violations.add(new FieldValidationException.Violation(
                    fieldPath,
                    "must be a JSON " + jsonType.description
            ));
            return;
        }
        String rawValue = node.isString() ? node.asString() : node.toString();
        if (!style.validate(rawValue)) {
            violations.add(new FieldValidationException.Violation(fieldPath, "must be " + style.getDesc()));
            return;
        }
        try {
            values.put(exportName, style.getTypedObject(rawValue));
        } catch (RuntimeException exception) {
            violations.add(new FieldValidationException.Violation(fieldPath, "must be " + style.getDesc()));
        }
    }

    private JsonScalarType requiredJsonType(Class<?> paramType) {
        if (paramType == Object.class) {
            return JsonScalarType.SCALAR;
        }
        if (paramType == Boolean.class || paramType == boolean.class) {
            return JsonScalarType.BOOLEAN;
        }
        if (Number.class.isAssignableFrom(paramType)
                || paramType == byte.class
                || paramType == short.class
                || paramType == int.class
                || paramType == long.class
                || paramType == float.class
                || paramType == double.class) {
            return JsonScalarType.NUMBER;
        }
        return JsonScalarType.STRING;
    }

    private boolean acceptsJsonType(JsonNode node, JsonScalarType jsonType) {
        return switch (jsonType) {
            case BOOLEAN -> node.isBoolean();
            case NUMBER -> node.isNumber();
            case STRING -> node.isString();
            case SCALAR -> node.isString() || node.isNumber() || node.isBoolean();
        };
    }

    private enum JsonScalarType {
        STRING("string"),
        NUMBER("number"),
        BOOLEAN("boolean"),
        SCALAR("scalar");

        private final String description;

        JsonScalarType(String description) {
            this.description = description;
        }
    }

    private JsonNode readJsonBody(
            HttpServletRequest request,
            Iterable<Field> fields,
            Iterable<FieldList> fieldLists,
            boolean strictJsonContract
    ) {
        boolean requiresJson = strictJsonContract;
        for (Field field : fields) {
            requiresJson |= field.source() == FieldSource.JSON;
        }
        for (FieldList fieldList : fieldLists) {
            requiresJson |= fieldList.source() == FieldSource.JSON;
        }
        if (!requiresJson) {
            return null;
        }
        if (objectMapper == null) {
            throw new IllegalStateException("JSON Field validation requires an ObjectMapper");
        }
        CachedBodyHttpServletRequest cachedRequest = findCachedRequest(request);
        if (cachedRequest == null) {
            if (request.getContentLengthLong() == 0) {
                return null;
            }
            throw new IllegalStateException("JSON Field validation requires request-body caching");
        }
        try {
            return objectMapper.readTree(cachedRequest.body());
        } catch (JacksonException exception) {
            throw new FieldValidationException(List.of(
                    new FieldValidationException.Violation("$", "must be valid JSON")
            ));
        }
    }

    private void rejectUnknownJsonFields(
            JsonNode jsonBody,
            Iterable<Field> fields,
            Iterable<FieldList> fieldLists,
            List<FieldValidationException.Violation> violations
    ) {
        if (jsonBody == null || !jsonBody.isObject()) {
            return;
        }
        Set<String> declaredPaths = new LinkedHashSet<>();
        Map<String, FieldList> jsonFieldLists = new LinkedHashMap<>();
        for (Field field : fields) {
            if (field.source() == FieldSource.JSON) {
                declaredPaths.add(field.param());
            }
        }
        for (FieldList fieldList : fieldLists) {
            if (fieldList.source() == FieldSource.JSON) {
                declaredPaths.add(fieldList.param());
                jsonFieldLists.put(fieldList.param(), fieldList);
            }
        }
        rejectUnknownObjectProperties(jsonBody, "", declaredPaths, jsonFieldLists, violations);
    }

    private void rejectUnknownObjectProperties(
            JsonNode objectNode,
            String parentPath,
            Set<String> declaredPaths,
            Map<String, FieldList> jsonFieldLists,
            List<FieldValidationException.Violation> violations
    ) {
        for (Map.Entry<String, JsonNode> property : objectNode.properties()) {
            String path = parentPath.isEmpty()
                    ? property.getKey()
                    : parentPath + "." + property.getKey();
            boolean exactDeclaration = declaredPaths.contains(path);
            boolean declaredParent = declaredPaths.stream()
                    .anyMatch(declaredPath -> declaredPath.startsWith(path + "."));
            if (!exactDeclaration && !declaredParent) {
                violations.add(new FieldValidationException.Violation(path, "is not allowed"));
                continue;
            }
            FieldList fieldList = jsonFieldLists.get(path);
            if (fieldList != null && property.getValue().isArray()) {
                rejectUnknownJsonListItemFields(property.getValue(), path, fieldList, violations);
            }
            if (property.getValue().isObject() && declaredParent) {
                rejectUnknownObjectProperties(
                        property.getValue(),
                        path,
                        declaredPaths,
                        jsonFieldLists,
                        violations
                );
            }
        }
    }

    private void rejectUnknownJsonListItemFields(
            JsonNode arrayNode,
            String listPath,
            FieldList fieldList,
            List<FieldValidationException.Violation> violations
    ) {
        for (int index = 0; index < arrayNode.size(); index++) {
            JsonNode itemNode = arrayNode.get(index);
            if (!itemNode.isObject()) {
                continue;
            }
            String itemPath = listPath + "[" + index + "]";
            Set<String> declaredItemPaths = new LinkedHashSet<>();
            for (Item item : fieldList.items()) {
                declaredItemPaths.add(itemPath + "." + item.param());
            }
            rejectUnknownObjectProperties(
                    itemNode,
                    itemPath,
                    declaredItemPaths,
                    Map.of(),
                    violations
            );
        }
    }

    private CachedBodyHttpServletRequest findCachedRequest(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper) {
            if (current instanceof CachedBodyHttpServletRequest cachedRequest) {
                return cachedRequest;
            }
            current = wrapper.getRequest();
        }
        return current instanceof CachedBodyHttpServletRequest cachedRequest ? cachedRequest : null;
    }

    private JsonNode resolveJsonNode(JsonNode root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return root;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private String readRawValue(HttpServletRequest request, FieldSource source, String param) {
        return switch (source) {
            case PARAM -> request.getParameter(param);
            case PATH -> {
                Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (!(pathVariables instanceof Map<?, ?> variables)) {
                    yield null;
                }
                Object value = variables.get(param);
                yield value instanceof String stringValue ? stringValue : null;
            }
            case JSON -> throw new IllegalStateException("JSON fields must be read from the cached request body");
        };
    }

    private Integer readCount(
            String rawCount,
            String countParam,
            List<FieldValidationException.Violation> violations
    ) {
        if (!StringUtils.hasText(rawCount)) {
            violations.add(new FieldValidationException.Violation(countParam, "is required"));
            return null;
        }
        try {
            int count = Integer.parseInt(rawCount);
            if (count < 0) {
                throw new NumberFormatException("negative count");
            }
            return count;
        } catch (NumberFormatException exception) {
            violations.add(new FieldValidationException.Violation(
                    countParam,
                    "must be a non-negative integer"
            ));
            return null;
        }
    }
}
