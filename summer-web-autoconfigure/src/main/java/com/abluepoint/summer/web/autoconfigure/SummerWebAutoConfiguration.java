package com.abluepoint.summer.web.autoconfigure;

import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.mvc.filter.SummerRequestContextFilter;
import com.abluepoint.summer.mvc.support.ApiExceptionHandler;
import com.abluepoint.summer.mvc.support.SummerResponseBodyAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingFilterBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(afterName = "com.abluepoint.summer.common.autoconfigure.SummerCommonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SummerRequestContextFilter.class)
@EnableConfigurationProperties(SummerWebProperties.class)
@ConditionalOnProperty(prefix = "summer.web", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SummerWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingFilterBean(SummerRequestContextFilter.class)
    FilterRegistrationBean<SummerRequestContextFilter> summerRequestContextFilter(SummerWebProperties properties) {
        FilterRegistrationBean<SummerRequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SummerRequestContextFilter(properties.isCacheRequestBody()));
        registration.setName("summerRequestContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    ApiExceptionHandler summerApiExceptionHandler(MessageResolver messageResolver) {
        return new ApiExceptionHandler(messageResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "summer.web", name = "wrap-responses", havingValue = "true", matchIfMissing = true)
    SummerResponseBodyAdvice summerResponseBodyAdvice(ObjectMapper objectMapper) {
        return new SummerResponseBodyAdvice(objectMapper);
    }
}
