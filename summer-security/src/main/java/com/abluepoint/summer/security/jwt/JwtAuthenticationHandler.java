/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JwtAuthenticationHandler.java
 * Date:2020-12-29 18:29:29
 */

package com.abluepoint.summer.security.jwt;

import com.abluepoint.summer.security.json.JsonAuthenticationHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthenticationHandler extends JsonAuthenticationHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	public JwtAuthenticationHandler(JwtManager jwtManager, ObjectMapper objectMapper) {
		super(jwtManager, objectMapper);
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
	}

}
