package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.common.context.SummerContextHolder;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.message.MessageResolver;
import com.abluepoint.summer.mvc.api.ApiError;
import com.abluepoint.summer.mvc.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MessageResolver messageResolver;

    public ApiExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @ExceptionHandler(SummerException.class)
    public ResponseEntity<ApiResponse<Void>> handleSummerException(SummerException exception) {
        String message = messageResolver.resolve(
                exception.errorCode(),
                exception.messageArguments(),
                LocaleContextHolder.getLocale()
        );
        ApiError error = new ApiError(exception.errorCode().code(), message, List.of());
        return ResponseEntity.status(statusFor(exception)).body(ApiResponse.failure(error, requestId()));
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

    private static HttpStatus statusFor(SummerException exception) {
        return switch (exception.errorCode().code()) {
            case "SUMMER-400" -> HttpStatus.BAD_REQUEST;
            case "SUMMER-401" -> HttpStatus.UNAUTHORIZED;
            case "SUMMER-403" -> HttpStatus.FORBIDDEN;
            case "SUMMER-404" -> HttpStatus.NOT_FOUND;
            case "SUMMER-409" -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String requestId() {
        return SummerContextHolder.current().map(context -> context.requestId()).orElse("unbound");
    }
}
