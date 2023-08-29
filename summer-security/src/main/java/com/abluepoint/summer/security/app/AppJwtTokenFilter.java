package com.abluepoint.summer.security.app;

import com.abluepoint.summer.security.AppHttpSessionIdResolver;
import com.abluepoint.summer.security.jwt.JwtManager;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Calendar;

/**
 * {@link com.abluepoint.summer.security.AppHttpSessionIdResolver}
 */

public class AppJwtTokenFilter extends OncePerRequestFilter {

    private RequestMatcher requestMatcher;
    private JwtManager jwtManager;
    private AuthenticationEntryPoint authenticationEntryPoint;


    public AppJwtTokenFilter(JwtManager jwtManager, AuthenticationEntryPoint authenticationEntryPoint) {
        this.requestMatcher = new RequestHeaderRequestMatcher("Authorization");
        this.jwtManager = jwtManager;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    private boolean requiresFilter(HttpServletRequest request) {
        return requestMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!requiresFilter(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            DecodedJWT jwt = (DecodedJWT) request.getAttribute(AppHttpSessionIdResolver.DECODED_JWT_ATTR);
            if (jwt == null) {
                throw new BadCredentialsException("token not found");
            }
            if (jwt.getExpiresAt().before(Calendar.getInstance().getTime())) {
                throw new CredentialsExpiredException("Token expires");
            }

            String username = jwt.getSubject();
            try {
                jwtManager.verify(username, jwt.getToken());
            } catch (Exception e) {
                throw new BadCredentialsException("JWT token verify fail", e);
            }

            // 获取securityContext
            SecurityContext securityContext = SecurityContextHolder.getContext();
            if (securityContext == null || securityContext.getAuthentication() == null) {
                throw new SessionAuthenticationException("read user from session Failed");
            }

            UserDetails user = (UserDetails) securityContext.getAuthentication().getPrincipal();
            if (user == null || user.getUsername() == null) {
                throw new SessionAuthenticationException("read user from session Failed");
            }
            if (!username.equals(user.getUsername())) {
                throw new SessionAuthenticationException("JWT token jti session expired");
            }
            filterChain.doFilter(request, response);
        } catch (AuthenticationException e) {
            authenticationEntryPoint.commence(request, response, e);
        }

    }

}
