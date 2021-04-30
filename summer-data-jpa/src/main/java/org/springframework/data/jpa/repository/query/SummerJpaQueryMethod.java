package org.springframework.data.jpa.repository.query;

import org.springframework.data.jpa.provider.QueryExtractor;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;

import java.lang.reflect.Method;

public class SummerJpaQueryMethod extends JpaQueryMethod {

    private final Method method;
    private final String javaClassName;
    private final String javaMethodName;

    /**
     * Creates a {@link JpaQueryMethod}.
     *
     * @param method    must not be {@literal null}
     * @param metadata  must not be {@literal null}
     * @param factory   must not be {@literal null}
     * @param extractor must not be {@literal null}
     */
    protected SummerJpaQueryMethod(Method method, RepositoryMetadata metadata, ProjectionFactory factory, QueryExtractor extractor) {
        super(method, metadata, factory, extractor);
        this.method = method;
        this.javaClassName = method.getDeclaringClass().getName();
        this.javaMethodName = method.getName();
    }

    public Method getMethod() {
        return method;
    }

    public String getJavaClassName() {
        return javaClassName;
    }

    public String getJavaMethodName() {
        return javaMethodName;
    }
}
