/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticationEntryPoint.java
 * Date:2020-12-29 18:37:29
 */

package com.abluepoint.summer.security.json;

import com.abluepoint.summer.mvc.domain.HttpResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.LocalDateTime;

/**
 * 认证入口EntryPoint
 */

public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

    private ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        HttpResult httpResult = new HttpResult(LocalDateTime.now(), HttpStatus.UNAUTHORIZED, authException.getMessage());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), httpResult);

    }
}
