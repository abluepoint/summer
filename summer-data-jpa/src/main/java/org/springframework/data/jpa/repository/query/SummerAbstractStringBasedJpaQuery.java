/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerAbstractStringBasedJpaQuery.java
 * Date:2020-12-31 18:59:31
 */

package org.springframework.data.jpa.repository.query;

import com.abluepoint.summer.data.jpa.JpaDefaultTemplate;
import com.abluepoint.summer.data.jpa.template.JpaTemplateSource;
import com.abluepoint.summer.data.jpa.transform.SummerTupleConverter;
import com.abluepoint.summer.data.jpa.util.JpaNameUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.*;

public class SummerAbstractStringBasedJpaQuery extends AbstractJpaQuery {
//    private final DeclaredQuery query;
//    private final DeclaredQuery countQuery;

    private final String queryString;
    private final String countQueryString;

    private final QueryMethodEvaluationContextProvider evaluationContextProvider;
    private final SpelExpressionParser parser;
    private final QueryParameterSetter.QueryMetadataCache metadataCache = new QueryParameterSetter.QueryMetadataCache();

    private final Map<String, DeclaredQuery> queryMap = new HashMap<>(16);
    private final Map<String, DeclaredQuery> countQueryMap = new HashMap<>(16);
    private final Map<String, ParameterBinder> parameterBinderMap = new HashMap<>(16);

