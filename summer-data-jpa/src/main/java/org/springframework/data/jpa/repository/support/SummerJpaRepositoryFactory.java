/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerJpaRepositoryFactory.java
 * Date:2020-12-29 13:23:29
 */

package org.springframework.data.jpa.repository.support;

import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.provider.QueryExtractor;
import org.springframework.data.jpa.repository.query.*;
import org.springframework.data.querydsl.EntityPathResolver;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;

import javax.persistence.EntityManager;
import java.util.Optional;


public class SummerJpaRepositoryFactory extends JpaRepositoryFactory {

    private final EntityManager entityManager;
    private final QueryExtractor extractor;

    private EntityPathResolver entityPathResolver;
    private EscapeCharacter escapeCharacter = EscapeCharacter.DEFAULT;
    private JpaQueryMethodFactory queryMethodFactory;

    public SummerJpaRepositoryFactory(EntityManager entityManager) {
        super(entityManager);
        this.entityManager = entityManager;
        this.extractor = PersistenceProvider.fromEntityManager(entityManager);

        setEscapeCharacter(escapeCharacter);
        this.entityPathResolver = SimpleEntityPathResolver.INSTANCE;
        setEntityPathResolver(this.entityPathResolver);
        this.queryMethodFactory = new SummerJpaQueryMethodFactory(extractor);
        setQueryMethodFactory(this.queryMethodFactory);
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return SummerSimpleJpaRepository.class;
    }


    /**
     * 使用 SummerJpaQueryLookupStrategy
     * @param key
     * @param evaluationContextProvider
     * @return
     */
    @Override
    protected Optional<QueryLookupStrategy> getQueryLookupStrategy(QueryLookupStrategy.Key key, QueryMethodEvaluationContextProvider evaluationContextProvider) {
        return Optional.of(SummerJpaQueryLookupStrategy.create(entityManager, queryMethodFactory, key, evaluationContextProvider,
                escapeCharacter));
    }
}
