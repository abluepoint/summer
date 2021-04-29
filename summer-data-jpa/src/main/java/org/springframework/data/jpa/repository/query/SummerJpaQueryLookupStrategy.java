/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerJpaQueryLookupStrategy.java
 * Date:2020-12-29 13:24:29
 */

package org.springframework.data.jpa.repository.query;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import javax.persistence.EntityManager;
import java.lang.reflect.Method;

/**
 * abluepoint: 使用SummerJpaQueryFactory
 */
public class SummerJpaQueryLookupStrategy {
    /**
     * Private constructor to prevent instantiation.
     */
    private SummerJpaQueryLookupStrategy() {}

    /**
     * Base class for {@link QueryLookupStrategy} implementations that need access to an {@link EntityManager}.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     */
    private abstract static class AbstractQueryLookupStrategy implements QueryLookupStrategy {

        private final EntityManager em;
        private final JpaQueryMethodFactory queryMethodFactory;

        /**
         * Creates a new {@link SummerJpaQueryLookupStrategy.AbstractQueryLookupStrategy}.
         *
         * @param em must not be {@literal null}.
         * @param queryMethodFactory must not be {@literal null}.
         */
        public AbstractQueryLookupStrategy(EntityManager em, JpaQueryMethodFactory queryMethodFactory) {

            Assert.notNull(em, "EntityManager must not be null!");
            Assert.notNull(queryMethodFactory, "JpaQueryMethodFactory must not be null!");

            this.em = em;
            this.queryMethodFactory = queryMethodFactory;
        }

        /*
         * (non-Javadoc)
         * @see org.springframework.data.repository.query.QueryLookupStrategy#resolveQuery(java.lang.reflect.Method, org.springframework.data.repository.core.RepositoryMetadata, org.springframework.data.projection.ProjectionFactory, org.springframework.data.repository.core.NamedQueries)
         */
        @Override
        public final RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata, ProjectionFactory factory,
                                                  NamedQueries namedQueries) {
            return resolveQuery(queryMethodFactory.build(method, metadata, factory), em, namedQueries);
        }

