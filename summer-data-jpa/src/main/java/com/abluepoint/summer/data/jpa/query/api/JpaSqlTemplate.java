package com.abluepoint.summer.data.jpa.query.api;

import com.abluepoint.summer.data.jpa.query.internal.SqlResultProjectionAdapter;
import com.abluepoint.summer.data.jpa.query.spi.SqlTemplateSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

import java.util.List;
import java.util.Map;

public final class JpaSqlTemplate {

    private final EntityManager entityManager;
    private final SqlTemplateSource templateSource;
    private final FreemarkerSqlRenderer renderer;
    private final SqlResultProjectionAdapter resultProjectionAdapter;

    public JpaSqlTemplate(
            EntityManager entityManager,
            SqlTemplateSource templateSource,
            FreemarkerSqlRenderer renderer
    ) {
        this.entityManager = entityManager;
        this.templateSource = templateSource;
        this.renderer = renderer;
        this.resultProjectionAdapter = new SqlResultProjectionAdapter();
    }

    public <T> List<T> query(
            String location,
            String templateName,
            Map<String, ?> templateParameters,
            Map<String, ?> queryParameters,
            Class<T> targetType
    ) {
        String source = templateSource.load(location, templateName);
        String sql = renderer.render(templateName, source, templateParameters);
        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        bind(query, queryParameters);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = query.getResultList();
        return rows.stream().map(tuple -> resultProjectionAdapter.project(tuple, targetType)).toList();
    }

    public long count(
            String location,
            String templateName,
            Map<String, ?> templateParameters,
            Map<String, ?> queryParameters
    ) {
        String source = templateSource.load(location, templateName);
        String sql = renderer.render(templateName, source, templateParameters);
        Query query = entityManager.createNativeQuery(sql);
        bind(query, queryParameters);
        Object result = query.getSingleResult();
        if (!(result instanceof Number number)) {
            throw new IllegalStateException("Count query did not return a numeric result: " + result);
        }
        return number.longValue();
    }

    private static void bind(Query query, Map<String, ?> parameters) {
        if (parameters != null) {
            parameters.forEach(query::setParameter);
        }
    }
}
