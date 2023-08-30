/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:AbstractJpaSqlTemplate.java
 * Date:2020-12-29 17:43:29
 */

package org.springframework.data.jpa.repository.query;

import com.abluepoint.summer.data.jpa.template.JpaSqlTemplate;
import jakarta.persistence.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

/**
 * 执行查询的模板方法类,其它功能不依赖它
 */
public abstract class AbstractJpaSqlTemplate implements JpaSqlTemplate {
    /**
     * 根据sql设置用到的参数
     *
     * @param sql
     * @param paramMap
     * @param query
     */
    protected void setParameters(String sql, Map<String, Object> paramMap, Query query) {
        DeclaredQuery declaredQuery = DeclaredQuery.of(sql, true);
        List<StringQuery.ParameterBinding> list = declaredQuery.getParameterBindings();
        String name;
        for (StringQuery.ParameterBinding binding : list) {
            name = binding.getName();
            query.setParameter(name, paramMap.get(name));
        }
    }

    private void setParameters(DeclaredQuery declaredQuery,Map<String, Object> paramMap, Query query) {
        List<StringQuery.ParameterBinding> list = declaredQuery.getParameterBindings();
        String name;
        for (StringQuery.ParameterBinding binding : list) {
            name = binding.getName();
            query.setParameter(name, paramMap.get(name));
        }
    }

    protected  <E> Query getSelectPageQuery(String queryString, Map<String, Object> parameter, Pageable pageable, Class<E> resultType) {
        DeclaredQuery declaredQuery = DeclaredQuery.of(queryString,true);
        Sort sort = pageable.getSortOr(Sort.unsorted());
        String sortedQueryString = QueryUtils.applySorting(queryString, sort, declaredQuery.getAlias());

        Query query = createQuery(sortedQueryString, resultType);
        setParameters(declaredQuery, parameter, query);
        return query;
    }

    protected abstract <E> Query createQuery(String sortedQueryString, Class<E> resultType);
}
