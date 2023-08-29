/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonLogoutAndSuccessHandler.java
 * Date:2020-12-29 18:33:29
 */

package com.abluepoint.summer.security.app;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.mvc.domain.R;
import com.abluepoint.summer.mvc.domain.Result;
import com.abluepoint.summer.security.LogoutAndSuccessHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

public class AppLogoutAndSuccessHandler implements LogoutAndSuccessHandler {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public AppLogoutAndSuccessHandler(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
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

        Result<Map<String, String>> result = R.okByMessage(messageSource,null, CodeMessageEnum.OK);
        result.setTimestamp(LocalDateTime.now());
        result.setPath(request.getContextPath());

        objectMapper.writeValue(response.getOutputStream(), result);

    }

}
