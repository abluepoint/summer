package com.abluepoint.summer.security;

import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

public interface LogoutAndSuccessHandler extends LogoutHandler, LogoutSuccessHandler {

}
