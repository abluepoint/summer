package com.abluepoint.summer.security.web;

import com.abluepoint.summer.common.context.SummerContextHolder;
import com.abluepoint.summer.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), SecurityErrorResponse.of(
                CommonErrorCode.FORBIDDEN.code(),
                CommonErrorCode.FORBIDDEN.defaultMessage(),
                requestId(request)
        ));
    }

    private static String requestId(HttpServletRequest request) {
        return SummerContextHolder.current()
                .map(context -> context.requestId())
                .orElseGet(() -> request.getRequestId());
    }
}
