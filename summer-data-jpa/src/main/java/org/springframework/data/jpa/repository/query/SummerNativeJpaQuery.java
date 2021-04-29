/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerNativeJpaQuery.java
 * Date:2020-12-29 13:03:29
 */

package org.springframework.data.jpa.repository.query;

import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.Tuple;

final class SummerNativeJpaQuery extends  SummerAbstractStringBasedJpaQuery{
    /**
     * Creates a new {@link SummerNativeJpaQuery} encapsulating the query annotated on the given {@link JpaQueryMethod}.
     *
     * @param method must not be {@literal null}.
     * @param em must not be {@literal null}.
     * @param queryString must not be {@literal null} or empty.
     * @param evaluationContextProvider
     */
    public SummerNativeJpaQuery(JpaQueryMethod method, EntityManager em, String queryString,
                          QueryMethodEvaluationContextProvider evaluationContextProvider, SpelExpressionParser parser) {

        super(method, em, queryString, evaluationContextProvider, parser);

        Parameters<?, ?> parameters = method.getParameters();

        if (parameters.hasSortParameter() && !queryString.contains("#sort")) {
            throw new InvalidJpaQueryMethodException("Cannot use native queries with dynamic sorting in method " + method);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.jpa.repository.query.AbstractStringBasedJpaQuery#createJpaQuery(java.lang.String)
     */
    @Override
    protected Query createJpaQuery(String queryString, ReturnedType returnedType,DeclaredQuery declaredQuery) {

        EntityManager em = getEntityManager();
        Class<?> type = getTypeToQueryFor(returnedType,declaredQuery);

        return type == null ? em.createNativeQuery(queryString) : em.createNativeQuery(queryString, type);
    }

    @Nullable
    private Class<?> getTypeToQueryFor(ReturnedType returnedType,DeclaredQuery declaredQuery) {

        Class<?> result = getQueryMethod().isQueryForEntity() ? returnedType.getDomainType() : null;

        if (declaredQuery.hasConstructorExpression() || declaredQuery.isDefaultProjection()) {
            return result;
        }

        return returnedType.isProjecting() && !getMetamodel().isJpaManaged(returnedType.getReturnedType()) //
                ? Tuple.class
                : result;
    }
}
