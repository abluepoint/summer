package com.abluepoint.summer.mvc.autoconfigure;

import com.abluepoint.summer.common.message.spi.MessageResolver;
import com.abluepoint.summer.mvc.parameter.api.Field;
import com.abluepoint.summer.mvc.parameter.api.FieldContract;
import com.abluepoint.summer.mvc.parameter.api.FieldList;
import com.abluepoint.summer.mvc.error.internal.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.request.internal.CachedBodyHttpServletRequest;
import com.abluepoint.summer.mvc.request.internal.RequestCorrelationFilter;
import com.abluepoint.summer.mvc.logging.internal.RequestLoggingFilter;
import com.abluepoint.summer.mvc.parameter.internal.FieldValidationInterceptor;
import com.abluepoint.summer.mvc.logging.spi.RequestLogSink;
import com.abluepoint.summer.mvc.error.internal.ApiExceptionHandler;
import com.abluepoint.summer.mvc.response.internal.SummerResponseBodyAdvice;
import com.abluepoint.summer.mvc.parameter.api.FieldValues;
import com.abluepoint.summer.mvc.parameter.api.PatternStyle;
import com.abluepoint.summer.mvc.parameter.spi.Style;
import com.abluepoint.summer.mvc.parameter.internal.StyleRegistry;
import com.abluepoint.summer.mvc.parameter.api.Styles;
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
            FilterRegistrationBean<?> logging = context.getBean(
                    "summerRequestLoggingFilter",
                    FilterRegistrationBean.class
            );
            assertThat(correlation.getOrder()).isLessThan(logging.getOrder());
            assertThat(logging.getOrder()).isLessThan(cachedBody.getOrder());
            assertThat(correlation.getOrder()).isLessThan(cachedBody.getOrder());
        });
    }

    @Test
    void ignoresTheRemovedCachedBodySwitchAndKeepsBodyCachingAvailable() {
        contextRunner
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("summerRequestCorrelationFilter");
                    assertThat(context).hasBean("summerCachedBodyFilter");
                });
    }

    @Test
    void bindsOneGlobalRequestBodyLimit() {
        contextRunner.withPropertyValues("summer.mvc.request-body.max-bytes=4096")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SummerMvcProperties.class)
                            .getRequestBody().getMaxBytes()).isEqualTo(4096);
                });
        contextRunner.withPropertyValues("summer.mvc.request-body.max-bytes=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void registersRequestLoggingFilterWithARequestLogSink() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RequestLogSink.class);
            assertThat(context).hasBean("summerRequestLoggingFilter");
            FilterRegistrationBean<?> logging = context.getBean(
                    "summerRequestLoggingFilter",
                    FilterRegistrationBean.class
            );
            assertThat(logging.getFilter()).isInstanceOf(RequestLoggingFilter.class);
            assertThat(logging.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 21);
        });
    }

    @Test
    void disablesRequestLoggingWithItsPublicApplicationProperty() {
        contextRunner
                .withPropertyValues("summer.mvc.request-logging.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RequestLogSink.class);
                    assertThat(context).doesNotHaveBean("summerRequestLoggingFilter");
                });
    }

    @Test
    void bindsOneSharedPublicBodyLoggingConfiguration() {
        contextRunner
                .withPropertyValues(
                        "summer.mvc.request-logging.body.enabled=true",
                        "summer.mvc.request-logging.body.max-preview-length=96",
                        "summer.mvc.request-logging.body.redacted-fields=password,accessToken"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SummerMvcProperties.RequestLogging.Body body = context
                            .getBean(SummerMvcProperties.class)
                            .getRequestLogging()
                            .getBody();
                    assertThat(body.isEnabled()).isTrue();
                    assertThat(body.getMaxPreviewLength()).isEqualTo(96);
                    assertThat(body.getRedactedFields()).containsExactlyInAnyOrder("password", "accessToken");
                    FilterRegistrationBean<?> registration = context.getBean(
                            "summerRequestLoggingFilter", FilterRegistrationBean.class
                    );
                    assertThat(registration.getFilter()).isInstanceOf(RequestLoggingFilter.class);
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
    void createsFieldValidationInfrastructureWithoutBuiltInStyles() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StyleRegistry.class);
            assertThat(context).hasSingleBean(FieldValidationInterceptor.class);
            assertThat(context).doesNotHaveBean("emptyStyle");
            assertThat(context).doesNotHaveBean("integerStyle");
            assertThat(context).doesNotHaveBean("longStyle");
            assertThat(context).doesNotHaveBean("decimalStyle");
            assertThat(context).doesNotHaveBean("booleanStyle");
            assertThat(context).hasBean("summerFieldValidationWebMvcConfigurer");
        });
    }

    @Test
    void injectsTheApplicationObjectMapperIntoJsonFieldValidation() {
        contextRunner
                .withBean("integerStyle", Style.class, () -> Styles.integer().build())
                .run(context -> assertThatCode(() -> {
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
    void applicationStyleBeanIsRegisteredByItsExplicitName() {
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
    void startsWithAJsonFieldContractRegardlessOfTheRemovedCachedBodySwitch() {
        contextRunner
                .withBean("integerStyle", Style.class, () -> Styles.integer().build())
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .withUserConfiguration(JsonFieldWebConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startsWithParamOnlyContractsWhenTheRemovedCachedBodySwitchIsOff() {
        contextRunner
                .withBean("integerStyle", Style.class, () -> Styles.integer().build())
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .withUserConfiguration(ParamFieldWebConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void startsWithAStrictJsonContractRegardlessOfTheRemovedCachedBodySwitch() {
        contextRunner
                .withPropertyValues("summer.mvc.cache-request-body=false")
                .withUserConfiguration(StrictJsonFieldWebConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsAtStartupWhenAFieldListHasAnInvalidSizeRange() {
        contextRunner
                .withBean("integerStyle", Style.class, () -> Styles.integer().build())
                .withPropertyValues("summer.mvc.field-validation.max-list-size=4")
                .withUserConfiguration(InvalidFieldListWebConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("Invalid FieldList configurations")
                            .hasMessageContaining("field=items")
                            .hasMessageContaining("minSize=5")
                            .hasMessageContaining("effective maxSize=4");
                });
    }

    @Test
    void failsAtStartupWhenAFieldReferencesAnUnknownStyle() {
        contextRunner
                .withUserConfiguration(MissingStyleWebConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("handler=")
                            .hasMessageContaining("field=missing")
                            .hasMessageContaining("style=doesNotExist");
                });
    }

    @RestController
    private static final class JsonFieldController {

        @PostMapping("/json-field")
        @Field(param = "age", required = true, style = "integerStyle")
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
        @FieldContract
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

    @RestController
    private static final class MissingStyleController {

        @PostMapping("/missing-style")
        @Field(param = "missing", required = true, style = "doesNotExist")
        void missingStyle() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(MissingStyleController.class)
    static class MissingStyleWebConfiguration {
    }

    @RestController
    private static final class InvalidFieldListController {

        @PostMapping("/invalid-list")
        @FieldList(
                param = "items",
                minSize = 5,
                items = @Field(param = "value", style = "integerStyle")
        )
        void invalidList() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(InvalidFieldListController.class)
    static class InvalidFieldListWebConfiguration {
    }

}
