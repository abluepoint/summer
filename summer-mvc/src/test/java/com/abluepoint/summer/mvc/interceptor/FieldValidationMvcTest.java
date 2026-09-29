package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.annotation.FieldSource;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.filter.CachedBodyFilter;
import com.abluepoint.summer.mvc.support.ApiExceptionHandler;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.mvc.validate.FieldValuesArgumentResolver;
import com.abluepoint.summer.mvc.validate.PatternStyle;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class FieldValidationMvcTest {

    @Test
    void exposesTypedFieldValuesAsAnExplicitControllerArgument() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        MvcResult result = mockMvc.perform(get("/field-values").param("age", "42")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("42");
    }

    @Test
    void exposesTypedPathFieldValuesAsAnExplicitControllerArgument() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        MvcResult result = mockMvc.perform(get("/customers/{customerId}", "42")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("42");
    }

    @Test
    void rejectsMultipleSourcesExportingTheSameFieldValueName() {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        assertThatThrownBy(() -> mockMvc.perform(
                get("/field-collision/{customerId}", "42").param("id", "99")
                )).hasRootCauseInstanceOf(com.abluepoint.summer.mvc.validate.FieldValidationException.class);
    }

    @Test
    void rejectsUnknownQueryFieldsWithoutAnOptInStrictnessFlag() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-unknown-query"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(get("/field-values")
                        .param("age", "42")
                        .param("typo", "ignored"))
                .andReturn();

        JsonNode response = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString())
                .isEqualTo("[{\"field\":\"typo\",\"message\":\"is not allowed\"}]");
    }

    @Test
    void rejectsAFieldProvidedByQueryAndJsonAtTheSameTime() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-source-conflict"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/json-fields")
                        .param("displayName", "query")
                        .contentType(APPLICATION_JSON)
                        .content("{\"displayName\":\"json\",\"profile\":{\"age\":42},\"active\":true}"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString())
                .contains("displayName", "one input source");
    }

    @Test
    void infersFormParametersAndRejectsUnknownFormFields() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-unknown-form"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/form-field")
                        .contentType("application/x-www-form-urlencoded")
                        .param("age", "42")
                        .param("typo", "ignored"))
                .andReturn();

        JsonNode response = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString())
                .isEqualTo("[{\"field\":\"typo\",\"message\":\"is not allowed\"}]");
    }

    @Test
    void rejectsFieldListsFromPathVariables() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        assertThatThrownBy(() -> mockMvc.perform(get("/path-items/{count}", "0")))
                .hasRootCauseInstanceOf(com.abluepoint.summer.mvc.validate.FieldValidationException.class);
    }

    @Test
    void returnsAggregatedPathViolationsAsTheUnifiedBadRequestContract() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "regionStyle",
                        new PatternStyle(String.class, "an uppercase region", Map.of(), Pattern.compile("[A-Z]{2}"))
                )),
                100
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-path-violations"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(get("/regions/{region}/customers/{customerId}", "12", "bad"))
                .andReturn();

        JsonNode response = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("success").asBoolean()).isFalse();
        assertThat(response.get("requestId").stringValue()).isEqualTo("request-path-violations");
        assertThat(response.get("error").get("code").stringValue()).isEqualTo("SUMMER_INVALID_ARGUMENT");
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"customerId\",\"message\":\"must be an integer\"},"
                        + "{\"field\":\"region\",\"message\":\"must be an uppercase region\"}]"
        );
    }

    @Test
    void validatesTypedJsonScalarsBeforeTheSameBodyIsDeserializedIntoADto() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "non-blank text", Map.of(), Pattern.compile(".*\\S.*")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        MvcResult result = mockMvc.perform(post("/json-fields")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":"Ada","profile":{"age":42},"active":true}
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("Ada:42:true:Ada");
    }

    @Test
    void continuesWithJakartaBeanValidationAfterJsonFieldValidation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-bean-validation"
        );
        try (ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()) {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                    .addFilters(new CachedBodyFilter())
                    .addInterceptors(interceptor)
                    .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                    .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                    .setControllerAdvice(exceptionHandler)
                    .build();

            MvcResult result = mockMvc.perform(post("/validated-json-fields")
                            .contentType(APPLICATION_JSON)
                            .content("{\"displayName\":\"\"}"))
                    .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(response.get("error").get("code").asString()).isEqualTo("SUMMER_INVALID_ARGUMENT");
            assertThat(response.get("error").get("violations").get(0).get("field").asString())
                    .isEqualTo("displayName");
        }
    }

    @Test
    void continuesWithJakartaBeanValidationWithoutFieldAnnotations() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-ordinary-bean-validation"
        );
        try (ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory()) {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                    .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                    .setControllerAdvice(exceptionHandler)
                    .build();

            MvcResult result = mockMvc.perform(post("/ordinary-validated-json")
                            .contentType(APPLICATION_JSON)
                            .content("{\"displayName\":\"\"}"))
                    .andReturn();

            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(response.get("error").get("code").asString()).isEqualTo("SUMMER_INVALID_ARGUMENT");
            assertThat(response.get("error").get("violations").get(0).get("field").asString())
                    .isEqualTo("displayName");
        }
    }

    @Test
    void preservesJsonScalarTypesInsteadOfCoercingStringsNumbersAndBooleans() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-json-scalar-types"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/json-fields")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":42,"profile":{"age":"42"},"active":"true"}
                                """))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"displayName\",\"message\":\"must be a JSON string\"},"
                        + "{\"field\":\"profile.age\",\"message\":\"must be a JSON number\"},"
                        + "{\"field\":\"active\",\"message\":\"must be a JSON boolean\"}]"
        );
    }

    @Test
    void returnsTheUnifiedBadRequestContractForMalformedOrdinaryRequestBodies() throws Exception {
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-malformed-json"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/ordinary-json")
                        .contentType(APPLICATION_JSON)
                        .content("{"))
                .andReturn();

        JsonNode response = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("code").asString()).isEqualTo("SUMMER_INVALID_ARGUMENT");
        assertThat(response.get("requestId").asString()).isEqualTo("request-malformed-json");
    }

    @Test
    void rejectsJsonObjectsAndArraysForScalarFieldsWithoutExportingPartialValues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-json-node-types"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/json-fields")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":{"value":"Ada"},"profile":{"age":42},"active":[true]}
                                """))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"displayName\",\"message\":\"must be a JSON scalar\"},"
                        + "{\"field\":\"active\",\"message\":\"must be a JSON scalar\"}]"
        );
    }

    @Test
    void supportsARootJsonScalarWithoutPreventingRequestBodyBinding() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        MvcResult result = mockMvc.perform(post("/root-json-field")
                        .contentType(APPLICATION_JSON)
                        .content("true"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("true:true");
    }

    @Test
    void returnsTheUnifiedBadRequestContractForMalformedJsonFieldBodies() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-malformed-field-json"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/json-fields")
                        .contentType(APPLICATION_JSON)
                        .content("{"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("code").asString()).isEqualTo("SUMMER_INVALID_ARGUMENT");
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"$\",\"message\":\"must be valid JSON\"}]"
        );
    }

    @Test
    void preservesJsonPresenceAndExplicitNullableNullInAMixedPathPatchContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        MvcResult result = mockMvc.perform(patch("/patch-customers/{email}", "ada@example.com")
                        .contentType(APPLICATION_JSON)
                        .content("{\"displayName\":null}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .isEqualTo("ada@example.com:true:true:false");
    }

    @Test
    void rejectsUnknownFieldsInAStrictJsonContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-unknown-json"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(patch("/patch-customers/{email}", "ada@example.com")
                        .contentType(APPLICATION_JSON)
                        .content("{\"displayName\":\"Ada\",\"nickname\":\"Countess\"}"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"nickname\",\"message\":\"is not allowed\"}]"
        );
    }

    @Test
    void rejectsExplicitNullForANonNullableJsonField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-non-null-json"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(patch("/patch-customers/{email}", "ada@example.com")
                        .contentType(APPLICATION_JSON)
                        .content("{\"active\":null}"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"active\",\"message\":\"must not be null\"}]"
        );
    }

    @Test
    void rejectsUnknownNestedJsonFieldsButAcceptsDeclaredNestedPaths() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-nested-unknown-json"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(patch("/patch-customers/{email}", "ada@example.com")
                        .contentType(APPLICATION_JSON)
                        .content("{\"profile\":{\"age\":42,\"nickname\":\"Countess\"}}"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"profile.nickname\",\"message\":\"is not allowed\"}]"
        );
    }

    @Test
    void validatesJsonObjectArraysUsingTheirActualLengthAndNestedItemPaths() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100,
                objectMapper
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        MvcResult result = mockMvc.perform(post("/json-items")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"sku":"A-1","details":{"quantity":2},"note":null},
                                  {"sku":"B-2","details":{"quantity":5},"note":"priority"}
                                ]}
                                """))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.toString()).isEqualTo(
                "[{\"sku\":\"A-1\",\"quantity\":2,\"note\":null},"
                        + "{\"sku\":\"B-2\",\"quantity\":5,\"note\":\"priority\"}]"
        );
    }

    @Test
    void aggregatesJsonObjectArrayShapeItemAndStrictContractViolations() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-invalid-json-items"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/json-items")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  "not-an-object",
                                  {"details":{"quantity":"many"},"extra":true}
                                ]}
                                """))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"items[1].extra\",\"message\":\"is not allowed\"},"
                        + "{\"field\":\"items[0]\",\"message\":\"must be a JSON object\"},"
                        + "{\"field\":\"items[1].sku\",\"message\":\"is required\"},"
                        + "{\"field\":\"items[1].details.quantity\",\"message\":\"must be a JSON number\"}]"
        );
    }

    @Test
    void rejectsJsonFieldListsThatAreNotArrays() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MockMvc mockMvc = jsonFieldListMockMvc(100, objectMapper, "request-json-list-shape");

        MvcResult result = mockMvc.perform(post("/json-items")
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":{\"sku\":\"A-1\"}}"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"items\",\"message\":\"must be a JSON object array\"}]"
        );
    }

    @Test
    void rejectsEmptyJsonArraysEvenWhenCollectionIsOptionalAndNullable() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MockMvc mockMvc = optionalJsonFieldListMockMvc(objectMapper, "request-empty-list");

        MvcResult result = mockMvc.perform(post("/optional-json-items")
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString())
                .contains("must contain at least 1 items");
    }

    @Test
    void enforcesTheLocalJsonFieldListLimit() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MockMvc mockMvc = jsonFieldListMockMvc(10, objectMapper, "request-json-list-local-limit");

        MvcResult result = mockMvc.perform(post("/json-items")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"A"},{"sku":"B"},{"sku":"C"}]}
                                """))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").toString()).isEqualTo(
                "[{\"field\":\"items\",\"message\":\"must contain at most 2 items\"}]"
        );
    }

    @Test
    void allowsAnExplicitListLimitToExceedThePublicDefault() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        MockMvc mockMvc = jsonFieldListMockMvc(1, objectMapper, "request-json-list-global-limit");

        MvcResult result = mockMvc.perform(post("/json-items")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"items":[
                                  {"sku":"A","details":{"quantity":1},"note":"one"},
                                  {"sku":"B","details":{"quantity":2},"note":"two"}
                                ]}
                                """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void treatsAnExplicitlyEmptyJsonBodyAsMissingFieldsInsteadOfMissingCacheConfiguration() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "booleanStyle",
                        new PatternStyle(Boolean.class, "true or false", Map.of(), Pattern.compile("true|false"))
                )),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-empty-json"
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(post("/json-fields")
                        .contentType(APPLICATION_JSON)
                        .content(new byte[0]))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.get("error").get("violations").size()).isEqualTo(3);
    }

    private MockMvc optionalJsonFieldListMockMvc(ObjectMapper objectMapper, String requestId) {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of("textStyle", new PatternStyle(
                        String.class, "text", Map.of(), Pattern.compile(".*\\S.*")
                ))),
                100,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> requestId
        );
        return MockMvcBuilders.standaloneSetup(new OptionalFieldListController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    private MockMvc jsonFieldListMockMvc(int maxListSize, ObjectMapper objectMapper, String requestId) {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*")),
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                maxListSize,
                objectMapper
        );
        ApiExceptionHandler exceptionHandler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> requestId
        );
        return MockMvcBuilders.standaloneSetup(new FieldController())
                .addFilters(new CachedBodyFilter())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @RestController
    private static final class FieldController {

        @GetMapping("/field-values")
        @Field(param = "age", required = true, style = "integerStyle")
        Integer fieldValues(FieldValues values) {
            return values.get("age", Integer.class);
        }

        @PostMapping("/form-field")
        @Field(param = "age", required = true, style = "integerStyle")
        Integer formField(FieldValues values) {
            return values.get("age", Integer.class);
        }

        @GetMapping("/customers/{customerId}")
        @Field(
                param = "customerId",
                required = true,
                style = "integerStyle"
        )
        Integer pathFieldValues(FieldValues values) {
            return values.get("customerId", Integer.class);
        }

        @GetMapping("/field-collision/{id}")
        @Field(param = "id", required = true, style = "integerStyle")
        Integer collidingFieldValues(FieldValues values) {
            return values.get("id", Integer.class);
        }

        @GetMapping("/path-items/{count}")
        @FieldList(
                param = "items",
                items = @Field(param = "quantity", name = "quantity", style = "integerStyle")
        )
        Integer pathFieldList(FieldValues values) {
            return values.get("items", java.util.List.class).size();
        }

        @GetMapping("/regions/{region}/customers/{customerId}")
        @Field(
                param = "customerId",
                required = true,
                style = "integerStyle"
        )
        @Field(
                param = "region",
                required = true,
                style = "regionStyle"
        )
        String invalidPathFields(FieldValues values) {
            return values.asMap().toString();
        }

        @PostMapping("/json-fields")
        @Field(param = "displayName", required = true, style = "textStyle")
        @Field(param = "profile.age", required = true, style = "integerStyle")
        @Field(param = "active", required = true, style = "booleanStyle")
        String jsonFields(FieldValues values, @RequestBody JsonRequest body) {
            return values.get("displayName", String.class)
                    + ":" + values.get("profile.age", Integer.class)
                    + ":" + values.get("active", Boolean.class)
                    + ":" + body.displayName();
        }

        @PostMapping("/ordinary-json")
        JsonRequest ordinaryJson(@RequestBody JsonRequest body) {
            return body;
        }

        @PostMapping("/ordinary-validated-json")
        String ordinaryValidatedJson(@Valid @RequestBody ValidatedJsonRequest body) {
            return body.displayName();
        }

        @PostMapping("/validated-json-fields")
        @Field(param = "displayName", required = true, style = "textStyle")
        String validatedJsonFields(
                FieldValues values,
                @Valid @RequestBody ValidatedJsonRequest body
        ) {
            return values.get("displayName", String.class) + ":" + body.displayName();
        }

        @PostMapping("/root-json-field")
        @Field(param = "", name = "enabled", required = true, style = "booleanStyle")
        String rootJsonField(FieldValues values, @RequestBody Boolean body) {
            return values.get("enabled", Boolean.class) + ":" + body;
        }

        @PatchMapping("/patch-customers/{email}")
        @FieldContract
        @Field(param = "email", required = true, style = "textStyle")
        @Field(
                param = "displayName",
                required = false,
                nullable = true,
                style = "textStyle"
        )
        @Field(param = "active", required = false, style = "booleanStyle")
        @Field(param = "profile.age", required = false, style = "integerStyle")
        String patchCustomer(FieldValues values) {
            return values.get("email", String.class)
                    + ":" + values.contains("displayName")
                    + ":" + (values.get("displayName", String.class) == null)
                    + ":" + values.contains("active");
        }

        @PostMapping("/json-items")
        @FieldContract
        @FieldList(
                param = "items",
                maxSize = 2,
                items = {
                        @Field(param = "sku", name = "sku", required = true, style = "textStyle"),
                        @Field(
                                param = "details.quantity",
                                name = "quantity",
                                required = true,
                                style = "integerStyle"
                        ),
                        @Field(param = "note", name = "note", required = false, nullable = true, style = "textStyle")
                }
        )
        Object jsonItems(FieldValues values) {
            return values.get("items", List.class);
        }
    }

    @RestController
    private static final class OptionalFieldListController {

        @PostMapping("/optional-json-items")
        @FieldList(
                param = "items",
                required = false,
                nullable = true,
                items = @Field(param = "sku", style = "textStyle")
        )
        Object items(FieldValues values) {
            return values.get("items", List.class);
        }
    }

    private record JsonRequest(String displayName, Profile profile, boolean active) {
    }

    private record Profile(int age) {
    }

    private record ValidatedJsonRequest(@NotBlank String displayName) {
    }
}
