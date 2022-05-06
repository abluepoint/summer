/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonLogoutAndSuccessHandler.java
 * Date:2020-12-29 18:33:29
 */

package com.abluepoint.summer.security.os;

import com.abluepoint.summer.mvc.domain.HttpResult;
import com.abluepoint.summer.mvc.domain.Result;
import com.abluepoint.summer.mvc.domain.Results;
import com.abluepoint.summer.security.LogoutAndSuccessHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

public class OSLogoutAndSuccessHandler implements LogoutAndSuccessHandler {

    private ObjectMapper objectMapper;

    public OSLogoutAndSuccessHandler(ObjectMapper objectMapper) {
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

        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Result<String> result = Results.success("logout success");
        objectMapper.writeValue(response.getOutputStream(), result);

    }

}
