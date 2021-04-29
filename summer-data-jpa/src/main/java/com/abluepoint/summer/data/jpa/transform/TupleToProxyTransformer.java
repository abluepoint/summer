/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:TupleToProxyTransformer.java
 * Date:2020-12-31 18:09:31
 */

package com.abluepoint.summer.data.jpa.transform;

import org.springframework.lang.Nullable;

import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import java.util.*;
import java.util.stream.Collectors;

public class TupleToProxyTransformer implements TupleTransformer {

    private static final DynamicProxyProjectionFactory factory = new DynamicProxyProjectionFactory();
    private Class<?> resultType;

    public TupleToProxyTransformer(Class<?> resultType){
        this.resultType = resultType;
    }

    @Override
    public Object transformTuple(Tuple tuple, List<TupleElement<?>> elements) {
        return factory.createProjection(resultType, new TupleBackedMap(tuple));
    }

    /**
     * A {@link Map} implementation which delegates all calls to a {@link Tuple}. Depending on the provided
     * {@link Tuple} implementation it might return the same value for various keys of which only one will appear in the
     * key/entry set.
     *
     * @author Jens Schauder
     */
    private static class TupleBackedMap implements Map<String, Object> {

        private static final String UNMODIFIABLE_MESSAGE = "A TupleBackedMap cannot be modified.";

        private final Tuple tuple;

        TupleBackedMap(Tuple tuple) {
            this.tuple = tuple;
        }

        @Override
        public int size() {
            return tuple.getElements().size();
        }

        @Override
        public boolean isEmpty() {
            return tuple.getElements().isEmpty();
        }

        /**
         * If the key is not a {@code String} or not a key of the backing {@link Tuple} this returns {@code false}.
         * Otherwise this returns {@code true} even when the value from the backing {@code Tuple} is {@code null}.
         *
         * @param key the key for which to get the value from the map.
         * @return whether the key is an element of the backing tuple.
         */
        @Override
        public boolean containsKey(Object key) {

            try {
                tuple.get((String) key);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        @Override
        public boolean containsValue(Object value) {
            return Arrays.asList(tuple.toArray()).contains(value);
        }

        /**
         * If the key is not a {@code String} or not a key of the backing {@link Tuple} this returns {@code null}.
         * Otherwise the value from the backing {@code Tuple} is returned, which also might be {@code null}.
         *
         * @param key the key for which to get the value from the map.
         * @return the value of the backing {@link Tuple} for that key or {@code null}.
         */
        @Override
        @Nullable
        public Object get(Object key) {

            if (!(key instanceof String)) {
                return null;
            }

            try {
                return tuple.get((String) key);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        @Override
        public Object put(String key, Object value) {
            throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
        }

        @Override
        public Object remove(Object key) {
            throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
        }

        @Override
        public void putAll(Map<? extends String, ?> m) {
            throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException(UNMODIFIABLE_MESSAGE);
        }

        @Override
        public Set<String> keySet() {

            return tuple.getElements().stream() //
                    .map(TupleElement::getAlias) //
                    .collect(Collectors.toSet());
        }

        @Override
        public Collection<Object> values() {
            return Arrays.asList(tuple.toArray());
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {

            return tuple.getElements().stream() //
                    .map(e -> new HashMap.SimpleEntry<String, Object>(e.getAlias(), tuple.get(e))) //
                    .collect(Collectors.toSet());
        }
    }



}
