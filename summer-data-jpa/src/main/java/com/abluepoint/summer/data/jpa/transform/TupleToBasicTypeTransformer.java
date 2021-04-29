/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:TupleToBasicTypeTransformer.java
 * Date:2020-12-29 18:15:29
 */

package com.abluepoint.summer.data.jpa.transform;

import com.abluepoint.summer.data.jpa.JpaSummerException;
import com.abluepoint.summer.data.jpa.JpaSummerRuntimeException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import java.util.List;

public class TupleToBasicTypeTransformer implements TupleTransformer {

    private static ConversionService conversionService = DefaultConversionService.getSharedInstance();

    private Class<?> resultType;

    public TupleToBasicTypeTransformer(Class<?> resultType){
        this.resultType = resultType;
    }

    @Override
    public Object transformTuple(Tuple tuple, List<TupleElement<?>> elements) {
        if (elements.size() != 1) {
            throw new JpaSummerRuntimeException("Can not cast multi column to basic type!");
        }
        TupleElement te = elements.get(0);

        if (te.getJavaType() == resultType) {
            return tuple.get(0);
        } else {
            return conversionService.convert(tuple.get(0), resultType);
        }
    }
}
