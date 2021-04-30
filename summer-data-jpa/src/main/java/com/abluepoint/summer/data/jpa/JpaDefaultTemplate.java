package com.abluepoint.summer.data.jpa;

import com.abluepoint.summer.data.jpa.template.JpaSqlTemplate;
import com.abluepoint.summer.data.jpa.template.JpaTemplateSource;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JpaDefaultTemplate implements JpaTemplate {

    private JpaSqlTemplate jpaSqlTemplate;

    private JpaTemplateSource jpaTemplateSource;

    private static ApplicationContext applicationContext;

    public JpaDefaultTemplate(JpaSqlTemplate jpaSqlTemplate, JpaTemplateSource jpaTemplateSource) {
        this.jpaSqlTemplate = jpaSqlTemplate;
        this.jpaTemplateSource = jpaTemplateSource;
    }

    //    private boolean isNative(String templateName) {
    //        return templateName.startsWith("sql-");
    //    }

    @Override
    public <T> T selectOne(String templateName, Class<T> resultType) throws Exception {
        return selectOne(templateName, Collections.emptyMap(), resultType);
    }

    @Override
    public <T> T selectOne(String templateName, Map<String, Object> parameter, Class<T> resultType) throws Exception {
        String sql = jpaTemplateSource.getTemplateSql(templateName, parameter);
        return jpaSqlTemplate.selectOne(sql, parameter, resultType);
    }

    @Override
    public <E> List<E> selectList(String templateName, Class<E> resultType) throws Exception {
        return selectList(templateName, Collections.emptyMap(), resultType);
    }

    @Override
    public <E> List<E> selectList(String templateName, Map<String, Object> parameter, Class<E> resultType) throws Exception {
        String sql = jpaTemplateSource.getTemplateSql(templateName, parameter);
        return jpaSqlTemplate.selectList(sql, parameter, resultType);
    }

//    @Override
//    public void select(String templateName, ResultHandler handler) throws Exception {
//        select(templateName, Collections.emptyMap(), handler);
//    }
//
//    @Override
//    public void select(String templateName, Map<String, Object> parameter, ResultHandler handler) throws Exception {
//        String sql = jpaTemplateSource.getTemplate(templateName, parameter);
//        jpaSqlTemplate.select(sql, parameter, handler);
//    }
//
//    @Override
//    public void select(String templateName, Map<String, Object> parameter, Pageable pageable, ResultHandler handler) throws Exception {
//        String sql = jpaTemplateSource.getTemplate(templateName, parameter);
//        jpaSqlTemplate.select(sql, parameter, pageable, handler);
//    }

    @Override
    public <E> Page<E> selectPage(String templateName, String countTemplateName, Map<String, Object> parameter, Pageable pageable, Class<E> resultType) throws Exception {
        String sql = jpaTemplateSource.getTemplateSql(templateName, parameter);
        String countSql = jpaTemplateSource.getTemplateSql(templateName, parameter);

        //	    boolean isNative = isNative(templateName, countTemplateName);

        return jpaSqlTemplate.selectPage(sql, countSql, parameter, pageable, resultType);

    }

    @Override
    public <E> Page<E> selectPage(String templateName, String countTemplateName, Pageable pageable, Class<E> resultType) throws Exception {
        return selectPage(templateName,countTemplateName,Collections.emptyMap(),pageable,resultType);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    //	private boolean isNative(String templateName, String countTemplateName) throws Exception {
    //		boolean isNative = isNative(templateName);
    //		boolean isCountNative = isNative(countTemplateName);
    //		if (isNative != isCountNative) {
    //			throw new Exception("Sql type must be same");
    //		}
    //		return isNative;
    //	}

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
