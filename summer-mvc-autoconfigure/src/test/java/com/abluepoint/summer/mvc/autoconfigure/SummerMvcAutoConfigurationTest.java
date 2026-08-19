package com.abluepoint.summer.mvc.autoconfigure;

import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.filter.RequestCorrelationFilter;
import com.abluepoint.summer.mvc.interceptor.FieldValidationInterceptor;
import com.abluepoint.summer.mvc.support.ApiExceptionHandler;
import com.abluepoint.summer.mvc.support.SummerResponseBodyAdvice;
import com.abluepoint.summer.mvc.validate.PatternStyle;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SummerMvcAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SummerMvcAutoConfiguration.class))
            .withBean(MessageResolver.class, () -> (errorCode, arguments, locale) -> errorCode.code())
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void backsOffWhenApplicationProvidesRequestCorrelationFilter() {
        contextRunner
                .withBean("customRequestCorrelationFilter", RequestCorrelationFilter.class,
                        RequestCorrelationFilter::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("summerRequestCorrelationFilter");
                    assertThat(context).hasSingleBean(RequestCorrelationFilter.class);
                });
    }

    @Test
    void registersCorrelationAndCachedBodyFiltersIndependently() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("summerRequestCorrelationFilter");
            assertThat(context).hasBean("summerCachedBodyFilter");
        });
    }

    @Test
    void disablesOnlyCachedBodyFilterWhenItsSwitchIsOff() {
        contextRunner
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("summerRequestCorrelationFilter");
                    assertThat(context).doesNotHaveBean("summerCachedBodyFilter");
                });
    }

    @Test
    void createsNoWebInfrastructureWhenDisabled() {
        contextRunner
                .withPropertyValues("summer.mvc.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("summerRequestCorrelationFilter");
                    assertThat(context).doesNotHaveBean("summerCachedBodyFilter");
                    assertThat(context).doesNotHaveBean(ApiExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SummerResponseBodyAdvice.class);
                    assertThat(context).doesNotHaveBean(FieldValidationInterceptor.class);
                    assertThat(context).doesNotHaveBean(StyleRegistry.class);
                });
    }

    @Test
    void backsOffWhenApplicationProvidesExceptionHandlerAndResponseAdvice() {
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.code(),
                new DefaultErrorStatusResolver(),
                () -> "custom-request-id"
        );
        SummerResponseBodyAdvice responseAdvice = new SummerResponseBodyAdvice(
                new ObjectMapper(),
                () -> "custom-request-id"
        );

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

    @Test
    void createsFieldValidationInfrastructureWithBuiltInStyles() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StyleRegistry.class);
            assertThat(context).hasSingleBean(FieldValidationInterceptor.class);
            assertThat(context).hasBean("emptyStyle");
            assertThat(context).hasBean("integerStyle");
            assertThat(context).hasBean("longStyle");
            assertThat(context).hasBean("decimalStyle");
            assertThat(context).hasBean("booleanStyle");
            assertThat(context).hasBean("summerFieldValidationWebMvcConfigurer");
        });
    }

    @Test
    void applicationStyleBeanOverridesTheBuiltInStyleByName() {
        Style customIntegerStyle = new PatternStyle(
                String.class,
                "a custom integer",
                Map.of("source", "application"),
                Pattern.compile("custom-\\d+")
        );

        contextRunner
                .withBean("integerStyle", Style.class, () -> customIntegerStyle)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).getBean("integerStyle").isSameAs(customIntegerStyle);
                    assertThat(context.getBean(StyleRegistry.class).require("integerStyle"))
                            .isSameAs(customIntegerStyle);
                });
    }

    @Test
    void disablesOnlyFieldValidationInfrastructureWhenItsSwitchIsOff() {
        contextRunner
                .withPropertyValues("summer.mvc.field-validation.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("summerRequestCorrelationFilter");
                    assertThat(context).hasBean("summerCachedBodyFilter");
                    assertThat(context).hasSingleBean(ApiExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(FieldValidationInterceptor.class);
                    assertThat(context).doesNotHaveBean(StyleRegistry.class);
                    assertThat(context).doesNotHaveBean("integerStyle");
                    assertThat(context).doesNotHaveBean("summerFieldValidationWebMvcConfigurer");
                });
    }
}
