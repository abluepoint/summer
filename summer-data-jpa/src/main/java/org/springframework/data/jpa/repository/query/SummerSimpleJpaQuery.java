/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerSimpleJpaQuery.java
 * Date:2020-12-29 13:06:29
 */

package org.springframework.data.jpa.repository.query;

import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import javax.persistence.EntityManager;

public class SummerSimpleJpaQuery extends SummerAbstractStringBasedJpaQuery {

    /**
     * Creates a new {@link SimpleJpaQuery} encapsulating the query annotated on the given {@link JpaQueryMethod}.
     *
     * @param method                    must not be {@literal null}
     * @param em                        must not be {@literal null}
     * @param evaluationContextProvider must not be {@literal null}
     * @param parser                    must not be {@literal null}
     */
    public SummerSimpleJpaQuery(JpaQueryMethod method, EntityManager em,
                                QueryMethodEvaluationContextProvider evaluationContextProvider, SpelExpressionParser parser) {
        this(method, em, method.getRequiredAnnotatedQuery(), evaluationContextProvider, parser);
    }

    /**
     * Creates a new {@link SimpleJpaQuery} that encapsulates a simple query string.
     *
     * @param method                    must not be {@literal null}
     * @param em                        must not be {@literal null}
     * @param queryString               must not be {@literal null} or empty
     * @param evaluationContextProvider must not be {@literal null}
     * @param parser                    must not be {@literal null}
     */
    public SummerSimpleJpaQuery(JpaQueryMethod method, EntityManager em, String queryString,
                                QueryMethodEvaluationContextProvider evaluationContextProvider, SpelExpressionParser parser) {

        super(method, em, queryString, evaluationContextProvider, parser);
        /**
         *  abluepoint: 去掉校验
         */
//        validateQuery(getQuery().getQueryString(), "Validation failed for query for method %s!", method);
//
//        if (method.isPageQuery()) {
//            validateQuery(getCountQuery().getQueryString(),
//                    String.format("Count query validation failed for method %s!", method));
//        }
    }

    /**
     * Validates the given query for syntactical correctness.
     *
     * @param query
     * @param errorMessage
     */
    private void validateQuery(String query, String errorMessage, Object... arguments) {

        if (getQueryMethod().isProcedureQuery()) {
            return;
        }

        EntityManager validatingEm = null;

        try {
            validatingEm = getEntityManager().getEntityManagerFactory().createEntityManager();
            validatingEm.createQuery(query);

        } catch (RuntimeException e) {

            // Needed as there's ambiguities in how an invalid query string shall be expressed by the persistence provider
            // https://java.net/projects/jpa-spec/lists/jsr338-experts/archive/2012-07/message/17
            throw new IllegalArgumentException(String.format(errorMessage, arguments), e);

        } finally {

            if (validatingEm != null) {
                validatingEm.close();
            }
        }
    }
}
