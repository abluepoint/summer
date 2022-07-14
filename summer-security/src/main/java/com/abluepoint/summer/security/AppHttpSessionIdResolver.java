package com.abluepoint.summer.security;

import com.abluepoint.summer.security.util.JwtTokenUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.impl.PublicClaims;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppHttpSessionIdResolver implements HttpSessionIdResolver {

    public final static String DECODED_JWT_ATTR = AppHttpSessionIdResolver.class.getName() + ".decoded_jwt";

    private CookieHttpSessionIdResolver cookieHttpSessionIdResolver;
    private RequestMatcher requestMatcher;

    public AppHttpSessionIdResolver() {
        this.requestMatcher = new RequestHeaderRequestMatcher("Authorization");
        this.cookieHttpSessionIdResolver = new CookieHttpSessionIdResolver();
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        if (requestMatcher.matches(request)) {
            String jwtToken = JwtTokenUtil.getJwtToken(request);
            if (StringUtils.hasText(jwtToken)) {
                try {
                    DecodedJWT jwt = JWT.decode(jwtToken);
                    request.setAttribute(DECODED_JWT_ATTR, jwt);
                    String jti = jwt.getClaim(PublicClaims.JWT_ID).asString();
                    if (StringUtils.hasText(jti)) {
                        return Arrays.asList(jti);
                    }
                } catch (Exception e) {
                    // no op
                }
            }
        } else {
            return cookieHttpSessionIdResolver.resolveSessionIds(request);
        }
        return Collections.emptyList();
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        cookieHttpSessionIdResolver.setSessionId(request, response, sessionId);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        cookieHttpSessionIdResolver.expireSession(request, response);
    }
}