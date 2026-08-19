package com.abluepoint.summer.web.autoconfigure;

import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.mvc.filter.SummerRequestContextFilter;
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
                    assertThat(context).doesNotHaveBean(FieldValidationInterceptor.class);
                    assertThat(context).doesNotHaveBean(StyleRegistry.class);
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
                .withPropertyValues("summer.web.field-validation.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("summerRequestContextFilter");
                    assertThat(context).hasSingleBean(ApiExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(FieldValidationInterceptor.class);
                    assertThat(context).doesNotHaveBean(StyleRegistry.class);
                    assertThat(context).doesNotHaveBean("integerStyle");
                    assertThat(context).doesNotHaveBean("summerFieldValidationWebMvcConfigurer");
                });
    }
}
