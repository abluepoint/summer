package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class MethodSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        ApiError error = new ApiError(
                CommonErrorCode.FORBIDDEN.code(),
                CommonErrorCode.FORBIDDEN.defaultMessage(),
                List.of()
        );
        return ResponseEntity.status(403).body(ApiResponse.failure(error, requestId(request)));
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute("com.abluepoint.summer.requestId");
        return requestId instanceof String value && !value.isBlank()
                ? value
                : request.getRequestId();
    }
}
