/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:DefaultJpaSqlTemplate.java
 * Date:2020-12-29 18:26:29
 */

package com.abluepoint.summer.data.jpa.template;


import com.abluepoint.summer.data.jpa.transform.TupleTransformer;
import com.abluepoint.summer.data.jpa.transform.TupleTransformers;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.query.AbstractJpaSqlTemplate;
import org.springframework.util.ClassUtils;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author bluepoint
 */
public class JpaSqlTemplateDefault extends AbstractJpaSqlTemplate {

    private EntityManager entityManager;

    public JpaSqlTemplateDefault(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Override
    protected <E> Query createQuery(String sql, Class<E> resultType) {
        return getEntityManager().createNativeQuery(sql, resultType);
    }

    @Override
    public <T> T selectOne(String queryString, Class<T> resultType) {
        return selectOne(queryString, Collections.emptyMap(), resultType);
    }

    @Override
    @SuppressWarnings("unckecked")
    public <T> T selectOne(String queryString, Map<String, Object> parameter, Class<T> resultType) {

        //如果有@Entity注解
        if (isAnnotationEntity(resultType)) {
            Query query = createQuery(queryString, resultType);
            setParameters(queryString, parameter, query);
            return (T) query.getSingleResult();
        }

        Query query = createQuery(queryString, Tuple.class);
        setParameters(queryString, parameter, query);
        Tuple tuple = (Tuple) query.getSingleResult();

        return handleTuple(tuple, resultType);

    }

    private <T> T handleTuple(Tuple tuple, Class<T> resultType) {
        return handleTuple(tuple, resultType, tuple.getElements());
    }

    @SuppressWarnings("unckecked")
    private <T> T handleTuple(Tuple tuple, Class<T> resultType, List<TupleElement<?>> elements) {
        boolean isBasic = ClassUtils.isPrimitiveOrWrapper(resultType) || resultType.isAssignableFrom(String.class);
        TupleTransformer transformer;
        if (isBasic) {
            transformer = TupleTransformers.toBasicType(resultType);
        } else if (resultType.isAssignableFrom(Map.class)) {
            transformer = TupleTransformers.TO_MAP;
        } else if (resultType.isAssignableFrom(List.class)) {
            transformer = TupleTransformers.TO_LIST;
        } else if (resultType.isInterface()) {
            transformer = TupleTransformers.toProxy(resultType);
        } else {
            transformer = TupleTransformers.toBean(resultType);
        }

        return (T) transformer.transformTuple(tuple, elements);
    }

    private <T> boolean isAnnotationEntity(Class<T> resultType) {
        return AnnotationUtils.isAnnotationDeclaredLocally(Entity.class, resultType);
    }

    @Override
    public <E> List<E> selectList(String queryString, Class<E> resultType) {
        return selectList(queryString, Collections.emptyMap(), resultType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> selectList(String queryString, Map<String, Object> parameter, Class<E> resultType) {
        if (isAnnotationEntity(resultType)) {
            Query query = createQuery(queryString, resultType);
            setParameters(queryString, parameter, query);
            return query.getResultList();
        }

        Query query = createQuery(queryString, Tuple.class);
        setParameters(queryString, parameter, query);
        List<Tuple> tuples = query.getResultList();

        return handleTuples(tuples, resultType);

    }

    @Override
    public <E> Page<E> selectPage(String queryString, String countString, Pageable pageable, Class<E> resultType) {
        return selectPage(queryString, countString, Collections.emptyMap(), pageable, resultType);
    }

    @Override
    @SuppressWarnings("unckecked")
    public <E> Page<E> selectPage(String queryString, String countString, Map<String, Object> parameter, Pageable pageable, Class<E> resultType) {
        if (isAnnotationEntity(resultType)) {
            Query query = getSelectPageQuery(queryString, parameter, pageable, resultType);

            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());

            List<E> resultList = query.getResultList();
            return new PageImpl<>(resultList);
        }

        Query query = createQuery(queryString, Tuple.class);
        setParameters(queryString, parameter, query);
        if (pageable.isPaged()) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }
        List<Tuple> tuples = query.getResultList();

        List<E> resultList = handleTuples(tuples, resultType);
        return new PageImpl<>(resultList);
    }


    private <E> List<E> handleTuples(List<Tuple> tuples, Class<E> resultType) {
        List<E> resultList;
        if (tuples.size() > 0) {
            resultList = new ArrayList<>(tuples.size());
            List<TupleElement<?>> elements = tuples.get(0).getElements();
            for (Tuple tuple : tuples) {
                resultList.add(handleTuple(tuple, resultType, elements));
            }
        } else {
            resultList = Collections.emptyList();
        }
        return resultList;
    }

}
