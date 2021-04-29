/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerSimpleJpaRepository.java
 * Date:2020-12-29 13:11:29
 */
package org.springframework.data.jpa.repository.support;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;


@Repository
@Transactional(readOnly = true)
public class SummerSimpleJpaRepository<T, ID> extends SimpleJpaRepository<T, ID>  implements SummerJpaRepositoryImplementation<T, ID> {

	public SummerSimpleJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
		super(entityInformation, entityManager);
	}

	public SummerSimpleJpaRepository(Class<T> domainClass, EntityManager em) {
		super(domainClass, em);
	}

}
