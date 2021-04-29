/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerJpaQueryFactory.java
 * Date:2020-12-29 13:19:29
 */

package org.springframework.data.jpa.repository.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;

import javax.persistence.EntityManager;

public enum SummerJpaQueryFactory {
    INSTANCE;

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final Logger LOG = LoggerFactory.getLogger(JpaQueryFactory.class);

    /**
     * Creates a {@link RepositoryQuery} from the given {@link QueryMethod} that is potentially annotated with
     * {@link Query}.
     *
     * @param method must not be {@literal null}.
     * @param em must not be {@literal null}.
     * @param evaluationContextProvider
     * @return the {@link RepositoryQuery} derived from the annotation or {@code null} if no annotation found.
     */
    @Nullable
    AbstractJpaQuery fromQueryAnnotation(JpaQueryMethod method, EntityManager em,
                                         QueryMethodEvaluationContextProvider evaluationContextProvider) {

        LOG.debug("Looking up query for method {}", method.getName());
        return fromMethodWithQueryString(method, em, method.getAnnotatedQuery(), evaluationContextProvider);
    }

    /**
     * Creates a {@link RepositoryQuery} from the given {@link String} query.
     *
     * @param method must not be {@literal null}.
     * @param em must not be {@literal null}.
     * @param queryString must not be {@literal null} or empty.
     * @param evaluationContextProvider
     * @return
     */
    @Nullable
    AbstractJpaQuery fromMethodWithQueryString(JpaQueryMethod method, EntityManager em, @Nullable String queryString,
                                               QueryMethodEvaluationContextProvider evaluationContextProvider) {

        if (queryString == null) {
            return null;
        }

        return method.isNativeQuery() ? new SummerNativeJpaQuery(method, em, queryString, evaluationContextProvider, PARSER)
                : new SummerSimpleJpaQuery(method, em, queryString, evaluationContextProvider, PARSER);
    }

    /**
     * Creates a {@link StoredProcedureJpaQuery} from the given {@link JpaQueryMethod} query.
     *
     * @param method must not be {@literal null}.
     * @param em must not be {@literal null}.
     * @return
     */
    @Nullable
    public StoredProcedureJpaQuery fromProcedureAnnotation(JpaQueryMethod method, EntityManager em) {

        if (!method.isProcedureQuery()) {
            return null;
        }

        return new StoredProcedureJpaQuery(method, em);
    }
}
