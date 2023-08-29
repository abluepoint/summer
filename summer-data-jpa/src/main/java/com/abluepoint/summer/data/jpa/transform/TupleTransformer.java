/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:TupleTransformer.java
 * Date:2020-12-29 18:15:29
 */

package com.abluepoint.summer.data.jpa.transform;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import java.util.List;

public interface TupleTransformer {

    public Object transformTuple(Tuple tuple, List<TupleElement<?>> elements);

}
