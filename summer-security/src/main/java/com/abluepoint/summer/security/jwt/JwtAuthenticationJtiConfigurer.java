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

public class JwtAuthenticationJtiConfigurer<T extends JwtAuthenticationJtiConfigurer<T, B>, B extends HttpSecurityBuilder<B>> extends AbstractHttpConfigurer<T, B> {

	private JwtAuthenticationJtiFilter authenticationFilter;

	public JwtAuthenticationJtiConfigurer() {
		this.authenticationFilter = new JwtAuthenticationJtiFilter();
	}

	@Override
	public void configure(B http) throws Exception {
		authenticationFilter.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
		JwtAuthenticationJtiFilter filter = postProcess(authenticationFilter);
		http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
	}

	public JwtAuthenticationJtiConfigurer<T, B> permissiveRequestUrls(String... urls) {
		authenticationFilter.setPermissiveUrl(urls);
		return this;
	}

	public JwtAuthenticationJtiConfigurer<T, B> successHandler(AuthenticationSuccessHandler successHandler) {
		authenticationFilter.setAuthenticationSuccessHandler(successHandler);
		return this;
	}

	public JwtAuthenticationJtiConfigurer<T, B> failureHandler(AuthenticationFailureHandler failureHandler) {
		authenticationFilter.setAuthenticationFailureHandler(failureHandler);
		return this;
	}

}
