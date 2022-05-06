/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticationHandler.java
 * Date:2020-12-29 18:31:29
 */

package com.abluepoint.summer.security.os;

import com.abluepoint.summer.mvc.domain.HttpResult;
import com.abluepoint.summer.mvc.domain.Result;
import com.abluepoint.summer.mvc.domain.Results;
import com.abluepoint.summer.mvc.domain.ReturnCode;
import com.abluepoint.summer.security.AuthenticationHandler;
import com.abluepoint.summer.security.json.JsonAuthenticateSuccessResult;
import com.abluepoint.summer.security.jwt.JwtManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * <h2>JsonLoginHandler<h2>
 *
 * @author bluepoint
 */
public class OSAuthenticationHandler implements AuthenticationHandler {

    private final JwtManager jwtManager;

    private final ObjectMapper objectMapper;

    public OSAuthenticationHandler(JwtManager jwtManager, ObjectMapper objectMapper) {
        this.jwtManager = jwtManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Result<Object> result = Results.fail(ReturnCode.LOGIN_FAILED);
        objectMapper.writeValue(response.getOutputStream(), result);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        UserDetails user = (UserDetails) authentication.getPrincipal();

        String token = jwtManager.createJwtToken(user.getUsername(), request.getRequestedSessionId());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, String> data = new HashMap<>(1);
        data.put("token", token);

        Result<Map<String, String>> result = Results.success(data);
        objectMapper.writeValue(response.getOutputStream(), result);
    }

}
