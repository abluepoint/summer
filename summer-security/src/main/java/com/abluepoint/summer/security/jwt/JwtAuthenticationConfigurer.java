/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JwtAuthenticationConfigurer.java
 * Date:2020-12-29 18:27:29
 */

package com.abluepoint.summer.security.jwt;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class JwtAuthenticationConfigurer<T extends JwtAuthenticationConfigurer<T, B>, B extends HttpSecurityBuilder<B>> extends AbstractHttpConfigurer<T, B> {

	private JwtAuthenticationFilter jwtAuthenticationFilter;

	public JwtAuthenticationConfigurer() {
		this.jwtAuthenticationFilter = new JwtAuthenticationFilter();
	}

	@Override
	public void configure(B http) throws Exception {
		jwtAuthenticationFilter.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
		JwtAuthenticationFilter filter = postProcess(jwtAuthenticationFilter);
		http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
	}

	public JwtAuthenticationConfigurer<T, B> permissiveRequestUrls(String... urls) {
		jwtAuthenticationFilter.setPermissiveUrl(urls);
		return this;
	}

	public JwtAuthenticationConfigurer<T, B> successHandler(AuthenticationSuccessHandler successHandler) {
		jwtAuthenticationFilter.setAuthenticationSuccessHandler(successHandler);
		return this;
	}

	public JwtAuthenticationConfigurer<T, B> failureHandler(AuthenticationFailureHandler failureHandler) {
		jwtAuthenticationFilter.setAuthenticationFailureHandler(failureHandler);
		return this;
	}

}
