/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticationConfigurer.java
 * Date:2020-12-29 18:32:29
 */

package com.abluepoint.summer.security.json;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

public class JsonAuthenticationConfigurer<T extends JsonAuthenticationConfigurer<T, B>, B extends HttpSecurityBuilder<B>> extends AbstractHttpConfigurer<T, B> {

    private JsonUsernamePasswordAuthenticationFilter authenticationFilter;

    public JsonAuthenticationConfigurer() {
        this.authenticationFilter = new JsonUsernamePasswordAuthenticationFilter();
    }

    @Override
    public void configure(B http) throws Exception {
        authenticationFilter.setAuthenticationManager(http.getSharedObject(AuthenticationManager.class));
        authenticationFilter.setSessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
        JsonUsernamePasswordAuthenticationFilter filter = postProcess(authenticationFilter);
        http.addFilterAfter(filter, LogoutFilter.class);
    }

    public JsonAuthenticationConfigurer<T, B> successHandler(AuthenticationSuccessHandler authSuccessHandler) {
        authenticationFilter.setAuthenticationSuccessHandler(authSuccessHandler);
        return this;
    }

    public JsonAuthenticationConfigurer<T, B> failureHandler(AuthenticationFailureHandler failureHandler) {
        authenticationFilter.setAuthenticationFailureHandler(failureHandler);
        return this;
    }

    public JsonAuthenticationConfigurer<T, B> sessionAuthenticationStrategy(SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        authenticationFilter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
        return this;
    }

    public JsonUsernamePasswordAuthenticationFilter getAuthenticationFilter() {
        return authenticationFilter;
    }

}
