/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JpaRepositoryFactoryV1.java
 * Date:2020-12-29 13:23:29
 */

package org.springframework.data.jpa.repository.support;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.provider.QueryExtractor;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.data.querydsl.EntityPathResolver;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;

import java.util.Optional;


public class JpaRepositoryFactoryV1 extends JpaRepositoryFactory {

    private final EntityManager entityManager;
    private final QueryExtractor extractor;

    private EntityPathResolver entityPathResolver;
    private EscapeCharacter escapeCharacter = EscapeCharacter.DEFAULT;
    private JpaQueryMethodFactory queryMethodFactory;
    private QueryRewriterProvider queryRewriterProvider = QueryRewriterProvider.simple();

    public JpaRepositoryFactoryV1(EntityManager entityManager) {
        super(entityManager);
        this.entityManager = entityManager;
        this.extractor = PersistenceProvider.fromEntityManager(entityManager);

        setEscapeCharacter(escapeCharacter);
        this.entityPathResolver = SimpleEntityPathResolver.INSTANCE;
        setEntityPathResolver(this.entityPathResolver);
        this.queryMethodFactory = new DefaultJpaQueryMethodFactoryExtendV1(extractor);
        setQueryMethodFactory(this.queryMethodFactory);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {

        return JpaLogicDeletableRepositoryImplV1.class;
    }


    /**
     * 使用 JpaQueryLookupStrategyV1
     *
     * @param key
     * @param evaluationContextProvider
     * @return
     */
    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(QueryLookupStrategy.Key key, QueryMethodEvaluationContextProvider evaluationContextProvider) {
        return Optional.of(JpaQueryLookupStrategyV1.create(entityManager, queryMethodFactory, key, evaluationContextProvider, queryRewriterProvider, escapeCharacter));
    }
}
