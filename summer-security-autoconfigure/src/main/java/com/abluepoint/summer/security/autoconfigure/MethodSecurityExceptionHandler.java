package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.common.context.SummerContextHolder;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.security.web.SecurityErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class MethodSecurityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<SecurityErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        String requestId = SummerContextHolder.current()
                .map(context -> context.requestId())
                .orElseGet(request::getRequestId);
        return ResponseEntity.status(403).body(SecurityErrorResponse.of(
                CommonErrorCode.FORBIDDEN.code(),
                CommonErrorCode.FORBIDDEN.defaultMessage(),
                requestId
        ));
    }
}
