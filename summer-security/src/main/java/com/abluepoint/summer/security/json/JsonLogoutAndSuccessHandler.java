/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonLogoutAndSuccessHandler.java
 * Date:2020-12-29 18:33:29
 */

package com.abluepoint.summer.security.json;

import com.abluepoint.summer.mvc.domain.HttpResult;
import com.abluepoint.summer.security.LogoutAndSuccessHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

public class JsonLogoutAndSuccessHandler implements LogoutAndSuccessHandler {

    private ObjectMapper objectMapper;

    public JsonLogoutAndSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 清理工作
     */
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

    }

    /**
     * 页面返回
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {

        HttpResult jsonMessage = new HttpResult(LocalDateTime.now(), HttpStatus.OK, "logout success");
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), jsonMessage);

    }

}
