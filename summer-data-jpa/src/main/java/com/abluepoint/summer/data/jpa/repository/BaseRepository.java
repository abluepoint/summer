/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:BaseRepository.java
 * Date:2020-12-29 12:26:29
 */

package com.abluepoint.summer.data.jpa.repository;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

@NoRepositoryBean
public interface BaseRepository<E, ID extends Serializable> extends JpaRepositoryImplementation<E, ID> {

}