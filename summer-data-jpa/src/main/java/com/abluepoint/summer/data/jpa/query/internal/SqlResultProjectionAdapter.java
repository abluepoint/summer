package com.abluepoint.summer.data.jpa.query.internal;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.jpa.util.TupleBackedMap;
import org.springframework.data.mapping.PreferredConstructor;
import org.springframework.data.mapping.model.PreferredConstructorDiscoverer;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Internal collaborator of JpaSqlTemplate, public only for cross-package query execution. */
public final class SqlResultProjectionAdapter {

    private final ProjectionFactory projectionFactory;
    private final ConversionService conversionService;

    public SqlResultProjectionAdapter() {
        this(new SpelAwareProxyProjectionFactory(), DefaultConversionService.getSharedInstance());
    }

    SqlResultProjectionAdapter(ProjectionFactory projectionFactory, ConversionService conversionService) {
        this.projectionFactory = projectionFactory;
        this.conversionService = conversionService;
    }

    public <T> T project(Tuple tuple, Class<T> targetType) {
        if (Tuple.class == targetType) {
            return targetType.cast(tuple);
        }

        Map<String, Object> values = toCamelCaseMap(tuple);
        if (Map.class == targetType) {
            return targetType.cast(values);
        }
        if (Collection.class.isAssignableFrom(targetType) || targetType.isArray()) {
            throw unsupportedTarget(targetType);
        }
        if (values.size() == 1 && canProjectScalar(values.values().iterator().next(), targetType)) {
            return convert(values.values().iterator().next(), targetType);
        }
        if (targetType.isInterface()) {
            return projectionFactory.createProjection(targetType, values);
        }
        return instantiateDto(values, targetType);
    }

    private Map<String, Object> toCamelCaseMap(Tuple tuple) {
        Map<String, Object> tupleValues = new TupleBackedMap(tuple);
        Map<String, Object> values = new LinkedHashMap<>(tupleValues.size());
        List<TupleElement<?>> elements = tuple.getElements();
        for (int index = 0; index < elements.size(); index++) {
            String alias = elements.get(index).getAlias();
            Object value = alias == null ? tuple.get(index) : tupleValues.get(alias);
            alias = alias == null ? "column" + index : alias;
            String normalizedAlias = toCamelCase(alias);
            if (values.containsKey(normalizedAlias)) {
                throw new IllegalArgumentException("Duplicate SQL result alias after camelCase normalization: " + normalizedAlias);
            }
            values.put(normalizedAlias, value);
        }
        return Collections.unmodifiableMap(values);
    }

    private <T> T instantiateDto(Map<String, Object> values, Class<T> targetType) {
        if (targetType.isArray()
                || targetType.isEnum()
                || Modifier.isAbstract(targetType.getModifiers())) {
            throw unsupportedTarget(targetType);
        }

        PreferredConstructor<T, ?> preferredConstructor = PreferredConstructorDiscoverer.discover(targetType);
        if (preferredConstructor == null || preferredConstructor.isNoArgConstructor()) {
            throw unsupportedTarget(targetType);
        }

        Constructor<T> constructor = preferredConstructor.getConstructor();
        var parameters = preferredConstructor.getParameters();
        Object[] arguments = new Object[parameters.size()];
        for (int index = 0; index < parameters.size(); index++) {
            var parameter = parameters.get(index);
            if (!parameter.hasName()) {
                throw new IllegalArgumentException(
                        "Constructor parameter names are required for SQL result projection: " + targetType.getName()
                );
            }
            String parameterName = parameter.getRequiredName();
            if (!values.containsKey(parameterName)) {
                throw new IllegalArgumentException(
                        "SQL result does not contain constructor parameter '" + parameterName + "' for " + targetType.getName()
                );
            }
            arguments[index] = convertValue(values.get(parameterName), parameter.getRawType());
        }

        try {
            if (!constructor.trySetAccessible()) {
                throw new IllegalArgumentException("SQL result constructor is not accessible: " + targetType.getName());
            }
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unable to create SQL result projection: " + targetType.getName(), exception);
        }
    }

    private boolean canProjectScalar(Object value, Class<?> targetType) {
        Class<?> resolvedTargetType = ClassUtils.resolvePrimitiveIfNecessary(targetType);
        return SimpleTypeHolder.DEFAULT.isSimpleType(resolvedTargetType)
                && ((value == null && !targetType.isPrimitive())
                || (value != null && (resolvedTargetType.isInstance(value)
                || conversionService.canConvert(value.getClass(), resolvedTargetType))));
    }

    private <T> T convert(Object value, Class<T> targetType) {
        @SuppressWarnings("unchecked")
        T converted = (T) convertValue(value, targetType);
        return converted;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Cannot project SQL NULL to primitive " + targetType.getName());
            }
            return null;
        }

        Class<?> resolvedTargetType = ClassUtils.resolvePrimitiveIfNecessary(targetType);
        if (resolvedTargetType.isInstance(value)) {
            return value;
        }
        if (!conversionService.canConvert(value.getClass(), resolvedTargetType)) {
            throw new IllegalArgumentException(
                    "Cannot convert SQL result value from " + value.getClass().getName() + " to " + targetType.getName()
            );
        }
        return conversionService.convert(value, resolvedTargetType);
    }

    private static IllegalArgumentException unsupportedTarget(Class<?> targetType) {
        return new IllegalArgumentException(
                "Unsupported SQL result projection type: " + targetType.getName()
                        + "; use a scalar, Map, record/constructor DTO, interface projection, or Tuple"
        );
    }

    static String toCamelCase(String value) {
        boolean hasSeparator = value.chars().anyMatch(character ->
                character == '_' || character == '-' || Character.isWhitespace(character));
        if (!hasSeparator) {
            if (value.isEmpty()) {
                return value;
            }
            boolean allUpperCase = value.chars()
                    .filter(Character::isLetter)
                    .allMatch(Character::isUpperCase);
            if (allUpperCase) {
                return value.toLowerCase(Locale.ROOT);
            }
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }

        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = false;
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character == '_' || character == '-' || Character.isWhitespace(character)) {
                capitalize = true;
            } else if (capitalize) {
                result.append(Character.toUpperCase(character));
                capitalize = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
