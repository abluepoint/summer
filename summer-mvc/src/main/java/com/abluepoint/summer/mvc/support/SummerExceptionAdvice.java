package com.abluepoint.summer.mvc.support;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Resolve an explicit business failure before another advice inspects its cause. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SummerExceptionAdvice {
    private final ApiExceptionHandler handler;

    public SummerExceptionAdvice(ApiExceptionHandler handler) {
        this.handler = handler;
    }

    @ExceptionHandler(SummerException.class)
    public ResponseEntity<ApiResponse<Void>> handle(SummerException exception, HttpServletResponse response) {
        return handler.handleSummerException(exception, response);
    }
}
