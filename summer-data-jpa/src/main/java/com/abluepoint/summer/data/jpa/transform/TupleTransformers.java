package com.abluepoint.summer.data.jpa.transform;

public final class TupleTransformers {

    public static final TupleToMapTransformer TO_MAP = TupleToMapTransformer.getInstance();

    public static final TupleToListTransformer TO_LIST = TupleToListTransformer.getInstance();

    public static TupleToBasicTypeTransformer toBasicType(Class<?> type) {
        return new TupleToBasicTypeTransformer(type);
    }

    public static TupleToBeanTransformer toBean(Class<?> type) {
        return new TupleToBeanTransformer(type);
    }

    public static TupleToProxyTransformer toProxy(Class<?> type) {
        return new TupleToProxyTransformer(type);
    }

}
