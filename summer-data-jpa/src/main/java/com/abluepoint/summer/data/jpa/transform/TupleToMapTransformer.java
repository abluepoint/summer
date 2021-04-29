/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:TupleToMapTransformer.java
 * Date:2020-12-29 18:24:29
 */

package com.abluepoint.summer.data.jpa.transform;

import com.abluepoint.summer.data.jpa.util.JpaNameUtils;

import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TupleToMapTransformer implements TupleTransformer {

    private static TupleToMapTransformer transformer;
    protected TupleToMapTransformer(){

    }

    @Override
    public Object transformTuple(Tuple tuple, List<TupleElement<?>> elements) {
        Map<String, Object> result = buildMap(tuple, elements);
        return result;
    }

    protected Map<String, Object> buildMap(Tuple tuple, List<TupleElement<?>> elements) {
        int size = elements.size();
        Map<String,Object> result = new HashMap(size);
        String alias = null;
        for (int i = 0; i < size; i++) {
            alias = elements.get(i).getAlias();
            if (alias != null) {
                if (alias.contains("_")) {
                    alias = JpaNameUtils.camelCaseName(alias);
                }
                result.put(alias, tuple.get(i));
            }
        }
        return result;
    }

    public static TupleToMapTransformer getInstance() {
        if (transformer == null) {
            transformer = new TupleToMapTransformer();
        }
        return transformer;
    }
}
