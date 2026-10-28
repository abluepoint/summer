package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void missingRequiredHeaderUsesTheClientErrorContract() throws Exception {
        ApiExceptionHandler handler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "header-request-123"
        );
        var http = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new RequiredHeaderController())
                .setControllerAdvice(handler).build();

        var response = http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/required-header"))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.getContentAsString());
        assertThat(body.path("error").path("code").asText()).isEqualTo("SUMMER_INVALID_ARGUMENT");
        assertThat(body.path("requestId").asText()).isEqualTo("header-request-123");
        assertThat(body.path("error").path("violations").get(0).path("field").asText()).isEqualTo("Idempotency-Key");
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/required-header")
                        .header("Idempotency-Key", "opaque-test-key"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @org.springframework.web.bind.annotation.RestController
    static class RequiredHeaderController {
        @org.springframework.web.bind.annotation.PostMapping("/required-header")
        java.util.Map<String, Boolean> accept(
                @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key) {
            return java.util.Map.of("accepted", true);
        }
    }

    @Test
    void convertsFieldInterceptorViolationsToTheStandardBadRequestResponse() {
        ApiExceptionHandler handler = new ApiExceptionHandler(
                (errorCode, arguments, locale) -> errorCode.defaultMessage(),
                new DefaultErrorStatusResolver(),
                () -> "request-123"
        );
        FieldValidationException exception = new FieldValidationException(List.of(
                new FieldValidationException.Violation("items[0].quantity", "must be an integer")
        ));

        ResponseEntity<ApiResponse<Void>> response = handler.handleFieldValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("SUMMER_INVALID_ARGUMENT");
        assertThat(response.getBody().requestId()).isEqualTo("request-123");
        assertThat(response.getBody().error().violations()).containsExactly(
                new com.abluepoint.summer.common.response.api.ApiError.FieldViolation(
                        "items[0].quantity",
                        "must be an integer"
                )
        );
    }
}
