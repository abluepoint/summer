/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JwtAuthenticationProvider.java
 * Date:2020-12-29 18:33:29
 */

package com.abluepoint.summer.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.impl.PublicClaims;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.www.NonceExpiredException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Calendar;


public class JwtAuthenticationJtiProvider implements AuthenticationProvider {
    private Logger logger = LoggerFactory.getLogger(JwtAuthenticationJtiProvider.class);

    private JwtManager jwtManager;
    private SessionRepository sessionRepository;

    public JwtAuthenticationJtiProvider(JwtManager jwtManager, SessionRepository sessionRepository) {
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

        String jti = jwt.getClaim(PublicClaims.JWT_ID).asString();
        if (!StringUtils.hasText(jti)) {
            throw new BadCredentialsException("JWT token jti not found");
        }
        Session session = sessionRepository.findById(jti);
        if (session == null) {
            throw new BadCredentialsException("JWT token jti session expired");
        }

        String username = jwt.getSubject();
        try {
            jwtManager.verify(username, jwt.getToken());
        } catch (Exception e) {
            throw new BadCredentialsException("JWT token verify fail", e);
        }

        SecurityContext securityContext = readSecurityContextFromSession(session);
        if (securityContext == null) {
            throw new UsernameNotFoundException("read user from session Failed");
        }
        UserDetails user = (UserDetails) securityContext.getAuthentication().getPrincipal();
        if (securityContext == null || user.getPassword() == null) {
            throw new UsernameNotFoundException("read user from session Failed");
        }
        if(!username.equals(user.getUsername())){
            throw new BadCredentialsException("JWT token jti session expired");
        }
        session.setLastAccessedTime(Instant.now());

        JwtAuthenticationToken token = new JwtAuthenticationToken(user, jwtToken, user.getAuthorities());
        return token;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.isAssignableFrom(JwtAuthenticationToken.class);
    }

    /**
     * {@link org.springframework.security.web.context.HttpSessionSecurityContextRepository}
     *
     * @param session the session obtained from the request.
     */
    private SecurityContext readSecurityContextFromSession(Session session) {
        if (session == null) {
            this.logger.trace("No HttpSession currently exists");
            return null;
        }
        // Session exists, so try to obtain a context from it.
        Object contextFromSession = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (contextFromSession == null) {
            return null;
        }

        // We now have the security context object from the session.
        if (!(contextFromSession instanceof SecurityContext)) {
            return null;
        }

        // Everything OK. The only non-null return from this method.
        return (SecurityContext) contextFromSession;
    }
}
