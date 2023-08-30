package org.springframework.data.jpa.repository.query;

import com.abluepoint.summer.data.jpa.JpaDefaultTemplate;
import com.abluepoint.summer.data.jpa.template.JpaTemplateSource;
import com.abluepoint.summer.data.jpa.util.JpaNameUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.ResultProcessor;
import org.springframework.data.repository.query.ReturnedType;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

public class AbstractStringBasedJpaQueryV1 extends AbstractJpaQuery {

    private final static Logger logger = LoggerFactory.getLogger(AbstractStringBasedJpaQueryV1.class);

    private final JpaQueryMethodExtendV1 method;
    private final JpaTemplateSource jpaTemplateSource;
    private final String queryString;
    private final String countQueryString;

    private final QueryMethodEvaluationContextProvider evaluationContextProvider;
    private final SpelExpressionParser parser;
    private final QueryParameterSetter.QueryMetadataCache metadataCache = new QueryParameterSetter.QueryMetadataCache();
    private final QueryRewriter queryRewriter;

    private final Map<String, DeclaredQuery> queryMap = new HashMap<>(16);
    private final Map<String, DeclaredQuery> countQueryMap = new HashMap<>(16);
    private final Map<String, ParameterBinder> parameterBinderMap = new HashMap<>(16);

    /**
     * Creates a new {@link AbstractStringBasedJpaQuery} from the given {@link JpaQueryMethod}, {@link EntityManager} and
     * query {@link String}.
     *
     * @param method                    must not be {@literal null}.
     * @param em                        must not be {@literal null}.
     * @param queryString               must not be {@literal null}.
     * @param countQueryString          must not be {@literal null}.
     * @param evaluationContextProvider must not be {@literal null}.
     * @param parser                    must not be {@literal null}.
     * @param queryRewriter             must not be {@literal null}.
     */
    public AbstractStringBasedJpaQueryV1(JpaQueryMethodExtendV1 method, EntityManager em, String queryString,
                                         @Nullable String countQueryString, QueryRewriter queryRewriter, QueryMethodEvaluationContextProvider evaluationContextProvider,
                                         SpelExpressionParser parser) {

        super(method, em);

        Assert.hasText(queryString, "Query string must not be null or empty");
        Assert.notNull(evaluationContextProvider, "ExpressionEvaluationContextProvider must not be null");
        Assert.notNull(parser, "Parser must not be null");
        Assert.notNull(queryRewriter, "QueryRewriter must not be null");

        this.evaluationContextProvider = evaluationContextProvider;

        /**
         * 重写了DeclaredQuery的构造方法，使得可以支持动态参数,不能在创建时确定
         */
        //this.query = new ExpressionBasedStringQuery(queryString, method.getEntityInformation(), parser,
        //        method.isNativeQuery());
        //
        //DeclaredQuery countQuery = query.deriveCountQuery(countQueryString, method.getCountQueryProjection());
        //this.countQuery = ExpressionBasedStringQuery.from(countQuery, method.getEntityInformation(), parser,
        //        method.isNativeQuery());
        //
        //Assert.isTrue(method.isNativeQuery() || !query.usesJdbcStyleParameters(),
        //        "JDBC style parameters (?) are not supported for JPA queries");

        this.parser = parser;
        this.queryRewriter = queryRewriter;

        this.method = method;
        this.queryString = queryString;
        this.countQueryString = countQueryString;

        ApplicationContext context = JpaDefaultTemplate.getApplicationContext();
        if (context == null) {
            throw new RuntimeException("It is not run in spring or not config AppContextUtil!");
        }
        jpaTemplateSource = context.getBean("jpaTemplateSource", JpaTemplateSource.class);
    }

