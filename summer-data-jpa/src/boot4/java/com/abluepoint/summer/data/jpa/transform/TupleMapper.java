package com.abluepoint.summer.data.jpa.transform;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TupleMapper {

    private final ConversionService conversionService;

    public TupleMapper() {
        this(DefaultConversionService.getSharedInstance());
    }

    public TupleMapper(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    public Map<String, Object> toMap(Tuple tuple) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<TupleElement<?>> elements = tuple.getElements();
        for (int index = 0; index < elements.size(); index++) {
            TupleElement<?> element = elements.get(index);
            String alias = element.getAlias() == null ? "column" + index : element.getAlias();
            values.put(toCamelCase(alias), tuple.get(index));
        }
        return values;
    }

    public List<Object> toList(Tuple tuple) {
        List<Object> values = new ArrayList<>(tuple.getElements().size());
        for (int index = 0; index < tuple.getElements().size(); index++) {
            values.add(tuple.get(index));
        }
        return List.copyOf(values);
    }

    public <T> T map(Tuple tuple, Class<T> targetType) {
        if (Map.class.isAssignableFrom(targetType)) {
            return targetType.cast(toMap(tuple));
        }
        if (List.class.isAssignableFrom(targetType)) {
            return targetType.cast(toList(tuple));
        }

        Map<String, Object> values = toMap(tuple);
        if (isScalar(targetType) && values.size() == 1) {
            return convert(values.values().iterator().next(), targetType);
        }
        if (targetType.isInterface()) {
            return createProjection(values, targetType);
        }
        if (targetType.isRecord()) {
            return createRecord(values, targetType);
        }
        return createBean(values, targetType);
    }

    private <T> T createBean(Map<String, Object> values, Class<T> targetType) {
        try {
            T bean = targetType.getDeclaredConstructor().newInstance();
            BeanWrapper wrapper = new BeanWrapperImpl(bean);
            values.forEach((name, value) -> {
                if (wrapper.isWritableProperty(name)) {
                    wrapper.setPropertyValue(name, value);
                }
            });
            return bean;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Target type must have an accessible no-argument constructor: " + targetType, exception);
        }
    }

    private <T> T createRecord(Map<String, Object> values, Class<T> targetType) {
        try {
            RecordComponent[] components = targetType.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int index = 0; index < components.length; index++) {
                parameterTypes[index] = components[index].getType();
                arguments[index] = convert(values.get(components[index].getName()), components[index].getType());
            }
            Constructor<T> constructor = targetType.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unable to create record projection: " + targetType, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createProjection(Map<String, Object> values, Class<T> targetType) {
        InvocationHandler handler = (proxy, method, arguments) -> invokeProjection(proxy, method, arguments, values);
        return (T) Proxy.newProxyInstance(targetType.getClassLoader(), new Class<?>[]{targetType}, handler);
    }

    private Object invokeProjection(Object proxy, Method method, Object[] arguments, Map<String, Object> values) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> values.toString();
                case "hashCode" -> values.hashCode();
                case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
        String property = propertyName(method.getName());
        return convert(values.get(property), method.getReturnType());
    }

    private <T> T convert(Object value, Class<T> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        if (!conversionService.canConvert(value.getClass(), targetType)) {
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getName() + " to " + targetType.getName());
        }
        return conversionService.convert(value, targetType);
    }

    private static boolean isScalar(Class<?> type) {
        return type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || type.isEnum();
    }

    private static String propertyName(String methodName) {
        String value = methodName;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            value = methodName.substring(3);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            value = methodName.substring(2);
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    static String toCamelCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean capitalize = false;
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character == '_' || character == '-' || character == ' ') {
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
