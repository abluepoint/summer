package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

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
                new com.abluepoint.summer.common.response.ApiError.FieldViolation(
                        "items[0].quantity",
                        "must be an integer"
                )
        );
    }
}
