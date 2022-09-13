/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonAuthenticationHandler.java
 * Date:2020-12-29 18:31:29
 */

package com.abluepoint.summer.security.app;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.common.exception.Messageable;
import com.abluepoint.summer.mvc.domain.R;
import com.abluepoint.summer.mvc.domain.Result;
import com.abluepoint.summer.security.AuthenticationHandler;
import com.abluepoint.summer.security.jwt.JwtManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * <h2>JsonLoginHandler<h2>
 *
 * @author bluepoint
 */

public class AppAuthenticationHandler implements AuthenticationHandler {

    private static final Logger log = LoggerFactory.getLogger(AppAuthenticationHandler.class);
    private final JwtManager jwtManager;
    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public static final String SESSION_REPOSITORY_ATTR = SessionRepository.class.getName();
    private static final String CURRENT_SESSION_ATTR = SESSION_REPOSITORY_ATTR + ".CURRENT_SESSION";

//    private static final String WRITTEN_SESSION_ID_ATTR = CookieHttpSessionIdResolver.class.getName()
//            .concat(".WRITTEN_SESSION_ID_ATTR");

    public AppAuthenticationHandler(JwtManager jwtManager, ObjectMapper objectMapper, MessageSource messageSource) {
        this.jwtManager = jwtManager;
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        log.info("authentication failure",exception);

        Result<Object> result ;
        if(exception instanceof Messageable){
            result = R.failByMessage(messageSource,((Messageable) exception).getCode(),((Messageable) exception).getMessageKey(),((Messageable) exception).getArgs());
        }else{
            result = R.failByMessage(messageSource, CodeMessageEnum.LOGIN_FAILED);
        }

        result.setTimestamp(LocalDateTime.now());
        result.setPath(request.getContextPath());

        objectMapper.writeValue(response.getOutputStream(), result);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        UserDetails user = (UserDetails) authentication.getPrincipal();
        HttpSession session = (HttpSession) request.getAttribute(CURRENT_SESSION_ATTR);
        String token = jwtManager.createJwtToken(user.getUsername(), session.getId());
        response.setCharacterEncoding("utf-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, String> data = new HashMap<>(1);
        data.put("token", token);

        Result<Map<String, String>> result = R.okByMessage(messageSource, data,CodeMessageEnum.OK);
        objectMapper.writeValue(response.getOutputStream(), result);
    }

}
