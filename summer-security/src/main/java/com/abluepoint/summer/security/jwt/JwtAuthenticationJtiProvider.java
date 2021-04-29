/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JwtAuthenticationProvider.java
 * Date:2020-12-29 18:33:29
 */

package com.abluepoint.summer.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.www.NonceExpiredException;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.util.StringUtils;

import java.util.Calendar;

public class JwtAuthenticationJtiProvider implements AuthenticationProvider {

    private UserDetailsService userService;

    private JwtManager jwtManager;

    private SessionRepository sessionRepository;

    public JwtAuthenticationJtiProvider(UserDetailsService userService, JwtManager jwtManager, SessionRepository sessionRepository) {
        this.userService = userService;
        this.jwtManager = jwtManager;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        JwtAuthenticationToken jwtAuthenticationToken = (JwtAuthenticationToken) authentication;
        String jwtToken = (String) jwtAuthenticationToken.getCredentials();
        DecodedJWT jwt = JWT.decode(jwtToken);
        if (jwt.getExpiresAt().before(Calendar.getInstance().getTime())) {
            throw new NonceExpiredException("Token expires");
        }

        String jti = jwt.getClaim("jti").asString();
        if (StringUtils.hasText(jti)) {
            throw new BadCredentialsException("JWT token jti not found");
        }
        Session s = sessionRepository.findById(jti);
        if (s == null) {
            throw new BadCredentialsException("JWT token jti session expired");
        }

        String username = jwt.getSubject();
        try {
            jwtManager.verify(username, jwt.getToken());
        } catch (Exception e) {
            throw new BadCredentialsException("JWT token verify fail", e);
        }

        UserDetails user = userService.loadUserByUsername(username);
        if (user == null || user.getPassword() == null) {
            throw new UsernameNotFoundException("loadUserByUsername Failed");
        }

        JwtAuthenticationToken token = new JwtAuthenticationToken(user, jwtToken, user.getAuthorities());
        token.setDetails(user);
        return token;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.isAssignableFrom(JwtAuthenticationToken.class);
    }

}
