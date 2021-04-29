/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:TupleToBeanTransformer.java
 * Date:2020-12-29 18:16:29
 */

package com.abluepoint.summer.data.jpa.transform;

import com.abluepoint.summer.data.jpa.util.JpaNameUtils;
import org.hibernate.HibernateException;
import org.hibernate.property.access.internal.PropertyAccessStrategyBasicImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyChainedImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyFieldImpl;
import org.hibernate.property.access.internal.PropertyAccessStrategyMapImpl;
import org.hibernate.property.access.spi.Setter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import javax.persistence.Tuple;
import javax.persistence.TupleElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TupleToBeanTransformer implements TupleTransformer {

    private static final ConversionService conversionService = DefaultConversionService.getSharedInstance();
    private final Class<?> resultClass;

    private boolean isInitialized;
    private String[] aliases;
    private Setter[] setters;
    private Class<?>[] settersType;

    public TupleToBeanTransformer(Class<?> resultClass) {
        if (resultClass == null) {
            throw new IllegalArgumentException("resultClass cannot be null");
        }
        isInitialized = false;
        this.resultClass = resultClass;
    }

    @Override
    public Object transformTuple(Tuple tuple, List<TupleElement<?>> elements) {
        Object result;

        try {
            if (!isInitialized) {
                initialize(elements);
            }

            result = resultClass.newInstance();

            Class<?> parameterType = null;
            for (int i = 0; i < aliases.length; i++) {
                if (setters[i] != null) {

                    if (tuple.get(i) != null) {
                        parameterType = settersType[i];
                        if (conversionService.canConvert(tuple.get(i).getClass(), parameterType)) {
                            setters[i].set(result, conversionService.convert(tuple.get(i), parameterType), null);
                        } else {
                            setters[i].set(result, tuple.get(i), null);
                        }
                    }

                }
            }
        } catch (InstantiationException e) {
            throw new HibernateException("Could not instantiate resultclass: " + resultClass.getName());
        } catch (IllegalAccessException e) {
            throw new HibernateException("Could not instantiate resultclass: " + resultClass.getName());
        }

        return result;
    }

    private void initialize(List<TupleElement<?>> elements) {
        PropertyAccessStrategyChainedImpl propertyAccessStrategy = new PropertyAccessStrategyChainedImpl(PropertyAccessStrategyBasicImpl.INSTANCE, PropertyAccessStrategyFieldImpl.INSTANCE, PropertyAccessStrategyMapImpl.INSTANCE);

        int size = elements.size();
        aliases = new String[size];
        setters = new Setter[size];
        settersType = new Class<?>[size];

        String alias = null;
        TupleElement te = null;
        Setter currentSetter = null;
        for (int i = 0; i < size; i++) {
            te = elements.get(i);
            alias = te.getAlias();
            if (alias != null) {
                if (alias.contains("_")) {
                    alias = JpaNameUtils.camelCaseName(alias);
                }
                aliases[i] = alias;
                currentSetter = propertyAccessStrategy.buildPropertyAccess(resultClass, alias).getSetter();
                setters[i] = currentSetter;

                settersType[i] = getParameterType(currentSetter, alias);
            }
        }
        isInitialized = true;
    }

    private Class<?> getParameterType(Setter setter, String alias) {
        Method method = setter.getMethod();
        if (method == null) {
            throw new HibernateException((new StringBuilder()).append("Could not set property for alias: ").append(alias).toString());
        }

        Class<?>[] parameterTypes = setter.getMethod().getParameterTypes();
        if (parameterTypes.length != 1) {
            throw new HibernateException((new StringBuilder()).append("Could not set property for alias: ").append(alias).toString());
        }
        return parameterTypes[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TupleToBeanTransformer that = (TupleToBeanTransformer) o;

        if (!resultClass.equals(that.resultClass)) {
            return false;
        }
        if (!Arrays.equals(aliases, that.aliases)) {
            return false;
        }

        return true;
    }

}
