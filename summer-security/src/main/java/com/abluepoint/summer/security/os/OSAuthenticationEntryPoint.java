/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticationEntryPoint.java
 * Date:2020-12-29 18:37:29
 */

package com.abluepoint.summer.security.os;

import com.abluepoint.summer.mvc.domain.ReturnCode;
import com.abluepoint.summer.mvc.domain.Result;
import com.abluepoint.summer.mvc.domain.Results;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 认证入口EntryPoint
 */

public class OSAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(OSAuthenticationEntryPoint.class);

    private ObjectMapper objectMapper;

    public OSAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Result<Object> result = Results.fail(ReturnCode.UNAUTHORIZED);
        objectMapper.writeValue(response.getOutputStream(), result);

    }
}
