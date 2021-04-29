package com.abluepoint.summer.data.jpa;

import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface JpaTemplate extends ApplicationContextAware {

    <T> T selectOne(String templateName,Class<T> resultType) throws Exception;

    <T> T selectOne(String templateName, Map<String,Object> parameter,Class<T> resultType) throws Exception;

    <E> List<E> selectList(String templateName,Class<E> resultType) throws Exception;

    <E> List<E> selectList(String templateName, Map<String,Object> parameter,Class<E> resultType) throws Exception;

    <E> Page<E> selectPage(String templateName,String countTemplateName, Pageable pageable,Class<E> resultType) throws Exception;

    <E> Page<E> selectPage(String templateName,String countTemplateName, Map<String,Object> parameter, Pageable pageable,Class<E> resultType) throws Exception;

}
