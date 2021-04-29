/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticationHandler.java
 * Date:2020-12-29 18:31:29
 */

package com.abluepoint.summer.security.json;

import com.abluepoint.summer.mvc.domain.HttpResult;
import com.abluepoint.summer.security.AuthenticationHandler;
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
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * <h2>JsonLoginHandler<h2>
 *
 * @author bluepoint
 */
public class JsonAuthenticationHandler implements AuthenticationHandler {

	private JwtManager jwtManager;

	private ObjectMapper objectMapper;

	public JsonAuthenticationHandler(JwtManager jwtManager, ObjectMapper objectMapper) {
		this.jwtManager = jwtManager;
		this.objectMapper = objectMapper;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
		HttpResult httpResponseEntity = new HttpResult(LocalDateTime.now(), HttpStatus.UNAUTHORIZED, exception.getMessage());
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setCharacterEncoding("utf-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), httpResponseEntity);
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
		UserDetails user = (UserDetails) authentication.getPrincipal();
		String token = jwtManager.createJwtToken(user.getUsername());
		HttpResult httpResult = new JsonAuthenticateSuccessResult(LocalDateTime.now(), "success", token);
		response.setCharacterEncoding("utf-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), httpResult);
	}

}
