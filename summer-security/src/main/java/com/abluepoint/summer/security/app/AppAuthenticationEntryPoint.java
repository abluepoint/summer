/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticationEntryPoint.java
 * Date:2020-12-29 18:37:29
 */

package com.abluepoint.summer.security.app;

import com.abluepoint.summer.common.exception.CodeMessage;
import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.common.exception.Messageable;
import com.abluepoint.summer.mvc.domain.R;
import com.abluepoint.summer.mvc.domain.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 认证入口EntryPoint
 */
public class AppAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(AppAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public AppAuthenticationEntryPoint(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException) throws IOException {

        log.debug("auth error",authenticationException);

        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Result<Object> result = R.failByMessage(messageSource, CodeMessageEnum.UNAUTHORIZED);

        result.setTimestamp(LocalDateTime.now());
        result.setPath(request.getContextPath());

        objectMapper.writeValue(response.getOutputStream(), result);

    }
}
