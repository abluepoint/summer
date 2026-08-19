package com.abluepoint.summer.mvc.autoconfigure;

import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import com.abluepoint.summer.mvc.filter.CachedBodyFilter;
import com.abluepoint.summer.mvc.filter.RequestCorrelationFilter;
import com.abluepoint.summer.mvc.interceptor.FieldValidationInterceptor;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.mvc.request.ServletRequestIdAccessor;
import com.abluepoint.summer.mvc.support.ApiExceptionHandler;
import com.abluepoint.summer.mvc.support.SummerResponseBodyAdvice;
import com.abluepoint.summer.mvc.validate.EmptyStyle;
import com.abluepoint.summer.mvc.validate.FieldValuesArgumentResolver;
import com.abluepoint.summer.mvc.validate.PatternStyle;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingFilterBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@AutoConfiguration(afterName = "com.abluepoint.summer.common.autoconfigure.SummerCommonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestCorrelationFilter.class)
@EnableConfigurationProperties(SummerMvcProperties.class)
@ConditionalOnProperty(prefix = "summer.mvc", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(SummerMvcAutoConfiguration.FieldValidationConfiguration.class)
public class SummerMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingFilterBean(RequestCorrelationFilter.class)
    FilterRegistrationBean<RequestCorrelationFilter> summerRequestCorrelationFilter() {
        FilterRegistrationBean<RequestCorrelationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestCorrelationFilter());
        registration.setName("summerRequestCorrelationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.mvc", name = "cache-request-body", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingFilterBean(CachedBodyFilter.class)
    FilterRegistrationBean<CachedBodyFilter> summerCachedBodyFilter() {
        FilterRegistrationBean<CachedBodyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CachedBodyFilter());
        registration.setName("summerCachedBodyFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 21);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    RequestIdAccessor summerRequestIdAccessor() {
        return new ServletRequestIdAccessor();
    }

    @Bean
    @ConditionalOnMissingBean
    ErrorStatusResolver summerErrorStatusResolver() {
        return new DefaultErrorStatusResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiExceptionHandler summerApiExceptionHandler(
            MessageResolver messageResolver,
            ErrorStatusResolver errorStatusResolver,
            RequestIdAccessor requestIdAccessor
    ) {
        return new ApiExceptionHandler(messageResolver, errorStatusResolver, requestIdAccessor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "summer.mvc", name = "wrap-responses", havingValue = "true", matchIfMissing = true)
    SummerResponseBodyAdvice summerResponseBodyAdvice(
            ObjectMapper objectMapper,
            RequestIdAccessor requestIdAccessor
    ) {
        return new SummerResponseBodyAdvice(objectMapper, requestIdAccessor);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "summer.mvc.field-validation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    static class FieldValidationConfiguration {

        @Bean("emptyStyle")
        @ConditionalOnMissingBean(name = "emptyStyle")
        EmptyStyle emptyStyle() {
            return new EmptyStyle();
        }

        @Bean("integerStyle")
        @ConditionalOnMissingBean(name = "integerStyle")
        PatternStyle integerStyle() {
            return new PatternStyle(
                    Integer.class,
                    "an integer",
                    Map.of(),
                    Pattern.compile("-?(0|[1-9]\\d*)")
            );
        }

        @Bean("longStyle")
        @ConditionalOnMissingBean(name = "longStyle")
        PatternStyle longStyle() {
            return new PatternStyle(
                    Long.class,
                    "a long integer",
                    Map.of(),
                    Pattern.compile("-?(0|[1-9]\\d*)")
            );
        }

        @Bean("decimalStyle")
        @ConditionalOnMissingBean(name = "decimalStyle")
        PatternStyle decimalStyle() {
            return new PatternStyle(
                    BigDecimal.class,
                    "a decimal number",
                    Map.of(),
                    Pattern.compile("-?(0|[1-9]\\d*)(\\.\\d+)?")
            );
        }

        @Bean("booleanStyle")
        @ConditionalOnMissingBean(name = "booleanStyle")
        PatternStyle booleanStyle() {
            return new PatternStyle(
                    Boolean.class,
                    "true or false",
                    Map.of(),
                    Pattern.compile("true|false", Pattern.CASE_INSENSITIVE)
            );
        }

        @Bean
        @ConditionalOnMissingBean
        StyleRegistry summerStyleRegistry(Map<String, Style> styles) {
            return new StyleRegistry(styles);
        }

        @Bean
        @ConditionalOnMissingBean
        FieldValidationInterceptor summerFieldValidationInterceptor(
                StyleRegistry styleRegistry,
                SummerMvcProperties properties
        ) {
            return new FieldValidationInterceptor(
                    styleRegistry,
                    properties.getFieldValidation().getMaxListSize()
            );
        }

        @Bean
        @ConditionalOnMissingBean
        FieldValuesArgumentResolver summerFieldValuesArgumentResolver() {
            return new FieldValuesArgumentResolver();
        }

        @Bean
        @ConditionalOnMissingBean(name = "summerFieldValidationWebMvcConfigurer")
        WebMvcConfigurer summerFieldValidationWebMvcConfigurer(
                FieldValidationInterceptor interceptor,
                FieldValuesArgumentResolver argumentResolver
        ) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor);
                }

                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(argumentResolver);
                }
            };
        }
    }
}
