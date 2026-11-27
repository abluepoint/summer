package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.annotation.FieldSource;
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
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FieldValidationInterceptor implements HandlerInterceptor {

    private static final boolean SPRING_SECURITY_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.web.csrf.CsrfToken", FieldValidationInterceptor.class.getClassLoader());

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
                handlerMethod.getMethod(), Field.class
        );
        Set<FieldList> fieldLists = AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(), FieldList.class
        );
        boolean fieldContract = !fields.isEmpty() || !fieldLists.isEmpty()
                || AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), FieldContract.class) != null;
        JsonNode jsonBody = readJsonBody(request, fields, fieldLists, fieldContract);

        Map<String, Object> values = new LinkedHashMap<>();
        List<SummerException.Violation> violations = new ArrayList<>();
        if (fieldContract) {
            rejectUnknownRequestFields(request, jsonBody, fields, fieldLists, violations);
        }
        for (Field field : fields) {
            readField(request, field, values, violations, jsonBody);
        }
        for (FieldList fieldList : fieldLists) {
            validateFieldListConfiguration(fieldList);
            EnumSet<FieldSource> presentSources = presentListSources(request, jsonBody, fieldList.param());
            if (presentSources.size() > 1) {
                violations.add(new SummerException.Violation(
                        fieldList.param(), "must be provided by one input source"
                ));
            } else if (presentSources.isEmpty()) {
                if (fieldList.required()) {
                    violations.add(new SummerException.Violation(fieldList.param(), "is required"));
                }
            } else if (presentSources.contains(FieldSource.JSON)) {
                readJsonFieldList(jsonBody, fieldList, values, violations);
            } else {
                readParameterFieldList(request, fieldList, values, violations);
            }
        }
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
        request.setAttribute(FieldValues.REQUEST_ATTRIBUTE, new FieldValues(values));
        return true;
    }

    private void validateFieldListConfiguration(FieldList fieldList) {
        int effectiveMax = fieldList.maxSize() == -1 ? maxListSize : fieldList.maxSize();
        if (fieldList.items().length == 0) {
            throw new IllegalStateException("FieldList items must not be empty: " + fieldList.param());
        }
        if (fieldList.minSize() < 1 || fieldList.maxSize() == 0 || fieldList.maxSize() < -1
                || fieldList.minSize() > effectiveMax) {
            throw new IllegalStateException(
                    "Invalid FieldList size configuration: " + fieldList.param()
                            + " minSize=" + fieldList.minSize()
                            + " maxSize=" + fieldList.maxSize()
                            + " effectiveMaxSize=" + effectiveMax
            );
        }
    }

    private int effectiveMax(FieldList fieldList) {
        return fieldList.maxSize() == -1 ? maxListSize : fieldList.maxSize();
    }

    private EnumSet<FieldSource> presentListSources(
            HttpServletRequest request,
            JsonNode jsonBody,
            String param
    ) {
        EnumSet<FieldSource> sources = EnumSet.noneOf(FieldSource.class);
        if (hasIndexedParameters(request, param)) {
            sources.add(FieldSource.PARAM);
        }
        if (jsonBody != null && resolveJsonNode(jsonBody, param) != null) {
            sources.add(FieldSource.JSON);
        }
        return sources;
    }

    private boolean hasIndexedParameters(HttpServletRequest request, String param) {
        String prefix = param + "[";
        return request.getParameterMap().keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    private void readJsonFieldList(
            JsonNode jsonBody,
            FieldList fieldList,
            Map<String, Object> values,
            List<SummerException.Violation> violations
    ) {
        JsonNode arrayNode = resolveJsonNode(jsonBody, fieldList.param());
        if (arrayNode == null) {
            if (fieldList.required()) {
                violations.add(new SummerException.Violation(fieldList.param(), "is required"));
            }
            return;
        }
        if (arrayNode.isNull()) {
            if (fieldList.nullable()) {
                values.put(exportName(fieldList), null);
            } else {
                violations.add(new SummerException.Violation(fieldList.param(), "must not be null"));
            }
            return;
        }
        if (!arrayNode.isArray()) {
            violations.add(new SummerException.Violation(
                    fieldList.param(), "must be a JSON object array"
            ));
            return;
        }
        if (!validateListSize(fieldList, arrayNode.size(), violations)) {
            return;
        }

        int initialViolationCount = violations.size();
        List<Map<String, Object>> itemValues = new ArrayList<>(arrayNode.size());
        for (int index = 0; index < arrayNode.size(); index++) {
            JsonNode itemNode = arrayNode.get(index);
            String itemPath = fieldList.param() + "[" + index + "]";
            if (!itemNode.isObject()) {
                violations.add(new SummerException.Violation(itemPath, "must be a JSON object"));
                continue;
            }
            Map<String, Object> valuesAtIndex = new LinkedHashMap<>();
            for (Field field : fieldList.items()) {
                readJsonListField(itemNode, itemPath, field, valuesAtIndex, violations);
            }
            itemValues.add(Collections.unmodifiableMap(new LinkedHashMap<>(valuesAtIndex)));
        }
        if (violations.size() == initialViolationCount) {
            values.put(exportName(fieldList), List.copyOf(itemValues));
        }
    }

    private void readParameterFieldList(
            HttpServletRequest request,
            FieldList fieldList,
            Map<String, Object> values,
            List<SummerException.Violation> violations
    ) {
        Pattern itemPattern = Pattern.compile(
                Pattern.quote(fieldList.param()) + "\\[(\\d+)]\\.(.+)"
        );
        Map<Integer, Boolean> indexes = new TreeMap<>();
        for (String parameter : request.getParameterMap().keySet()) {
            Matcher matcher = itemPattern.matcher(parameter);
            if (matcher.matches()) {
                try {
                    indexes.put(Integer.parseInt(matcher.group(1)), Boolean.TRUE);
                } catch (NumberFormatException exception) {
                    violations.add(new SummerException.Violation(
                            parameter, "index must be a valid non-negative integer"
                    ));
                }
            }
        }
        if (!violations.isEmpty()) {
            return;
        }
        if (indexes.isEmpty()) {
            violations.add(new SummerException.Violation(
                    fieldList.param(), "must contain indexed item fields"
            ));
            return;
        }
        int expected = 0;
        for (Integer index : indexes.keySet()) {
            if (index != expected) {
                violations.add(new SummerException.Violation(
                        fieldList.param(), "indexes must start at 0 and be contiguous"
                ));
                return;
            }
            expected++;
        }
        int count = indexes.size();
        if (!validateListSize(fieldList, count, violations)) {
            return;
        }

        List<Map<String, Object>> itemValues = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Map<String, Object> valuesAtIndex = new LinkedHashMap<>();
            for (Field field : fieldList.items()) {
                String parameter = fieldList.param() + "[" + index + "]." + field.param();
                readParameterField(request, parameter, field, valuesAtIndex, violations);
            }
            itemValues.add(Collections.unmodifiableMap(new LinkedHashMap<>(valuesAtIndex)));
        }
        values.put(exportName(fieldList), List.copyOf(itemValues));
    }

    private boolean validateListSize(
            FieldList fieldList,
            int size,
            List<SummerException.Violation> violations
    ) {
        if (size < fieldList.minSize()) {
            violations.add(new SummerException.Violation(
                    fieldList.param(), "must contain at least " + fieldList.minSize() + " items"
            ));
            return false;
        }
        int max = effectiveMax(fieldList);
        if (size > max) {
            violations.add(new SummerException.Violation(
                    fieldList.param(), "must contain at most " + max + " items"
            ));
            return false;
        }
        return true;
    }

    private String exportName(FieldList fieldList) {
        return StringUtils.hasText(fieldList.name()) ? fieldList.name() : fieldList.param();
    }

    private void readJsonListField(
            JsonNode itemNode,
            String itemPath,
            Field field,
            Map<String, Object> values,
            List<SummerException.Violation> violations
    ) {
        String fieldPath = itemPath + "." + field.param();
        JsonNode node = resolveJsonNode(itemNode, field.param());
        if (node == null) {
            if (field.required()) {
                violations.add(new SummerException.Violation(fieldPath, "is required"));
            }
            return;
        }
        readJsonScalar(
                node, fieldPath, exportName(field), field.nullable(), field.style(), values, violations
        );
    }

    private String exportName(Field field) {
        return StringUtils.hasText(field.name()) ? field.name() : field.param();
    }

    private void readParameterField(
            HttpServletRequest request,
            String parameter,
            Field field,
            Map<String, Object> values,
            List<SummerException.Violation> violations
    ) {
        String rawValue = request.getParameter(parameter);
        if (rawValue == null) {
            if (field.required()) {
                violations.add(new SummerException.Violation(parameter, "is required"));
            }
            return;
        }
        Style style = styleRegistry.require(field.style());
        if (!style.validate(rawValue)) {
            violations.add(new SummerException.Violation(parameter, "must be " + style.getDesc()));
            return;
        }
        try {
            values.put(exportName(field), style.getTypedObject(rawValue));
        } catch (RuntimeException exception) {
            violations.add(new SummerException.Violation(parameter, "must be " + style.getDesc()));
        }
    }

    private void readField(
            HttpServletRequest request,
            Field field,
            Map<String, Object> values,
            List<SummerException.Violation> violations,
            JsonNode jsonBody
    ) {
        EnumSet<FieldSource> presentSources = presentSources(request, jsonBody, field.param());
        if (presentSources.size() > 1) {
            violations.add(new SummerException.Violation(
                    field.param(),
                    "must be provided by one input source"
            ));
            return;
        }
        if (presentSources.isEmpty()) {
            if (field.required()) {
                violations.add(new SummerException.Violation(field.param(), "is required"));
            }
            return;
        }
        FieldSource source = presentSources.iterator().next();
        if (source == FieldSource.JSON) {
            readJsonField(
                    jsonBody,
                    field.param(),
                    field.name(),
                    field.required(),
                    field.nullable(),
                    field.style(),
                    values,
                    violations
            );
            return;
        }
        String rawValue = readRawValue(request, source, field.param());
        if (rawValue == null) {
            if (field.required()) {
                violations.add(new SummerException.Violation(field.param(), "is required"));
            }
            return;
        }

        Style style = styleRegistry.require(field.style());
        if (!style.validate(rawValue)) {
            violations.add(new SummerException.Violation(field.param(), "must be " + style.getDesc()));
            return;
        }
        try {
            values.put(StringUtils.hasText(field.name()) ? field.name() : field.param(), style.getTypedObject(rawValue));
        } catch (RuntimeException exception) {
            violations.add(new SummerException.Violation(field.param(), "must be " + style.getDesc()));
        }
    }

    private EnumSet<FieldSource> presentSources(
            HttpServletRequest request,
            JsonNode jsonBody,
            String param
    ) {
        EnumSet<FieldSource> sources = EnumSet.noneOf(FieldSource.class);
        if (request.getParameterMap().containsKey(param)) {
            sources.add(FieldSource.PARAM);
        }
        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables instanceof Map<?, ?> variables && variables.containsKey(param)) {
            sources.add(FieldSource.PATH);
        }
        if (jsonBody != null && resolveJsonNode(jsonBody, param) != null) {
            sources.add(FieldSource.JSON);
        }
        return sources;
    }

    private void readJsonField(
            JsonNode jsonBody,
            String param,
            String name,
            boolean required,
            boolean nullable,
            String styleName,
            Map<String, Object> values,
            List<SummerException.Violation> violations
    ) {
        JsonNode node = resolveJsonNode(jsonBody, param);
        if (node == null) {
            if (required) {
                violations.add(new SummerException.Violation(param, "is required"));
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
            List<SummerException.Violation> violations
    ) {
        if (node.isNull()) {
            if (nullable) {
                values.put(exportName, null);
            } else {
                violations.add(new SummerException.Violation(fieldPath, "must not be null"));
            }
            return;
        }
        if (!node.isString() && !node.isNumber() && !node.isBoolean()) {
            violations.add(new SummerException.Violation(fieldPath, "must be a JSON scalar"));
            return;
        }

        Style style = styleRegistry.require(styleName);
        JsonScalarType jsonType = requiredJsonType(style.getParamType());
        if (!acceptsJsonType(node, jsonType)) {
            violations.add(new SummerException.Violation(
                    fieldPath,
                    "must be a JSON " + jsonType.description
            ));
            return;
        }
        String rawValue = node.isString() ? node.asString() : node.toString();
        if (!style.validate(rawValue)) {
            violations.add(new SummerException.Violation(fieldPath, "must be " + style.getDesc()));
            return;
        }
        try {
            values.put(exportName, style.getTypedObject(rawValue));
        } catch (RuntimeException exception) {
            violations.add(new SummerException.Violation(fieldPath, "must be " + style.getDesc()));
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
        if (!strictJsonContract || !isJsonBodyCandidate(request)) {
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
            if (cachedRequest.body().length == 0) {
                return null;
            }
            return objectMapper.readTree(cachedRequest.body());
        } catch (JacksonException exception) {
            throw new FieldValidationException(List.of(
                    new SummerException.Violation("$", "must be valid JSON")
            ));
        }
    }

    private boolean isJsonBodyCandidate(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            return contentType.toLowerCase().contains("json");
        }
        return request.getContentLengthLong() > 0;
    }

    private void rejectUnknownRequestFields(
            HttpServletRequest request,
            JsonNode jsonBody,
            Iterable<Field> fields,
            Iterable<FieldList> fieldLists,
            List<SummerException.Violation> violations
    ) {
        Set<String> declaredParameters = new LinkedHashSet<>();
        List<FieldList> lists = new ArrayList<>();
        for (Field field : fields) {
            declaredParameters.add(field.param());
        }
        for (FieldList fieldList : fieldLists) {
            lists.add(fieldList);
        }
        for (String parameter : request.getParameterMap().keySet()) {
            boolean declared = declaredParameters.contains(parameter);
            if (!declared) {
                declared = lists.stream().anyMatch(fieldList -> isDeclaredListParameter(parameter, fieldList));
            }
            if (!declared && !(SPRING_SECURITY_PRESENT && CsrfParameters.matches(request, parameter))) {
                violations.add(new SummerException.Violation(parameter, "is not allowed"));
            }
        }

        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables instanceof Map<?, ?> variables) {
            for (Object key : variables.keySet()) {
                if (!(key instanceof String parameter) || !declaredParameters.contains(parameter)) {
                    violations.add(new SummerException.Violation(String.valueOf(key), "is not allowed"));
                }
            }
        }
        rejectUnknownJsonFields(jsonBody, fields, lists, violations);
    }

    // Spring Security publishes the active token on the request. Its form parameter belongs to the filter.
    private static final class CsrfParameters {
        static boolean matches(HttpServletRequest request, String parameter) {
            return request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token
                    && CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                    && parameter.equals(token.getParameterName());
        }
    }

    private boolean isDeclaredListParameter(String parameter, FieldList fieldList) {
        String prefix = fieldList.param() + "[";
        if (!parameter.startsWith(prefix)) {
            return false;
        }
        Matcher matcher = Pattern.compile(Pattern.quote(fieldList.param()) + "\\[(\\d+)]\\.(.+)")
                .matcher(parameter);
        if (!matcher.matches()) {
            return false;
        }
        String childPath = matcher.group(2);
        for (Field field : fieldList.items()) {
            if (field.param().equals(childPath)) {
                return true;
            }
        }
        return false;
    }

    private void rejectUnknownJsonFields(
            JsonNode jsonBody,
            Iterable<Field> fields,
            Iterable<FieldList> fieldLists,
            List<SummerException.Violation> violations
    ) {
        if (jsonBody == null || !jsonBody.isObject()) {
            return;
        }
        Set<String> declaredPaths = new LinkedHashSet<>();
        Map<String, FieldList> jsonFieldLists = new LinkedHashMap<>();
        for (Field field : fields) {
            declaredPaths.add(field.param());
        }
        for (FieldList fieldList : fieldLists) {
            declaredPaths.add(fieldList.param());
            jsonFieldLists.put(fieldList.param(), fieldList);
        }
        rejectUnknownObjectProperties(jsonBody, "", declaredPaths, jsonFieldLists, violations);
    }

    private void rejectUnknownObjectProperties(
            JsonNode objectNode,
            String parentPath,
            Set<String> declaredPaths,
            Map<String, FieldList> jsonFieldLists,
            List<SummerException.Violation> violations
    ) {
        for (Map.Entry<String, JsonNode> property : objectNode.properties()) {
            String path = parentPath.isEmpty() ? property.getKey() : parentPath + "." + property.getKey();
            boolean exactDeclaration = declaredPaths.contains(path);
            boolean declaredParent = declaredPaths.stream().anyMatch(declaredPath -> declaredPath.startsWith(path + "."));
            if (!exactDeclaration && !declaredParent) {
                violations.add(new SummerException.Violation(path, "is not allowed"));
                continue;
            }
            FieldList fieldList = jsonFieldLists.get(path);
            if (fieldList != null && property.getValue().isArray()) {
                rejectUnknownJsonListItemFields(property.getValue(), path, fieldList, violations);
            }
            if (property.getValue().isObject() && declaredParent) {
                rejectUnknownObjectProperties(property.getValue(), path, declaredPaths, jsonFieldLists, violations);
            }
        }
    }

    private void rejectUnknownJsonListItemFields(
            JsonNode arrayNode,
            String listPath,
            FieldList fieldList,
            List<SummerException.Violation> violations
    ) {
        for (int index = 0; index < arrayNode.size(); index++) {
            JsonNode itemNode = arrayNode.get(index);
            if (!itemNode.isObject()) {
                continue;
            }
            Set<String> paths = new LinkedHashSet<>();
            for (Field field : fieldList.items()) {
                paths.add(listPath + "[" + index + "]." + field.param());
            }
            rejectUnknownObjectProperties(itemNode, listPath + "[" + index + "]", paths, Map.of(), violations);
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

}
