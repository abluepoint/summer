package com.abluepoint.summer.web.autoconfigure;

import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.mvc.filter.SummerRequestContextFilter;
import com.abluepoint.summer.mvc.support.ApiExceptionHandler;
import com.abluepoint.summer.mvc.support.SummerResponseBodyAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SummerWebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SummerWebAutoConfiguration.class))
            .withBean(MessageResolver.class, () -> (errorCode, arguments, locale) -> errorCode.code())
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void backsOffWhenApplicationProvidesRequestContextFilter() {
        contextRunner
                .withBean("customRequestContextFilter", SummerRequestContextFilter.class,
                        () -> new SummerRequestContextFilter(false))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("summerRequestContextFilter");
                    assertThat(context).hasSingleBean(SummerRequestContextFilter.class);
                });
    }

    @Test
    void createsNoWebInfrastructureWhenDisabled() {
        contextRunner
                .withPropertyValues("summer.web.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SummerRequestContextFilter.class);
                    assertThat(context).doesNotHaveBean(ApiExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SummerResponseBodyAdvice.class);
                });
    }

    @Test
    void backsOffWhenApplicationProvidesExceptionHandlerAndResponseAdvice() {
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.code());
        SummerResponseBodyAdvice responseAdvice = new SummerResponseBodyAdvice(new ObjectMapper());

        contextRunner
                .withBean("customExceptionHandler", ApiExceptionHandler.class, () -> exceptionHandler)
                .withBean("customResponseAdvice", SummerResponseBodyAdvice.class, () -> responseAdvice)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("summerApiExceptionHandler");
                    assertThat(context).doesNotHaveBean("summerResponseBodyAdvice");
                    assertThat(context).getBean(ApiExceptionHandler.class).isSameAs(exceptionHandler);
                    assertThat(context).getBean(SummerResponseBodyAdvice.class).isSameAs(responseAdvice);
                });
    }
}
