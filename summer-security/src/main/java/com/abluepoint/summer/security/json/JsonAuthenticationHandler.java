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
import org.springframework.session.web.http.CookieHttpSessionIdResolver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;

import static org.springframework.session.web.http.SessionRepositoryFilter.SESSION_REPOSITORY_ATTR;

/**
 * <h2>JsonLoginHandler<h2>
 *
 * @author bluepoint
 */
public class JsonAuthenticationHandler implements AuthenticationHandler {

	private JwtManager jwtManager;

	private ObjectMapper objectMapper;

//	private static final String CURRENT_SESSION_ATTR = SESSION_REPOSITORY_ATTR + ".CURRENT_SESSION";

	private static final String WRITTEN_SESSION_ID_ATTR = CookieHttpSessionIdResolver.class.getName()
			.concat(".WRITTEN_SESSION_ID_ATTR");


	public JsonAuthenticationHandler(JwtManager jwtManager, ObjectMapper objectMapper) {
		this.jwtManager = jwtManager;
		this.objectMapper = objectMapper;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
		HttpResult httpResponseEntity = new HttpResult(LocalDateTime.now(), HttpStatus.UNAUTHORIZED.value(), exception.getMessage(),request.getContextPath());
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setCharacterEncoding("utf-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), httpResponseEntity);
	}



	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
		UserDetails user = (UserDetails) authentication.getPrincipal();
		String sessionId = (String) request.getAttribute(WRITTEN_SESSION_ID_ATTR);
		String token = jwtManager.createJwtToken(user.getUsername(),sessionId);
		HttpResult httpResult = new JsonAuthenticateSuccessResult(LocalDateTime.now(), "success", token);
		response.setCharacterEncoding("utf-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), httpResult);
	}

}
