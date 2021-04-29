/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerJpaRepositoryFactoryBean.java
 * Date:2020-12-29 11:11:29
 */
package org.springframework.data.jpa.repository.support;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.query.EscapeCharacter;
import org.springframework.data.jpa.repository.query.JpaQueryMethodFactory;
import org.springframework.data.querydsl.EntityPathResolver;
import org.springframework.data.querydsl.SimpleEntityPathResolver;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.lang.Nullable;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

public class SummerJpaRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
        extends JpaRepositoryFactoryBean<T, S, ID> {

    private @Nullable EntityManager entityManager;
    private EntityPathResolver entityPathResolver;
    private EscapeCharacter escapeCharacter = EscapeCharacter.DEFAULT;
    private JpaQueryMethodFactory queryMethodFactory;

    /**
     * Creates a new {@link SummerJpaRepositoryFactoryBean} for the given repository interface.
     *
     * @param repositoryInterface must not be {@literal null}.
     */
    public SummerJpaRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        super.setEntityManager(entityManager);
        this.entityManager = entityManager;
    }

    @Autowired
    public void setEntityPathResolver(ObjectProvider<EntityPathResolver> resolver) {
        super.setEntityPathResolver(resolver);
        this.entityPathResolver = resolver.getIfAvailable(() -> SimpleEntityPathResolver.INSTANCE);
    }

    @Autowired
    public void setQueryMethodFactory(@Nullable JpaQueryMethodFactory factory) {
        super.setQueryMethodFactory(factory);
        if (factory != null) {
            this.queryMethodFactory = factory;
        }

    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        SummerJpaRepositoryFactory summerJpaRepositoryFactory = new SummerJpaRepositoryFactory(entityManager);
        summerJpaRepositoryFactory.setEntityPathResolver(entityPathResolver);
        summerJpaRepositoryFactory.setEscapeCharacter(escapeCharacter);

        if (queryMethodFactory != null) {
            summerJpaRepositoryFactory.setQueryMethodFactory(queryMethodFactory);
        }

        return summerJpaRepositoryFactory;

    }
}
