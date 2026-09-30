package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MessageResolver messageResolver;
    private final ErrorStatusResolver errorStatusResolver;
    private final RequestIdAccessor requestIdAccessor;

    public ApiExceptionHandler(
            MessageResolver messageResolver,
            ErrorStatusResolver errorStatusResolver,
            RequestIdAccessor requestIdAccessor
    ) {
        this.messageResolver = messageResolver;
        this.errorStatusResolver = errorStatusResolver;
        this.requestIdAccessor = requestIdAccessor;
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFieldValidation(FieldValidationException exception) {
        List<ApiError.FieldViolation> violations = exception.violations().stream()
                .map(violation -> new ApiError.FieldViolation(violation.field(), violation.message()))
                .toList();
        ApiError error = new ApiError(
                CommonErrorCode.INVALID_ARGUMENT.code(),
                CommonErrorCode.INVALID_ARGUMENT.defaultMessage(),
                violations
        );
        return ResponseEntity.badRequest().body(ApiResponse.failure(error, requestId()));
    }

    @ExceptionHandler(SummerException.class)
    public ResponseEntity<ApiResponse<Void>> handleSummerException(SummerException exception) {
        String message = messageResolver.resolve(
                exception.errorCode(),
                exception.messageArguments(),
                LocaleContextHolder.getLocale()
        );
        List<ApiError.FieldViolation> violations = exception.violations().stream()
                .map(violation -> new ApiError.FieldViolation(violation.field(), violation.message()))
                .toList();
        ApiError error = new ApiError(exception.errorCode().code(), message, violations);
        return ResponseEntity.status(errorStatusResolver.resolve(exception.errorCode()))
                .body(ApiResponse.failure(error, requestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldViolation> violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiError.FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()
                ))
                .toList();
        ApiError error = new ApiError(
                CommonErrorCode.INVALID_ARGUMENT.code(),
                CommonErrorCode.INVALID_ARGUMENT.defaultMessage(),
                violations
        );
        return ResponseEntity.badRequest().body(ApiResponse.failure(error, requestId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        ApiError error = new ApiError(
                CommonErrorCode.INVALID_ARGUMENT.code(),
                CommonErrorCode.INVALID_ARGUMENT.defaultMessage(),
                List.of(new ApiError.FieldViolation("$", "must be valid JSON"))
        );
        return ResponseEntity.badRequest().body(ApiResponse.failure(error, requestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request failure", exception);
        ApiError error = new ApiError(
                CommonErrorCode.INTERNAL_ERROR.code(),
                CommonErrorCode.INTERNAL_ERROR.defaultMessage(),
                List.of()
        );
        return ResponseEntity.internalServerError().body(ApiResponse.failure(error, requestId()));
    }

    private String requestId() {
        return requestIdAccessor.currentRequestId();
    }
}
