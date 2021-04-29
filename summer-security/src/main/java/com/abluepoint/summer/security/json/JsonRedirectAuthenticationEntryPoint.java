/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonRedirectAuthenticationEntryPoint.java
 * Date:2020-12-29 18:38:29
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

public class JsonRedirectAuthenticationEntryPoint extends JsonAuthenticationEntryPoint {
	private static final Logger log = LoggerFactory.getLogger(JsonRedirectAuthenticationEntryPoint.class);

    private ObjectMapper objectMapper;
    private String authenticationEntryUrl;
    private String oauthAuthenticationUrl;

    public JsonRedirectAuthenticationEntryPoint(ObjectMapper objectMapper, String authenticationEntryUrl, String oauthAuthenticationUrl) {
        super(objectMapper);
        this.objectMapper = objectMapper;
        this.authenticationEntryUrl = authenticationEntryUrl;
        this.oauthAuthenticationUrl = oauthAuthenticationUrl;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
		if(authException instanceof InsufficientAuthenticationException){
			String redirectUri = URLEncoder.encode(oauthAuthenticationUrl+"?"+request.getQueryString(),"UTF-8");
			response.sendRedirect(authenticationEntryUrl +"?redirect_uri="+redirectUri);
		}else {
			super.commence(request,response,authException);
		}

    }
}
