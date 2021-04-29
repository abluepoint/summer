/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerTupleConverter.java
 * Date:2020-12-31 18:08:31
 */

package com.abluepoint.summer.data.jpa.transform;

import org.springframework.beans.BeanUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.TypeUtils;

import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import java.util.*;
import java.util.stream.Collectors;

public class SummerTupleConverter implements Converter<Object, Object> {
    private final ReturnedType type;

    /**
     * Creates a new {@link SummerTupleConverter} for the given {@link ReturnedType}.
     *
     * @param type must not be {@literal null}.
     */
    public SummerTupleConverter(ReturnedType type) {

        Assert.notNull(type, "Returned type must not be null!");

        this.type = type;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.core.convert.converter.Converter#convert(java.lang.Object)
     */
    @Override
    public Object convert(Object source) {

        // 如果不是Tuple类型
        if (!(source instanceof Tuple)) {
            return source;
        }

        // 安全的类型强制转换
        Tuple tuple = (Tuple) source;
        List<TupleElement<?>> elements = tuple.getElements();
        if (BeanUtils.isSimpleValueType(type.getReturnedType())) {
            return TupleTransformers.toBasicType(type.getReturnedType()).transformTuple(tuple, elements);
        } else if (ClassUtils.isAssignable(Map.class, type.getReturnedType())) {
            return TupleTransformers.TO_MAP.transformTuple(tuple, elements);
        } else if (ClassUtils.isAssignable(List.class, type.getReturnedType())) {
            return TupleTransformers.TO_LIST.transformTuple(tuple, elements);
        } else if (type.getReturnedType().isInterface()) {
            return TupleTransformers.toProxy(type.getReturnedType()).transformTuple(tuple, elements);
        } else {
            return TupleTransformers.toBean(type.getReturnedType()).transformTuple(tuple, elements);
        }

    }

}