    /**
     * Creates a new {@link SummerAbstractStringBasedJpaQuery} from the given {@link JpaQueryMethod}, {@link EntityManager} and
     * query {@link String}.
     *
     * @param method                    must not be {@literal null}.
     * @param em                        must not be {@literal null}.
     * @param queryString               must not be {@literal null}.
     * @param evaluationContextProvider must not be {@literal null}.
     * @param parser                    must not be {@literal null}.
     */
    public SummerAbstractStringBasedJpaQuery(JpaQueryMethod method, EntityManager em, String queryString,
                                             QueryMethodEvaluationContextProvider evaluationContextProvider, SpelExpressionParser parser) {

        super(method, em);

        Assert.hasText(queryString, "Query string must not be null or empty!");
        Assert.notNull(evaluationContextProvider, "ExpressionEvaluationContextProvider must not be null!");
        Assert.notNull(parser, "Parser must not be null!");
        this.queryString = queryString;
        this.countQueryString = method.getCountQuery();
        this.evaluationContextProvider = evaluationContextProvider;
        this.parser = parser;

        /**
         * abluepoint: 动态的创建query,当前确定不了query
         */
//        this.query = new ExpressionBasedStringQuery(queryString, method.getEntityInformation(), parser);
//        DeclaredQuery countQuery = query.deriveCountQuery(method.getCountQuery(), method.getCountQueryProjection());
//        this.countQuery = ExpressionBasedStringQuery.from(countQuery, method.getEntityInformation(), parser);
//        Assert.isTrue(method.isNativeQuery() || !query.usesJdbcStyleParameters(),
//                "JDBC style parameters (?) are not supported for JPA queries.");
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.jpa.repository.query.AbstractJpaQuery#doCreateQuery(JpaParametersParameterAccessor)
     */
    @Override
    public Query doCreateQuery(JpaParametersParameterAccessor accessor) {
        SummerJpaParametersParameterAccessor parameterAccessor = (SummerJpaParametersParameterAccessor) accessor;
        String querySql = getQuerySql(queryString, parameterAccessor.getNamedParameterMap());

        // sql to hash
        DeclaredQuery declaredQuery = queryMap.get(querySql);
        if (declaredQuery == null) {
            JpaQueryMethod method = getQueryMethod();
            declaredQuery = new ExpressionBasedStringQuery(querySql, method.getEntityInformation(), parser);
            Assert.isTrue(method.isNativeQuery() || !declaredQuery.usesJdbcStyleParameters(), "JDBC style parameters (?) are not supported for JPA queries.");
            queryMap.put(querySql, declaredQuery);
        }

        String sortedQueryString = QueryUtils.applySorting(querySql, accessor.getSort(), declaredQuery.getAlias());
        ResultProcessor processor = getQueryMethod().getResultProcessor().withDynamicProjection(accessor);

        Query query = createJpaQuery(sortedQueryString, processor.getReturnedType(), declaredQuery);

        QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(sortedQueryString, query);

        ParameterBinder parameterBinder = parameterBinderMap.get(sortedQueryString);
        if (parameterBinder == null) {
            parameterBinder = createBinder(declaredQuery);
            parameterBinderMap.put(countQueryString, parameterBinder);
        }

        // it is ok to reuse the binding contained in the ParameterBinder although we create a new query String because the
        // parameters in the query do not change.
        return parameterBinder.bindAndPrepare(query, metadata, accessor);
    }

    /*
     * abluepoint: 不能确定的创建了,
     * (non-Javadoc)
     * @see org.springframework.data.jpa.repository.query.AbstractJpaQuery#createBinder(JpaParametersParameterAccessor)
     */
    @Override
    protected final ParameterBinder createBinder() {

//        return ParameterBinderFactory.createQueryAwareBinder(getQueryMethod().getParameters(), query, parser,
//                evaluationContextProvider);
        return null;
    }

    /**
     * abluepoint: 动态创建ParameterBinder
     *
     * @param query
     * @return
     */
    protected ParameterBinder createBinder(DeclaredQuery query) {
        return ParameterBinderFactory.createQueryAwareBinder(getQueryMethod().getParameters(), query, parser, evaluationContextProvider);
    }

    /*
     * abluepoint:countQuery use freemarker
     * (non-Javadoc)
     * @see org.springframework.data.jpa.repository.query.AbstractJpaQuery#doCreateCountQuery(JpaParametersParameterAccessor)
     */
    @Override
    protected Query doCreateCountQuery(JpaParametersParameterAccessor accessor) {
        SummerJpaParametersParameterAccessor parameterAccessor = (SummerJpaParametersParameterAccessor) accessor;
        String countQuerySql = getQuerySql(countQueryString, parameterAccessor.getNamedParameterMap());

        DeclaredQuery declaredCountQuery = countQueryMap.get(countQuerySql);
        if (declaredCountQuery == null) {
            JpaQueryMethod method = getQueryMethod();
            DeclaredQuery middleCountQuery = DeclaredQuery.of(countQuerySql);
            declaredCountQuery = ExpressionBasedStringQuery.from(middleCountQuery, method.getEntityInformation(), parser);
            countQueryMap.put(countQuerySql, declaredCountQuery);
        }

        String countQueryString = declaredCountQuery.getQueryString();

        EntityManager em = getEntityManager();

        Query query = getQueryMethod().isNativeQuery() //
                ? em.createNativeQuery(countQueryString) //
                : em.createQuery(countQueryString, Long.class);

        QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(countQueryString, query);

        ParameterBinder parameterBinder = parameterBinderMap.get(countQueryString);
        if (parameterBinder == null) {
            parameterBinder = createBinder(declaredCountQuery);
            parameterBinderMap.put(countQueryString, parameterBinder);
        }

        parameterBinder.bind(metadata.withQuery(query), accessor, QueryParameterSetter.ErrorHandling.LENIENT);

        return query;
    }

    /**
     * @return the query
     */
//    public DeclaredQuery getQuery() {
//        return query;
//    }

    /**
     * @return the countQuery
     */
//    public DeclaredQuery getCountQuery() {
//        return countQuery;
//    }

    /**
     * Creates an appropriate JPA query from an {@link EntityManager} according to the current {@link AbstractJpaQuery}
     * type.
     */
    protected Query createJpaQuery(String queryString, ReturnedType returnedType, DeclaredQuery declaredQuery) {

        EntityManager em = getEntityManager();

        if (declaredQuery.hasConstructorExpression() || declaredQuery.isDefaultProjection()) {
            return em.createQuery(queryString);
        }

        Class<?> typeToRead = getTypeToRead(returnedType);

        return typeToRead == null //
                ? em.createQuery(queryString) //
                : em.createQuery(queryString, typeToRead);
    }


    /**
     * abluepoint: 从 JpaDefaultTemplate 获取queryString
     *
     * @param queryString
     * @param parameterMap
     * @return
     */
    protected String getQuerySql(String queryString, Map<String, Object> parameterMap) {
        String querySql = null;
        if (queryString.equals("")) {

        } else if (queryString.startsWith("${")) {
            ApplicationContext context = JpaDefaultTemplate.getApplicationContext();
            if (context == null) {
                throw new RuntimeException("It is not run in spring or not config AppContextUtil!");
            }
            JpaTemplateSource jpaTemplateSource = context.getBean("jpaTemplateSource", JpaTemplateSource.class);

            String templateName = JpaNameUtils.getTemplateName(queryString);

            try {
                querySql = jpaTemplateSource.getTemplateSql(templateName, parameterMap);
            } catch (Exception e) {
                throw new RuntimeException(new StringBuilder("Find template error,template name is \"").append(templateName).append("\"").toString(), e);
            }
        } else {
            querySql = queryString;
        }
        return querySql;
    }

    @Override
    public Object execute(Object[] parameters) {
        return doExecute(getExecution(), parameters);
    }

    @Nullable
    private Object doExecute(JpaQueryExecution execution, Object[] values) {
        QueryMethod method = getQueryMethod();
        JpaParametersParameterAccessor accessor = new SummerJpaParametersParameterAccessor(method.getParameters(), values);
        Object result = execution.execute(this, accessor);
        ResultProcessor withDynamicProjection = method.getResultProcessor().withDynamicProjection(accessor);
        return withDynamicProjection.processResult(result, new SummerTupleConverter(withDynamicProjection.getReturnedType()));
    }


}
