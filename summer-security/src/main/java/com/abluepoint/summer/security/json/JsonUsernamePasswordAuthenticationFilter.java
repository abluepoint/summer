/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonUsernamePasswordAuthenticationFilter.java
 * Date:2020-12-29 17:31:29
 */

package com.abluepoint.summer.security.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * <pre>
 * POST {"username":"user","password":"pwd"}
 * </pre>
 *
 * @author bluepoint
 * @since jdk 1.8
 */
public class JsonUsernamePasswordAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private ObjectMapper objectMapper;

    public JsonUsernamePasswordAuthenticationFilter() {
        super(new AntPathRequestMatcher("/login"));
        objectMapper = new ObjectMapper();
    }

    public JsonUsernamePasswordAuthenticationFilter(ObjectMapper objectMapper) {
        super(new AntPathRequestMatcher("/login"));
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(getAuthenticationManager(), "authenticationManager must be specified");
        Assert.notNull(getSuccessHandler(), "AuthenticationSuccessHandler must be specified");
        Assert.notNull(getFailureHandler(), "AuthenticationFailureHandler must be specified");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {

        if(!HttpMethod.POST.matches(request.getMethod())){
            throw new BadCredentialsException("only post method allowed");
        }

        String body = StreamUtils.copyToString(request.getInputStream(), Charset.forName("UTF-8"));
        String username = null, password = null;

        if (StringUtils.hasText(body)) {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode tempNode = jsonNode.get("username");
            if (tempNode != null) {
                username = tempNode.asText();
            }
            tempNode = jsonNode.get("password");
            if (tempNode != null) {
                password = tempNode.asText();
            }

//			tempNode = jsonNode.get("redirect_uri");
//			if (tempNode != null) {
//				String redirectUri = tempNode.asText();
//				request.setAttribute(Constants.HTTP_ATTRIBUTE_REDIRECT_URI,redirectUri);
//			}
        }

        if (username == null)
            throw new BadCredentialsException("username is null");
        if (password == null)
            throw new BadCredentialsException("password is null");

        username = username.trim();

        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(username, password);

        return getAuthenticationManager().authenticate(authRequest);
    }

}
