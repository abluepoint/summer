package com.abluepoint.summer.security;

import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

public interface AuthenticationHandler extends AuthenticationSuccessHandler, AuthenticationFailureHandler {

}
