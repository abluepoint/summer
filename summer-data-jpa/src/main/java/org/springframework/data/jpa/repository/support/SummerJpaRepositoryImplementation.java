/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerJpaRepositoryImplementation.java
 * Date:2020-12-28 19:56:28
 */
package org.springframework.data.jpa.repository.support;

import org.springframework.data.repository.NoRepositoryBean;


@NoRepositoryBean
public interface SummerJpaRepositoryImplementation<T, ID> extends JpaRepositoryImplementation<T, ID> {

}
