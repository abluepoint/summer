package com.abluepoint.summer.security.web;

import com.abluepoint.summer.common.context.SummerContextHolder;
import com.abluepoint.summer.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), SecurityErrorResponse.of(
                CommonErrorCode.UNAUTHORIZED.code(),
                CommonErrorCode.UNAUTHORIZED.defaultMessage(),
                requestId(request)
        ));
    }

    private static String requestId(HttpServletRequest request) {
        return SummerContextHolder.current()
                .map(context -> context.requestId())
                .orElseGet(() -> request.getRequestId());
    }
}
