/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAccessDeniedHandler.java
 * Date:2020-12-29 18:32:29
 */

package com.abluepoint.summer.security.app;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.mvc.domain.R;
import com.abluepoint.summer.mvc.domain.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

public class AppAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public AppAccessDeniedHandler(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Result<Object> result = R.failByMessage(messageSource, CodeMessageEnum.FORBIDDEN);
        result.setTimestamp(LocalDateTime.now());
        result.setPath(request.getContextPath());
        objectMapper.writeValue(response.getOutputStream(), result);
    }

}
