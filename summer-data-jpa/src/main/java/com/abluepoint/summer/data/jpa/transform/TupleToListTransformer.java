/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:TupleToListTransformer.java
 * Date:2020-12-29 18:40:29
 */

package com.abluepoint.summer.data.jpa.transform;

import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * todo: abluepoint 确定内部结构
 */
public class TupleToListTransformer implements TupleTransformer {

    private static TupleToListTransformer tupleToListTransformer;

    private TupleToListTransformer(){

    }

    @Override
    public Object transformTuple(Tuple tuple, List<TupleElement<?>> elements) {
        return Arrays.asList(tuple);
    }

    public static TupleToListTransformer getInstance() {
        if (tupleToListTransformer == null) {
            tupleToListTransformer = new TupleToListTransformer();
        }
        return tupleToListTransformer;
    }
}