    @Override
    public Query doCreateQuery(JpaParametersParameterAccessor accessor) {

        JpaParametersParameterAccessorExtendV1 parameterAccessor = (JpaParametersParameterAccessorExtendV1) accessor;
        String querySql = getQuerySql(queryString, parameterAccessor.getNamedParameterMap());

        // todo:sql to hash
        DeclaredQuery declaredQuery = queryMap.get(querySql);
        if (declaredQuery == null) {
            JpaQueryMethod method = getQueryMethod();
            declaredQuery = new ExpressionBasedStringQuery(querySql, method.getEntityInformation(), parser,true);
            Assert.isTrue(method.isNativeQuery() || !declaredQuery.usesJdbcStyleParameters(), "JDBC style parameters (?) are not supported for JPA queries.");
            queryMap.put(querySql, declaredQuery);
        }

        String sortedQueryString = QueryEnhancerFactory.forQuery(declaredQuery) //
                .applySorting(accessor.getSort(), declaredQuery.getAlias());
        ResultProcessor processor = getQueryMethod().getResultProcessor().withDynamicProjection(accessor);

        Query query = createJpaQuery(sortedQueryString, accessor.getSort(), accessor.getPageable(),
                processor.getReturnedType(),declaredQuery);

        QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(sortedQueryString, query);

        ParameterBinder parameterBinder = parameterBinderMap.get(sortedQueryString);
        if (parameterBinder == null) {
            parameterBinder = createBinder(declaredQuery);
            parameterBinderMap.put(countQueryString, parameterBinder);
        }
        // it is ok to reuse the binding contained in the ParameterBinder although we create a new query String because the
        // parameters in the query do not change.
        return parameterBinder.bindAndPrepare(query, metadata, accessor);
    }

    /**
     * 不再适用
     *
     * @return
     */
    @Deprecated
    @Override
    protected ParameterBinder createBinder() {

        //return ParameterBinderFactory.createQueryAwareBinder(getQueryMethod().getParameters(), query, parser,
        //        evaluationContextProvider);
        return null;
    }

    /**
     * abluepoint: 动态创建ParameterBinder
     *
     * @param query
     * @return
     */
    private ParameterBinder createBinder(DeclaredQuery query) {
        return ParameterBinderFactory.createQueryAwareBinder(getQueryMethod().getParameters(), query, parser, evaluationContextProvider);
    }

    @Override
    protected Query doCreateCountQuery(JpaParametersParameterAccessor accessor) {

        JpaParametersParameterAccessorExtendV1 parameterAccessor = (JpaParametersParameterAccessorExtendV1) accessor;
        String countQuerySql = getCountQuerySql(countQueryString, parameterAccessor.getNamedParameterMap());

        DeclaredQuery declaredCountQuery = countQueryMap.get(countQuerySql);
        if (declaredCountQuery == null) {
            JpaQueryMethod method = getQueryMethod();
            DeclaredQuery middleCountQuery = DeclaredQuery.of(countQuerySql,true);
            declaredCountQuery = ExpressionBasedStringQuery.from(middleCountQuery, method.getEntityInformation(), parser,true);
            countQueryMap.put(countQuerySql, declaredCountQuery);
        }

        String queryString = declaredCountQuery.getQueryString();
        EntityManager em = getEntityManager();

        Query query = getQueryMethod().isNativeQuery() //
                ? em.createNativeQuery(queryString) //
                : em.createQuery(queryString, Long.class);

        QueryParameterSetter.QueryMetadata metadata = metadataCache.getMetadata(queryString, query);

        ParameterBinder parameterBinder = parameterBinderMap.get(countQueryString);
        if (parameterBinder == null) {
            parameterBinder = createBinder(declaredCountQuery);
            parameterBinderMap.put(countQueryString, parameterBinder);
        }


        parameterBinder.bind(metadata.withQuery(query), accessor, QueryParameterSetter.ErrorHandling.LENIENT);

        return query;
    }

    /**
     * Creates an appropriate JPA query from an {@link EntityManager} according to the current {@link AbstractJpaQuery}
     * type.
     */
    protected Query createJpaQuery(String queryString, Sort sort, @Nullable Pageable pageable,
                                   ReturnedType returnedType,DeclaredQuery declaredQuery) {

        EntityManager em = getEntityManager();

        if (declaredQuery.hasConstructorExpression() || declaredQuery.isDefaultProjection()) {
            return em.createQuery(potentiallyRewriteQuery(queryString, sort, pageable));
        }

        Class<?> typeToRead = getTypeToRead(returnedType);

        return typeToRead == null //
                ? em.createQuery(potentiallyRewriteQuery(queryString, sort, pageable)) //
                : em.createQuery(potentiallyRewriteQuery(queryString, sort, pageable), typeToRead);
    }

