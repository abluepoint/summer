/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAccessDeniedHandler.java
 * Date:2020-12-29 18:32:29
 */

package com.abluepoint.summer.security.json;

import com.abluepoint.summer.mvc.domain.HttpResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        HttpResult httpResponseEntity = new HttpResult(LocalDateTime.now(), HttpStatus.FORBIDDEN, accessDeniedException.getMessage()+"JsonAccessDeniedHandler");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), httpResponseEntity);
    }

}
