/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ContextUtils.java
 * Date:2020-03-28 00:20:28
 */

package com.abluepoint.summer.common.util;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class ContextUtils {

    private static final ConversionService conversionService = DefaultConversionService.getSharedInstance();

    public static <T> T getValue(Map<String, Object> data, String key, Class<T> type) {
        Object object = data.get(key);
        return conversionService.convert(object,type);
    }

    public static Map<String, Object> toMap(Object source) throws BeansException {
        return toMap(source, null, (String[]) null);
    }

    public static Map<String, Object> toMap(Object source, @Nullable String... ignoreProperties) throws BeansException {
        return toMap(source, null, ignoreProperties);
    }

    public static Map<String, Object> toMap(Object source, Class<?> editable) throws BeansException {
        return toMap(source, editable, (String[]) null);
    }

    public static Map<String, Object> toMap(Object source, Class<?> editable, @Nullable String... ignoreProperties) throws BeansException {
        Class<?> actualEditable = getActualEditableClass(source, editable);
        PropertyDescriptor[] sourcePds = BeanUtils.getPropertyDescriptors(actualEditable);
        List<String> ignoreList = (ignoreProperties != null ? Arrays.asList(ignoreProperties) : null);
        HashMap<String, Object> map = new HashMap<>(sourcePds.length);

        Object value;
        Method readMethod;

        for (PropertyDescriptor sourcePd : sourcePds) {
            readMethod = sourcePd.getReadMethod();
            if (readMethod != null && (ignoreList == null || !ignoreList.contains(sourcePd.getName()))) {
                try {
                    if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
                        readMethod.setAccessible(true);
                    }
                    value = readMethod.invoke(source);
                    map.put(sourcePd.getName(), value);
                } catch (Throwable ex) {
                    throw new FatalBeanException("Could not copy property '" + sourcePd.getName() + "' from source to map", ex);
                }

            }
        }

        return map;
    }

    private static Class<?> getActualEditableClass(Object source, Class<?> editable) {
        Class<?> actualEditable = source.getClass();
        if (editable != null) {
            if (!editable.isInstance(source)) {
                throw new IllegalArgumentException("Target class [" + source.getClass().getName() + "] not assignable to Editable class [" + editable.getName() + "]");
            }
            actualEditable = editable;
        }
        return actualEditable;
    }

    public static void mapProperties(Map<String, Object> source, Object target) throws BeansException {
        mapProperties(source, target, null, (String[]) null);
    }

    public static void mapProperties(Map<String, Object> source, Object target, @Nullable String... ignoreProperties) throws BeansException {
        mapProperties(source, target, null, ignoreProperties);
    }

    public static void mapProperties(Map<String, Object> source, Object target, Class<?> editable) throws BeansException {
        mapProperties(source, target, editable, (String[]) null);
    }

    public static void mapProperties(Map<String, Object> source, Object target, @Nullable Class<?> editable, @Nullable String... ignoreProperties) {
        Class<?> actualEditable = getActualEditableClass(target, editable);

        List<String> ignoreList = (ignoreProperties != null ? Arrays.asList(ignoreProperties) : null);
        PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(actualEditable);

        Object value;
        Method writeMethod;
        for (PropertyDescriptor targetPd : targetPds) {
            writeMethod = targetPd.getWriteMethod();
            if (writeMethod != null && (ignoreList == null || !ignoreList.contains(targetPd.getName()))) {
                try {
                    value = source.get(targetPd.getName());
                    if (value != null && !ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], value.getClass())) {
                        value = conversionService.convert(value, writeMethod.getParameterTypes()[0]);
                    }
                    if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                        writeMethod.setAccessible(true);
                    }
                    writeMethod.invoke(target, value);
                } catch (Throwable ex) {
                    throw new FatalBeanException("Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                }
            }
        }
    }

    public static void mapProperties(Map<String, Object> source, Object target, Function<String, String> nameResolver, @Nullable Class<?> editable, @Nullable String... ignoreProperties) {
        Class<?> actualEditable = getActualEditableClass(target, editable);

        List<String> ignoreList = (ignoreProperties != null ? Arrays.asList(ignoreProperties) : null);
        PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(actualEditable);

        Object value;
        Method writeMethod;
        for (PropertyDescriptor targetPd : targetPds) {
            writeMethod = targetPd.getWriteMethod();
            if (writeMethod != null && (ignoreList == null || !ignoreList.contains(targetPd.getName()))) {
                try {
                    value = source.get(nameResolver.apply(targetPd.getName()));
                    if (value != null && !ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], value.getClass())) {
                        value = conversionService.convert(value, writeMethod.getParameterTypes()[0]);
                    }
                    if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                        writeMethod.setAccessible(true);
                    }
                    writeMethod.invoke(target, value);
                } catch (Throwable ex) {
                    throw new FatalBeanException("Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                }
            }
        }
    }

    public static void copyProperties(Object source, Object target) throws BeansException {
        copyProperties(source, target, null, (String[]) null);
    }

    public static void copyProperties(Object source, Object target, @Nullable String... ignoreProperties) throws BeansException {
        copyProperties(source, target, null, ignoreProperties);
    }

    public static void copyProperties(Object source, Object target, Class<?> editable) throws BeansException {
        copyProperties(source, target, editable, (String[]) null);
    }

    public static void copyProperties(Object source, Object target, @Nullable Class<?> editable, @Nullable String... ignoreProperties) throws BeansException {
        Assert.notNull(source, "Source must not be null");
        Assert.notNull(target, "Target must not be null");

        Class<?> actualEditable = getActualEditableClass(target, editable);
        PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(actualEditable);
        List<String> ignoreList = (ignoreProperties != null ? Arrays.asList(ignoreProperties) : null);

        Object value;
        Method writeMethod;
        PropertyDescriptor sourcePd;
        Method readMethod;
        for (PropertyDescriptor targetPd : targetPds) {
            writeMethod = targetPd.getWriteMethod();
            if (writeMethod != null && (ignoreList == null || !ignoreList.contains(targetPd.getName()))) {
                sourcePd = BeanUtils.getPropertyDescriptor(source.getClass(), targetPd.getName());
                if (sourcePd != null) {
                    readMethod = sourcePd.getReadMethod();
                    if (readMethod != null) {
                        try {
                            if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
                                readMethod.setAccessible(true);
                            }
                            value = readMethod.invoke(source);
                            if (value != null && !ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], readMethod.getReturnType())) {
                                value = conversionService.convert(value, writeMethod.getParameterTypes()[0]);
                            }
                            if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                                writeMethod.setAccessible(true);
                            }
                            writeMethod.invoke(target, value);
                        } catch (Throwable ex) {
                            throw new FatalBeanException("Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                        }
                    }
                }
            }
        }
    }

    public static void copyProperties(Object source, Object target, Function<String, String> nameResolver, @Nullable Class<?> editable, @Nullable String... ignoreProperties) throws BeansException {

        Assert.notNull(source, "Source must not be null");
        Assert.notNull(target, "Target must not be null");

        Class<?> actualEditable = getActualEditableClass(target, editable);
        PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(actualEditable);
        List<String> ignoreList = (ignoreProperties != null ? Arrays.asList(ignoreProperties) : null);

        Object value;
        Method writeMethod;
        PropertyDescriptor sourcePd;
        Method readMethod;
        for (PropertyDescriptor targetPd : targetPds) {
            writeMethod = targetPd.getWriteMethod();
            if (writeMethod != null && (ignoreList == null || !ignoreList.contains(targetPd.getName()))) {
                sourcePd = BeanUtils.getPropertyDescriptor(source.getClass(), nameResolver.apply(targetPd.getName()));
                if (sourcePd != null) {
                    readMethod = sourcePd.getReadMethod();
                    if (readMethod != null) {
                        try {
                            if (!Modifier.isPublic(readMethod.getDeclaringClass().getModifiers())) {
                                readMethod.setAccessible(true);
                            }
                            value = readMethod.invoke(source);
                            if (value != null && !ClassUtils.isAssignable(writeMethod.getParameterTypes()[0], readMethod.getReturnType())) {
                                value = conversionService.convert(value, writeMethod.getParameterTypes()[0]);
                            }
                            if (!Modifier.isPublic(writeMethod.getDeclaringClass().getModifiers())) {
                                writeMethod.setAccessible(true);
                            }
                            writeMethod.invoke(target, value);
                        } catch (Throwable ex) {
                            throw new FatalBeanException("Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                        }
                    }
                }
            }
        }
    }
}