    /**
     * Use the {@link QueryRewriter}, potentially rewrite the query, using relevant {@link Sort} and {@link Pageable}
     * information.
     *
     * @param originalQuery
     * @param sort
     * @param pageable
     * @return
     */
    protected String potentiallyRewriteQuery(String originalQuery, Sort sort, @Nullable Pageable pageable) {

        return pageable != null && pageable.isPaged() //
                ? queryRewriter.rewrite(originalQuery, pageable) //
                : queryRewriter.rewrite(originalQuery, sort);
    }

    /**
     * 重写了execute方法，使得可以支持动态参数
     *
     * @param parameters
     * @return
     */
    @Nullable
    @Override
    public Object execute(Object[] parameters) {
        return doExecute(getExecution(), parameters);
    }

    /**
     * 重写了doExecute方法，使得可以支持动态参数,引入扩展JpaParametersParameterAccessorExtendV1
     *
     * @param execution
     * @param values
     * @return
     */
    @Nullable
    private Object doExecute(JpaQueryExecution execution, Object[] values) {
        JpaParametersParameterAccessor accessor = new JpaParametersParameterAccessorExtendV1(method.getParameters(), values);
        Object result = execution.execute(this, accessor);
        ResultProcessor withDynamicProjection = method.getResultProcessor().withDynamicProjection(accessor);
        return withDynamicProjection.processResult(result, new TupleConverter(withDynamicProjection.getReturnedType()));
    }

    public String getQuerySql(String queryString, Map<String, Object> parameterMap) {
        String querySql = null;
        if (queryString.equals("#")) {

            StringBuilder queryStringBuilder = new StringBuilder(method.getJavaClassName()).append(".").append(method.getJavaMethodName());

            queryString = queryStringBuilder.toString();
            logger.debug("query string is: {}", queryString);

            String templateName = queryString;
            try {
                querySql = jpaTemplateSource.getTemplateSql(templateName, parameterMap);
            } catch (Exception e) {
                throw new RuntimeException(new StringBuilder("Find template error,template name is \"").append(templateName).append("\"").toString(), e);
            }

        } else if (queryString.startsWith("##") && queryString.endsWith("##")) {

            String templateName = JpaNameUtils.getTemplateName(queryString);
            try {
                querySql = jpaTemplateSource.getTemplateSql(templateName, parameterMap);
            } catch (Exception e) {
                throw new RuntimeException(new StringBuilder("Find template error,template name is \"").append(templateName).append("\"").toString(), e);
            }

        } else {
            querySql = queryString;
        }
        return querySql;
    }

    private String getCountQuerySql(String countQueryString, Map<String, Object> parameterMap) {
        String countQuerySql = null;
        if (countQueryString.equals("#")) {

            StringBuilder queryStringBuilder = new StringBuilder(method.getJavaClassName()).append(".").append(method.getJavaMethodName());
            queryStringBuilder.append(".count");
            countQueryString = queryStringBuilder.toString();
            logger.debug("countQueryString string is: {}", countQueryString);

            String templateName = countQueryString;
            try {
                countQuerySql = jpaTemplateSource.getTemplateSql(templateName, parameterMap);
            } catch (Exception e) {
                throw new RuntimeException(new StringBuilder("Find template error,template name is \"").append(templateName).append("\"").toString(), e);
            }

        } else if (countQueryString.startsWith("##") && countQueryString.endsWith("##")) {

            String templateName = JpaNameUtils.getTemplateName(countQueryString);
            try {
                countQuerySql = jpaTemplateSource.getTemplateSql(templateName, parameterMap);
            } catch (Exception e) {
                throw new RuntimeException(new StringBuilder("Find template error,template name is \"").append(templateName).append("\"").toString(), e);
            }

        } else {
            countQuerySql = countQueryString;
        }
        return countQuerySql;
    }


}
