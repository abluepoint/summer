package com.abluepoint.summer.sample.validation;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.mvc.validate.PatternStyle;
import com.abluepoint.summer.mvc.validate.Style;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({FieldValidationHttpTest.Endpoint.class, FieldValidationHttpTest.Messages.class})
class FieldValidationHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void fieldErrorsUseTheUnifiedLocaleAndCorrelationContract() throws Exception {
        mvc.perform(get("/api/field-validation/translated").with(jwt())
                        .header("Accept-Language", "fr").header("X-Request-Id", "field-unified"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.message").value("Champ invalide"))
                .andExpect(violations(List.of(
                        Map.of("field", "profile.age", "message", "must be an integer"))))
                .andExpect(jsonPath("$.requestId").value("field-unified"))
                .andExpect(header().string("X-Request-Id", "field-unified"))
                .andExpect(jsonPath("$.error.cause").doesNotExist())
                .andExpect(jsonPath("$.error.stackTrace").doesNotExist());
    }

    @Test
    void applicationFieldErrorsRetainTheirCodeLiteralMessageAndOverriddenStatus() throws Exception {
        mvc.perform(get("/api/field-validation/literal").with(jwt()).header("Accept-Language", "fr"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("TEST_FIELD_INVALID"))
                .andExpect(jsonPath("$.error.message").value("  field {0}  "))
                .andExpect(violations(List.of(
                        Map.of("field", "value", "message", "is above max"))))
                .andExpect(jsonPath("$.error.cause").doesNotExist());
    }

    @Test
    void nullMessagesResolvePerLocaleWhileExplicitEmptyAndBlankMessagesStayLiteral() throws Exception {
        for (String[] example : new String[][]{{"en", "Invalid field age"}, {"fr", "Champ age invalide"},
                {"en", "Invalid field age"}}) {
            mvc.perform(get("/api/field-validation/arguments").with(jwt()).header("Accept-Language", example[0]))
                    .andExpect(status().is(422)).andExpect(jsonPath("$.error.message").value(example[1]));
        }
        for (String[] example : new String[][]{{"empty", ""}, {"blank", "   "},
                {"fallback", "The request conflicts with current state"}}) {
            mvc.perform(get("/api/field-validation/" + example[0]).with(jwt()))
                    .andExpect(jsonPath("$.error.message").value(example[1]));
        }
    }

    @Test
    void emptyAndRepeatedFieldDetailsRemainOrderedAndGenerateARequestId() throws Exception {
        var result = mvc.perform(get("/api/field-validation/empty-details").with(jwt()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.violations").isEmpty())
                .andReturn();
        var body = new tools.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(body.path("requestId").asString()).isNotBlank()
                .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        mvc.perform(get("/api/field-validation/repeated").with(jwt()))
                .andExpect(violations(List.of(
                        Map.of("field", "age", "message", "first"),
                        Map.of("field", "age", "message", "second"))));
    }

    @Test
    void parameterContractAggregatesQueryErrorsAndStillExportsValidValues() throws Exception {
        mvc.perform(get("/api/field-validation/query").with(jwt()).param("age", "bad").param("size", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(violations(List.of(
                        Map.of("field", "age", "message", "must be an integer"),
                        Map.of("field", "size", "message", "must be an integer"))))
                .andExpect(result -> assertThat(result.getRequest().getAttribute(FieldValues.REQUEST_ATTRIBUTE)).isNull());
        mvc.perform(get("/api/field-validation/query").with(jwt()).param("age", "12").param("size", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.age").value(12))
                .andExpect(jsonPath("$.data.size").value(3));
    }

    @Test
    void jsonArraysPreserveNestedPathsAndTheOriginalAggregationOrder() throws Exception {
        mvc.perform(post("/api/field-validation/items").with(jwt()).contentType(APPLICATION_JSON)
                        .content("""
                                {"items":["wrong-shape",{"extra":true,"details":{"quantity":"many"}}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(violations(List.of(
                        Map.of("field", "items[1].extra", "message", "is not allowed"),
                        Map.of("field", "items[0]", "message", "must be a JSON object"),
                        Map.of("field", "items[1].sku", "message", "is required"),
                        Map.of("field", "items[1].details.quantity", "message", "must be a JSON number"))));
    }

    @Test
    void formFieldListsRejectIndexGapsAndKeepNestedFieldPaths() throws Exception {
        mvc.perform(post("/api/field-validation/items").with(jwt()).contentType(APPLICATION_FORM_URLENCODED)
                        .param("items[1].sku", "A").param("items[1].details.quantity", "2"))
                .andExpect(status().isBadRequest()).andExpect(violations(List.of(
                        Map.of("field", "items", "message", "indexes must start at 0 and be contiguous"))));
        mvc.perform(post("/api/field-validation/items").with(jwt()).contentType(APPLICATION_FORM_URLENCODED)
                        .param("items[0].sku", "A").param("items[0].details.quantity", "many"))
                .andExpect(status().isBadRequest()).andExpect(violations(List.of(
                        Map.of("field", "items[0].details.quantity", "message", "must be an integer"))));
    }

    @Test
    void ordinaryAndNestedBeanValidationRemainNativeAndKeepTheirFieldPaths() throws Exception {
        for (String[] example : new String[][]{{"{\"profile\":{\"name\":\"\"},\"items\":[]}", "profile.name"},
                {"{\"profile\":{\"name\":\"Ada\"},\"items\":[{\"name\":\"\"}]}", "items[0].name"}}) {
            mvc.perform(post("/api/field-validation/bean").with(jwt()).contentType(APPLICATION_JSON).content(example[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(result -> assertThat(result.getResolvedException())
                            .isInstanceOf(org.springframework.web.bind.MethodArgumentNotValidException.class))
                    .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                    .andExpect(violations(List.of(
                            Map.of("field", example[1], "message", "must have a name"))));
        }
    }

    @Test
    void beanValidationStillRunsAfterTheJsonParameterContractAcceptsTheField() throws Exception {
        mvc.perform(post("/api/field-validation/validated").with(jwt()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(org.springframework.web.bind.MethodArgumentNotValidException.class))
                .andExpect(violations(List.of(
                        Map.of("field", "name", "message", "must have a name"))));
    }

    private static org.springframework.test.web.servlet.ResultMatcher violations(List<Map<String, String>> expected) {
        return result -> {
            var mapper = new tools.jackson.databind.ObjectMapper();
            var body = mapper.readTree(result.getResponse().getContentAsString());
            assertThat(body.path("error").path("violations")).isEqualTo(mapper.valueToTree(expected));
        };
    }

    @RestController
    static class Endpoint {
        @GetMapping("/api/field-validation/{mode}")
        Object fail(@PathVariable String mode) {
            var details = List.of(new SummerException.Violation("value", "is above max"));
            switch (mode) {
                case "translated" -> throw new FieldValidationException(List.of(
                        new SummerException.Violation("profile.age", "must be an integer")));
                case "literal" -> throw new FieldValidationException(TestCode.INVALID, details, "  field {0}  ",
                        new IllegalStateException("private diagnostic"), "age");
                case "arguments" -> throw new FieldValidationException(TestCode.INVALID, details, null, null, "age");
                case "empty" -> throw new FieldValidationException(TestCode.INVALID, details, "", null);
                case "blank" -> throw new FieldValidationException(TestCode.INVALID, details, "   ", null);
                case "fallback" -> throw new FieldValidationException(CommonErrorCode.CONFLICT, details);
                case "empty-details" -> throw new FieldValidationException(List.of());
                case "repeated" -> throw new FieldValidationException(List.of(
                        new SummerException.Violation("age", "first"), new SummerException.Violation("age", "second")));
                default -> throw new IllegalArgumentException("Unknown fixture mode");
            }
        }

        @GetMapping("/api/field-validation/query")
        @Field(param = "age", style = "fieldTestIntegerStyle")
        @Field(param = "size", style = "fieldTestIntegerStyle")
        Map<String, Object> query(FieldValues values) { return values.asMap(); }

        @PostMapping("/api/field-validation/items")
        @FieldList(param = "items", items = {
                @Field(param = "sku", style = "fieldTestTextStyle"),
                @Field(param = "details.quantity", style = "fieldTestIntegerStyle")})
        Map<String, Object> items(FieldValues values) { return values.asMap(); }

        @PostMapping("/api/field-validation/bean")
        Object bean(@Valid @RequestBody BeanInput input) { return input; }

        @PostMapping("/api/field-validation/validated")
        @Field(param = "name", style = "fieldTestTextStyle")
        Object validated(@Valid @RequestBody Named input) { return input; }
    }

    record BeanInput(@Valid Named profile, @Valid List<Named> items) { }
    record Named(@NotBlank(message = "must have a name") String name) { }

    enum TestCode implements ErrorCode {
        INVALID;
        public String code() { return "TEST_FIELD_INVALID"; }
        public String defaultMessage() { return "Invalid field"; }
        public ErrorCategory category() { return ErrorCategory.INVALID_ARGUMENT; }
    }

    @TestConfiguration
    static class Messages {
        @Bean StaticMessageSource messageSource() {
            var source = new StaticMessageSource();
            source.addMessage("SUMMER_INVALID_ARGUMENT", Locale.FRENCH, "Champ invalide");
            source.addMessage("TEST_FIELD_INVALID", Locale.ENGLISH, "Invalid field {0}");
            source.addMessage("TEST_FIELD_INVALID", Locale.FRENCH, "Champ {0} invalide");
            return source;
        }
        @Bean ErrorStatusResolver fieldTestStatusResolver() {
            return new DefaultErrorStatusResolver(Map.of(TestCode.INVALID, HttpStatus.valueOf(422)));
        }
        @Bean Style fieldTestIntegerStyle() {
            return new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"));
        }
        @Bean Style fieldTestTextStyle() {
            return new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*"));
        }
    }
}