        protected abstract RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries);
    }

    /**
     * {@link QueryLookupStrategy} to create a query from the method name.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     */
    private static class CreateQueryLookupStrategy extends SummerJpaQueryLookupStrategy.AbstractQueryLookupStrategy {

        private final EscapeCharacter escape;

        public CreateQueryLookupStrategy(EntityManager em, JpaQueryMethodFactory queryMethodFactory,
                                         EscapeCharacter escape) {

            super(em, queryMethodFactory);

            this.escape = escape;
        }

        @Override
        protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries) {
            return new PartTreeJpaQuery(method, em, escape);
        }
    }

    /**
     * {@link QueryLookupStrategy} that tries to detect a declared query declared via {@link Query} annotation followed by
     * a JPA named query lookup.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     * @author Jens Schauder
     */
    private static class DeclaredQueryLookupStrategy extends SummerJpaQueryLookupStrategy.AbstractQueryLookupStrategy {

        private final QueryMethodEvaluationContextProvider evaluationContextProvider;

        /**
         * Creates a new {@link SummerJpaQueryLookupStrategy.DeclaredQueryLookupStrategy}.
         *
         * @param em
         * @param queryMethodFactory
         * @param evaluationContextProvider
         */
        public DeclaredQueryLookupStrategy(EntityManager em, JpaQueryMethodFactory queryMethodFactory,
                                           QueryMethodEvaluationContextProvider evaluationContextProvider) {

            super(em, queryMethodFactory);

            this.evaluationContextProvider = evaluationContextProvider;
        }

        /*
         * (non-Javadoc)
         * @see org.springframework.data.jpa.repository.query.JpaQueryLookupStrategy.AbstractQueryLookupStrategy#resolveQuery(org.springframework.data.jpa.repository.query.JpaQueryMethod, javax.persistence.EntityManager, org.springframework.data.repository.core.NamedQueries)
         */
        @Override
        protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries) {

            RepositoryQuery query = SummerJpaQueryFactory.INSTANCE.fromQueryAnnotation(method, em, evaluationContextProvider);

            if (null != query) {
                return query;
            }

            query = SummerJpaQueryFactory.INSTANCE.fromProcedureAnnotation(method, em);

            if (null != query) {
                return query;
            }

            String name = method.getNamedQueryName();
            if (namedQueries.hasQuery(name)) {
                return SummerJpaQueryFactory.INSTANCE.fromMethodWithQueryString(method, em, namedQueries.getQuery(name),
                        evaluationContextProvider);
            }

            query = NamedQuery.lookupFrom(method, em);

            if (null != query) {
                return query;
            }

            throw new IllegalStateException(
                    String.format("Did neither find a NamedQuery nor an annotated query for method %s!", method));
        }
    }

    /**
     * {@link QueryLookupStrategy} to try to detect a declared query first (
     * {@link org.springframework.data.jpa.repository.Query}, JPA named query). In case none is found we fall back on
     * query creation.
     *
     * @author Oliver Gierke
     * @author Thomas Darimont
     */
    private static class CreateIfNotFoundQueryLookupStrategy extends SummerJpaQueryLookupStrategy.AbstractQueryLookupStrategy {

        private final SummerJpaQueryLookupStrategy.DeclaredQueryLookupStrategy lookupStrategy;
        private final SummerJpaQueryLookupStrategy.CreateQueryLookupStrategy createStrategy;

        /**
         * Creates a new {@link SummerJpaQueryLookupStrategy.CreateIfNotFoundQueryLookupStrategy}.
         *
         * @param em must not be {@literal null}.
         * @param queryMethodFactory must not be {@literal null}.
         * @param createStrategy must not be {@literal null}.
         * @param lookupStrategy must not be {@literal null}.
         */
        public CreateIfNotFoundQueryLookupStrategy(EntityManager em, JpaQueryMethodFactory queryMethodFactory,
                                                   SummerJpaQueryLookupStrategy.CreateQueryLookupStrategy createStrategy, SummerJpaQueryLookupStrategy.DeclaredQueryLookupStrategy lookupStrategy) {

            super(em, queryMethodFactory);

            Assert.notNull(createStrategy, "CreateQueryLookupStrategy must not be null!");
            Assert.notNull(lookupStrategy, "DeclaredQueryLookupStrategy must not be null!");

            this.createStrategy = createStrategy;
            this.lookupStrategy = lookupStrategy;
        }

        /*
         * (non-Javadoc)
         * @see org.springframework.data.jpa.repository.query.JpaQueryLookupStrategy.AbstractQueryLookupStrategy#resolveQuery(org.springframework.data.jpa.repository.query.JpaQueryMethod, javax.persistence.EntityManager, org.springframework.data.repository.core.NamedQueries)
         */
        @Override
        protected RepositoryQuery resolveQuery(JpaQueryMethod method, EntityManager em, NamedQueries namedQueries) {

            try {
                return lookupStrategy.resolveQuery(method, em, namedQueries);
            } catch (IllegalStateException e) {
                return createStrategy.resolveQuery(method, em, namedQueries);
            }
        }
    }

    /**
     * Creates a {@link QueryLookupStrategy} for the given {@link EntityManager} and {@link QueryLookupStrategy.Key}.
     *
     * @param em must not be {@literal null}.
     * @param queryMethodFactory must not be {@literal null}.
     * @param key may be {@literal null}.
     * @param evaluationContextProvider must not be {@literal null}.
     * @param escape
     * @return
     */
    public static QueryLookupStrategy create(EntityManager em, JpaQueryMethodFactory queryMethodFactory,
                                             @Nullable QueryLookupStrategy.Key key, QueryMethodEvaluationContextProvider evaluationContextProvider, EscapeCharacter escape) {

        Assert.notNull(em, "EntityManager must not be null!");
        Assert.notNull(evaluationContextProvider, "EvaluationContextProvider must not be null!");

        switch (key != null ? key : QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND) {
            case CREATE:
                return new SummerJpaQueryLookupStrategy.CreateQueryLookupStrategy(em, queryMethodFactory, escape);
            case USE_DECLARED_QUERY:
                return new SummerJpaQueryLookupStrategy.DeclaredQueryLookupStrategy(em, queryMethodFactory, evaluationContextProvider);
            case CREATE_IF_NOT_FOUND:
                return new SummerJpaQueryLookupStrategy.CreateIfNotFoundQueryLookupStrategy(em, queryMethodFactory,
                        new SummerJpaQueryLookupStrategy.CreateQueryLookupStrategy(em, queryMethodFactory, escape),
                        new SummerJpaQueryLookupStrategy.DeclaredQueryLookupStrategy(em, queryMethodFactory, evaluationContextProvider));
            default:
                throw new IllegalArgumentException(String.format("Unsupported query lookup strategy %s!", key));
        }
    }
}
