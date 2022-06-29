/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAccessDeniedHandler.java
 * Date:2020-12-29 18:32:29
 */

package com.abluepoint.summer.security.os;

import com.abluepoint.summer.mvc.domain.HttpResult;
import com.abluepoint.summer.mvc.domain.ReturnCode;
import com.abluepoint.summer.mvc.domain.Result;
import com.abluepoint.summer.mvc.domain.Results;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

public class OSAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public OSAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        HttpResult httpResult = new HttpResult(LocalDateTime.now(), HttpStatus.FORBIDDEN.value(), accessDeniedException.getMessage(), request.getContextPath());

        Result<HttpResult> result = Results.fail(ReturnCode.FORBIDDEN.value(), ReturnCode.FORBIDDEN.message(), httpResult);
        objectMapper.writeValue(response.getOutputStream(), result);
    }

}
