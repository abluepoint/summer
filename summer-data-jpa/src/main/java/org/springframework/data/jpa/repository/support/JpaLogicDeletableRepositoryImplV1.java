/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerSimpleJpaRepository.java
 * Date:2020-12-29 13:11:29
 */
package org.springframework.data.jpa.repository.support;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.data.jpa.JpaSummerRuntimeException;
import com.abluepoint.summer.data.jpa.entity.LogicDeletable;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class JpaLogicDeletableRepositoryImplV1<T, ID> extends SimpleJpaRepository<T, ID> implements JpaLogicDeletableRepositoryV1<T, ID> {

    private EntityManager entityManager;

    public JpaLogicDeletableRepositoryImplV1(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    public JpaLogicDeletableRepositoryImplV1(Class<T> domainClass, EntityManager em) {
        super(domainClass, em);
        this.entityManager = entityManager;
    }

    @Override
    public void logicDeleteById(ID id) {
        Assert.notNull(id, "The given id must not be null!");
        Optional<T> optional = findById(id);
        if (optional.isPresent()) {
            T t = optional.get();
            logicDelete(t);
        }
    }

    @Override
    public void logicDelete(T entity) {
        Assert.notNull(entity, "Entity must not be null.");
        if (entity instanceof LogicDeletable) {
            ((LogicDeletable) entity).setIsDeleted(null);
        } else {
            throw new JpaSummerRuntimeException(CodeMessageEnum.SYSTEM_DATA_LOGIC_DELETE_NOT_SUPPORT, entity);
        }
        save(entity);
    }

    @Override
    public void logicDelete(Iterable<? extends T> entities) {
        Assert.notNull(entities, "Entities must not be null!");
        for (T entity : entities) {
            logicDelete(entity);
        }
    }

    @Override
    public void logicDeleteAll() {
        for (T element : findAll()) {
            logicDelete(element);
        }
    }
}
