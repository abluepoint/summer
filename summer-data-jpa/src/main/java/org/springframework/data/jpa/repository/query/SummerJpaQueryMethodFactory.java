/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:SummerJpaQueryMethodFactory.java
 * Date:2020-12-29 13:09:29
 */

package org.springframework.data.jpa.repository.query;

import org.springframework.data.jpa.provider.QueryExtractor;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;

import java.lang.reflect.Method;

public class SummerJpaQueryMethodFactory extends DefaultJpaQueryMethodFactory {

    private final QueryExtractor extractor;

    public SummerJpaQueryMethodFactory(QueryExtractor extractor) {
        super(extractor);
        this.extractor = extractor;

    }

    @Override
    public JpaQueryMethod build(Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
        return new JpaQueryMethod(method, metadata, factory, extractor);
    }
}
