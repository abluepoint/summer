package com.abluepoint.summer.mvc.autoconfigure;

import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.annotation.FieldSource;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.filter.CachedBodyHttpServletRequest;
import com.abluepoint.summer.mvc.filter.RequestCorrelationFilter;
import com.abluepoint.summer.mvc.interceptor.FieldValidationInterceptor;
import com.abluepoint.summer.mvc.support.ApiExceptionHandler;
import com.abluepoint.summer.mvc.support.SummerResponseBodyAdvice;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.mvc.validate.PatternStyle;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
            FilterRegistrationBean<?> correlation = context.getBean(
                    "summerRequestCorrelationFilter",
                    FilterRegistrationBean.class
            );
            FilterRegistrationBean<?> cachedBody = context.getBean(
                    "summerCachedBodyFilter",
                    FilterRegistrationBean.class
            );
            assertThat(correlation.getOrder()).isLessThan(cachedBody.getOrder());
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
    void injectsTheApplicationObjectMapperIntoJsonFieldValidation() {
        contextRunner.run(context -> assertThatCode(() -> {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContentType("application/json");
            request.setContent("{\"age\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

            context.getBean(FieldValidationInterceptor.class).preHandle(
                    cachedRequest,
                    new MockHttpServletResponse(),
                    new HandlerMethod(new JsonFieldController(),
                            JsonFieldController.class.getDeclaredMethod("jsonField"))
            );

            assertThat(FieldValues.from(cachedRequest).get("age", Integer.class)).isEqualTo(42);
        }).doesNotThrowAnyException());
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

    @Test
    void failsAtStartupWhenAJsonFieldContractIsDeclaredWithoutRequestBodyCaching() {
        contextRunner
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .withUserConfiguration(JsonFieldWebConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "JSON Field validation requires summer.mvc.cache-request-body=true"
                            )
                            .hasMessageContaining("JsonFieldController#jsonField");
                });
    }

    @Test
    void allowsParamOnlyContractsWhenRequestBodyCachingIsDisabled() {
        contextRunner
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .withUserConfiguration(ParamFieldWebConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsAtStartupWhenAStrictJsonContractIsDeclaredWithoutRequestBodyCaching() {
        contextRunner
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .withUserConfiguration(StrictJsonFieldWebConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining(
                                    "JSON Field validation requires summer.mvc.cache-request-body=true"
                            )
                            .hasMessageContaining("StrictJsonFieldController#strictJsonField");
                });
    }

    @RestController
    private static final class JsonFieldController {

        @PostMapping("/json-field")
        @Field(param = "age", source = FieldSource.JSON, required = true, style = "integerStyle")
        void jsonField() {
        }
    }

    @RestController
    private static final class ParamFieldController {

        @PostMapping("/param-field")
        @Field(param = "age", required = true, style = "integerStyle")
        void paramField() {
        }
    }

    @RestController
    private static final class StrictJsonFieldController {

        @PostMapping("/strict-json-field")
        @FieldContract(rejectUnknownJson = true)
        void strictJsonField() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(JsonFieldController.class)
    static class JsonFieldWebConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(ParamFieldController.class)
    static class ParamFieldWebConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(StrictJsonFieldController.class)
    static class StrictJsonFieldWebConfiguration {
    }
}
